package dev.jacobandersen.conduit.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * WebSub publisher configuration. Conduit is a WebSub publisher only: on any
 * content change it pings each configured hub so they re-fetch the feed.
 */
@ConfigurationProperties(prefix = "conduit.websub")
data class WebsubProperties(
    val hubs: List<String> = emptyList(),
    val topicUrl: String = "",
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 10,
)
