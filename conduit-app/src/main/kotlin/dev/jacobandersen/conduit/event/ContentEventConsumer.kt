package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.config.ConduitEventProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.JetStream
import io.nats.client.JetStreamManagement
import io.nats.client.Message
import io.nats.client.PushSubscribeOptions
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Durable JetStream consumer of Bastion's `content.post.*` events. Each message
 * is dispatched (version-guarded, idempotent) and acked; a failed dispatch is
 * redelivered with a short delay.
 */
@Component
@ConditionalOnProperty(prefix = "conduit.events.nats", name = ["enabled"], havingValue = "true")
class ContentEventConsumer(
    private val connection: Connection,
    private val eventDispatcher: ContentEventDispatcher,
    private val properties: ConduitEventProperties,
) {
    private val jetStream: JetStream = connection.jetStream()
    private val management: JetStreamManagement = connection.jetStreamManagement()
    private var dispatcher: Dispatcher? = null

    @PostConstruct
    fun start() {
        val nats = properties.nats
        ensureStream(nats.contentStream, nats.contentSubject)

        val options =
            PushSubscribeOptions
                .builder()
                .stream(nats.contentStream)
                .durable(nats.contentConsumer)
                .build()

        val handler: (Message) -> Unit = { message ->
            try {
                eventDispatcher.handle(String(message.data, Charsets.UTF_8))
                message.ack()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to dispatch content event ${message.subject}; will retry" }
                message.nakWithDelay(Duration.ofSeconds(5))
            }
        }

        dispatcher =
            connection.createDispatcher().also { d ->
                jetStream.subscribe(nats.contentSubject, d, handler, false, options)
            }
        logger.info { "Subscribed to ${nats.contentSubject} (durable=${nats.contentConsumer}, stream=${nats.contentStream})" }
    }

    @PreDestroy
    fun stop() {
        dispatcher?.let { connection.closeDispatcher(it) }
    }

    private fun ensureStream(
        stream: String,
        subject: String,
    ) {
        val existing = runCatching { management.getStreamInfo(stream) }.getOrNull()
        if (existing == null) {
            logger.info { "Creating JetStream stream $stream capturing $subject" }
            management.addStream(
                StreamConfiguration
                    .builder()
                    .name(stream)
                    .subjects(subject)
                    .storageType(StorageType.File)
                    .replicas(properties.nats.replicas)
                    .build(),
            )
        } else if (subject !in existing.configuration.subjects) {
            management.updateStream(StreamConfiguration.builder(existing.configuration).addSubjects(subject).build())
        }
    }
}
