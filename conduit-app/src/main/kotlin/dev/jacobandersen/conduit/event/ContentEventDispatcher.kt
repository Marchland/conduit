package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.syndication.service.SyndicationReconciliationService
import dev.jacobandersen.conduit.websub.WebsubPublisher
import dev.jacobandersen.content.event.ContentPostEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Applies a consumed `content.post.*` event: reconciles syndicated copies
 * against the post's desired targets, pings WebSub hubs, and records the
 * per-post version checkpoint (guarding against duplicate/reordered delivery).
 */
@Component
class ContentEventDispatcher(
    private val objectMapper: ObjectMapper,
    private val syndicationReconciliationService: SyndicationReconciliationService,
    private val websubPublisher: WebsubPublisher,
    private val checkpointService: ContentEventCheckpointService,
) {
    @Transactional
    fun handle(payload: String) {
        val event = objectMapper.readValue(payload, ContentPostEvent::class.java)
        val postId =
            runCatching { UUID.fromString(event.id) }.getOrElse {
                logger.warn { "Ignoring content event with non-UUID post id ${event.id}" }
                return
            }

        val lastApplied = checkpointService.lastApplied(postId)
        if (event.version <= lastApplied) {
            logger.debug { "Skipping content event v${event.version} for post $postId (lastApplied=$lastApplied)" }
            return
        }

        syndicationReconciliationService.reconcile(event)
        if (isWebsubRelevant(event)) {
            websubPublisher.publish()
        }

        checkpointService.record(postId, event.version)
        logger.info { "Applied content event ${event.eventType} v${event.version} for post $postId" }
    }

    /**
     * WebSub subscribers only care about a post that is (or was) publicly
     * published; skip draft/private changes so hubs are not pinged needlessly.
     * A delete still notifies, since a published post disappearing is a change.
     */
    private fun isWebsubRelevant(event: ContentPostEvent): Boolean =
        event.deleted || (event.status == "PUBLISHED" && event.visibility == "PUBLIC")
}
