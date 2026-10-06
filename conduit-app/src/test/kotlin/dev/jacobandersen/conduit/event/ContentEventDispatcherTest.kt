package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.syndication.service.SyndicationReconciliationService
import dev.jacobandersen.conduit.websub.WebsubPublisher
import dev.jacobandersen.content.event.ContentPostEvent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

class ContentEventDispatcherTest {
    private val postId: UUID = UUID.randomUUID()
    private val url = "https://example.com/2024/01/01/s"

    private val reconciliation: SyndicationReconciliationService = mock()
    private val websubPublisher: WebsubPublisher = mock()
    private val checkpointService: ContentEventCheckpointService = mock()
    private val dispatcher =
        ContentEventDispatcher(
            objectMapper = jacksonObjectMapper(),
            syndicationReconciliationService = reconciliation,
            websubPublisher = websubPublisher,
            checkpointService = checkpointService,
        )

    private fun createdJson(version: Int): String =
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
          "version": $version,
          "post": {"type": ["h-entry"], "properties": {"content": ["hi"]}},
          "syndicationTargets": ["t1"]
        }
        """.trimIndent()

    @Test
    fun `applies a created event, pings hubs and checkpoints`() {
        given(checkpointService.lastApplied(postId)).willReturn(0)

        dispatcher.handle(createdJson(version = 1))

        verify(reconciliation).reconcile(any<ContentPostEvent>())
        verify(websubPublisher).publish()
        verify(checkpointService).record(postId, 1)
    }

    @Test
    fun `ignores a stale event`() {
        given(checkpointService.lastApplied(postId)).willReturn(5)

        dispatcher.handle(createdJson(version = 3))

        verify(reconciliation, never()).reconcile(any<ContentPostEvent>())
        verify(checkpointService, never()).record(any(), any())
    }
}
