package dev.jacobandersen.conduit.reconciliation

import dev.jacobandersen.conduit.config.ReconciliationProperties
import dev.jacobandersen.conduit.syndication.service.SyndicationReconciliationService
import dev.jacobandersen.content.client.ContentReadClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Periodic self-heal: pulls posts changed since the stored cursor from Bastion's
 * `/internal/posts/changed` and re-derives expected syndication state, diffing
 * against Conduit's records. Idempotent (already-syndicated targets are left
 * alone), it heals lost/duplicated/reordered events and restart gaps.
 */
@Service
class ConduitReconciliationService(
    private val contentReadClient: ObjectProvider<ContentReadClient>,
    private val reconciliation: SyndicationReconciliationService,
    private val cursorService: ReconciliationCursorService,
    private val properties: ReconciliationProperties,
) {
    fun reconcile() {
        val client = contentReadClient.ifAvailable
        if (client == null) {
            logger.debug { "Reconciliation skipped: content service read client is not configured" }
            return
        }

        var cursor = cursorService.get(CURSOR_ID)
        var pages = 0
        while (pages < properties.maxPages) {
            val page = client.changedSince(cursor, properties.pageSize)
            page.posts.forEach { reconciliation.reconcile(it) }

            val next = page.nextCursor ?: break
            cursor = next
            cursorService.set(CURSOR_ID, cursor)
            pages++
        }
        logger.debug { "Reconciliation sweep processed $pages page(s)" }
    }

    companion object {
        const val CURSOR_ID = "content"
    }
}
