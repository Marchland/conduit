package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.TestcontainersConfiguration
import dev.jacobandersen.conduit.syndication.repository.PostSyndicationRepository
import io.nats.client.Nats
import io.nats.client.Options
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end event flow into Conduit: a `content.post.created` event published
 * to NATS JetStream is consumed, guarded, and reconciled (the desired
 * syndication target is recorded) with the version checkpointed. Real Postgres
 * + NATS containers. HTTP dispatch itself is covered by the reconciliation unit
 * tests; here the configured target endpoint is a blocked host so no network is
 * touched.
 */
@Testcontainers
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "conduit.events.nats.enabled=true",
        "conduit.syndication.targets[0].uid=t1",
        "conduit.syndication.targets[0].name=Target One",
        "conduit.syndication.targets[0].endpoint=http://localhost:9/blocked",
    ],
)
class SyndicationNatsIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js")
                .withExposedPorts(4222)

        @JvmStatic
        @DynamicPropertySource
        fun natsProperties(registry: DynamicPropertyRegistry) {
            registry.add("conduit.events.nats.url") { "nats://${nats.host}:${nats.getMappedPort(4222)}" }
        }
    }

    @Autowired
    private lateinit var checkpointRepository: ContentEventCheckpointRepository

    @Autowired
    private lateinit var postSyndicationRepository: PostSyndicationRepository

    @Test
    fun `consumes a content event, records the desired target and checkpoints`() {
        val postId = UUID.randomUUID()
        val url = "https://example.com/2024/01/01/s"
        val payload =
            """
            {
              "eventType": "CREATED",
              "id": "$postId",
              "slug": "s",
              "url": "$url",
              "h": "h-entry",
              "status": "PUBLISHED",
              "visibility": "PUBLIC",
              "deleted": false,
              "categories": [],
              "version": 1,
              "post": {"type": ["h-entry"], "properties": {"content": ["hi"]}},
              "syndicationTargets": ["t1"]
            }
            """.trimIndent()

        Nats.connect(Options.builder().server("nats://${nats.host}:${nats.getMappedPort(4222)}").build()).use { connection ->
            connection.jetStream().publish("content.post.created", payload.toByteArray(Charsets.UTF_8))
        }

        await { checkpointRepository.findById(postId).isPresent }
        assertEquals(1L, checkpointRepository.findById(postId).get().version)

        await { postSyndicationRepository.findByPostId(postId).isNotEmpty() }
        assertEquals("t1", postSyndicationRepository.findByPostId(postId).single().targetUid)
    }

    private fun await(
        timeoutMillis: Long = 20_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue(condition(), "condition not met within ${timeoutMillis}ms")
    }
}
