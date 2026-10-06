package dev.jacobandersen.conduit.websub

import dev.jacobandersen.conduit.config.WebsubProperties
import dev.jacobandersen.conduit.util.HttpUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

class WebsubPublishException(
    message: String,
) : RuntimeException(message)

/**
 * Publishes the configured topic to each configured WebSub hub. Dispatch is
 * fire-and-forget: one JobRunr job per hub, with JobRunr retrying retryable
 * failures. This is near-stateless and driven by `content.post.*` events.
 */
@Service
class WebsubPublisher(
    private val jobScheduler: JobScheduler,
    private val httpClient: WebsubHttpClient,
    private val config: WebsubProperties,
) {
    private val hubs: List<String>
        get() = config.hubs.map(String::trim).filter(String::isNotBlank)

    private val enabled: Boolean
        get() = hubs.isNotEmpty() && config.topicUrl.isNotBlank()

    fun publish() {
        if (!enabled) {
            logger.debug { "WebSub publishing disabled (no hubs or topic URL configured)" }
            return
        }
        hubs.forEach { hub -> jobScheduler.enqueue { publishToHub(hub) } }
    }

    fun publishToHub(hubUrl: String) {
        if (HttpUtil.isBlockedHost(hubUrl, failClosedOnDnsError = false)) {
            logger.warn { "Skipping publish to blocked hub $hubUrl" }
            return
        }

        logger.info { "Publishing WebSub topic ${config.topicUrl} to $hubUrl..." }
        when (val result = httpClient.publish(hubUrl, config.topicUrl)) {
            is PublishResult.Success -> {
                logger.info { "WebSub publish delivered to $hubUrl (HTTP ${result.statusCode})" }
            }

            is PublishResult.Failure -> {
                if (result.retryable) {
                    throw WebsubPublishException("WebSub publish to $hubUrl failed: ${result.message}")
                } else {
                    logger.warn { "WebSub publish to $hubUrl permanently failed: ${result.message}" }
                }
            }
        }
    }
}
