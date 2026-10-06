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
        val contentStream: String = "CONTENT",
        val contentSubject: String = "content.>",
        val contentConsumer: String = "conduit-content",
        val distributionStream: String = "DISTRIBUTION",
        val distributionSubject: String = "syndication.>",
    )
}
