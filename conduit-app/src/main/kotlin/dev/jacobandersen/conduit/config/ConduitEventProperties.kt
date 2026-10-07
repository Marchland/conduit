package dev.jacobandersen.conduit.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Event transport configuration: the NATS JetStream connection Conduit consumes
 * `content.post.*` from and publishes `syndication.*` / `websub.*` to.
 */
@ConfigurationProperties(prefix = "conduit.events")
data class ConduitEventProperties(
    val nats: Nats = Nats(),
) {
    data class Nats(
        val enabled: Boolean = false,
        val url: String = "nats://localhost:4222",
        /** JetStream replica count for streams this service creates (1 dev, 3 prod). */
        val replicas: Int = 1,
        val contentStream: String = "CONTENT",
        val contentSubject: String = "content.>",
        val contentConsumer: String = "conduit-content",
        /** Give up redelivering an event after this many dispatch attempts. */
        val maxDeliveries: Int = 10,
        val distributionStream: String = "SYNDICATION",
        val distributionSubject: String = "syndication.>",
    )
}
