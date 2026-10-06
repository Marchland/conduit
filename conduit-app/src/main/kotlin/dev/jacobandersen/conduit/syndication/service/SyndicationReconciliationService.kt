package dev.jacobandersen.conduit.syndication.service

import dev.jacobandersen.conduit.config.SyndicationProperties
import dev.jacobandersen.conduit.event.SyndicationEvent
import dev.jacobandersen.conduit.event.SyndicationEventPublisher
import dev.jacobandersen.conduit.event.SyndicationEventType
import dev.jacobandersen.conduit.syndication.SyndicationAction
import dev.jacobandersen.conduit.syndication.SyndicationContentMapper
import dev.jacobandersen.conduit.syndication.SyndicationHttpClient
import dev.jacobandersen.conduit.syndication.SyndicationSendResult
import dev.jacobandersen.conduit.util.HttpUtil
import dev.jacobandersen.content.event.ContentPostEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Reconciles a post's actual syndicated copies against the targets it desires,
 * driven by `content.post.*` events. On a public post, every desired target
 * gets a copy (or is re-based on a rename); targets no longer desired are
 * retracted. On a non-public/deleted post, copies are retracted but desired
 * targets are retained so a re-publish re-syndicates. Emits `syndication.*`.
 */
@Service
class SyndicationReconciliationService(
    private val properties: SyndicationProperties,
    private val postSyndicationService: PostSyndicationService,
    private val httpClient: SyndicationHttpClient,
    private val eventPublisher: SyndicationEventPublisher,
) {
    fun reconcile(event: ContentPostEvent) {
        val postId = runCatching { UUID.fromString(event.id) }.getOrNull() ?: return
        val canonicalUrl = event.url
        val public = event.status == "PUBLISHED" && event.visibility == "PUBLIC" && !event.deleted
        val desired = desiredTargets(event.syndicationTargets)
        val actual = postSyndicationService.findByPostId(postId).associateBy { it.targetUid }

        if (!public) {
            actual.values.filter { it.syndicatedUrl != null }.forEach { record ->
                retract(postId, record.targetUid, canonicalUrl, removeRecord = false)
            }
            desired.forEach { postSyndicationService.record(postId, it.uid) }
            return
        }

        desired.forEach { target ->
            val existing = actual[target.uid]
            when {
                existing == null -> {
                    postSyndicationService.record(postId, target.uid)
                    syndicate(postId, event, target.uid, canonicalUrl)
                }

                existing.syndicatedUrl == null -> {
                    syndicate(postId, event, target.uid, canonicalUrl)
                }

                event.previousUrl != null && event.previousUrl != canonicalUrl -> {
                    rebase(postId, event, target.uid, event.previousUrl!!, canonicalUrl)
                }

                else -> {
                    Unit
                } // already syndicated at the current URL
            }
        }

        val desiredUids = desired.map { it.uid }.toSet()
        actual.values.filter { it.targetUid !in desiredUids }.forEach { record ->
            retract(postId, record.targetUid, canonicalUrl, removeRecord = true)
        }
    }

    private fun desiredTargets(uids: List<String>): List<SyndicationProperties.Target> =
        uids.distinct().mapNotNull { properties.targetByUid(it)?.takeIf { t -> t.supports(SyndicationAction.CREATE) } }

    private fun syndicate(
        postId: UUID,
        event: ContentPostEvent,
        targetUid: String,
        canonicalUrl: String,
    ) {
        val target = properties.targetByUid(targetUid) ?: return
        val post = event.post ?: return
        if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
            logger.warn { "Skipping syndication to blocked target \"$targetUid\" for post $postId" }
            return
        }

        val payload =
            SyndicationContentMapper.build(post, event.type, canonicalUrl, properties.effectiveMaxGraphemes(target))
        when (val result = httpClient.sendCreate(target, payload)) {
            is SyndicationSendResult.Success -> {
                val url = result.location ?: canonicalUrl
                postSyndicationService.recordOutcome(postId, targetUid, url)
                eventPublisher.publish(
                    SyndicationEvent(SyndicationEventType.SYNDICATED, postId.toString(), targetUid, target.name, url, canonicalUrl),
                )
                logger.info { "Syndicated post $postId to \"$targetUid\" (HTTP ${result.statusCode})" }
            }

            is SyndicationSendResult.Failure -> {
                logger.warn { "Syndication create to \"$targetUid\" for post $postId failed: ${result.message}" }
            }
        }
    }

    private fun retract(
        postId: UUID,
        targetUid: String,
        sourceUrl: String,
        removeRecord: Boolean,
    ) {
        val target = properties.targetByUid(targetUid)
        if (target != null &&
            target.supports(SyndicationAction.DELETE) &&
            !HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)
        ) {
            when (val result = httpClient.sendDelete(target, sourceUrl)) {
                is SyndicationSendResult.Success -> {
                    logger.info { "Retracted copy for post $postId from \"$targetUid\" (HTTP ${result.statusCode})" }
                }

                is SyndicationSendResult.Failure -> {
                    logger.warn { "Syndication delete to \"$targetUid\" for post $postId failed: ${result.message}" }
                }
            }
        }

        if (removeRecord) {
            postSyndicationService.remove(postId, targetUid)
        } else {
            postSyndicationService.clearOutcome(postId, targetUid)
        }
        eventPublisher.publish(
            SyndicationEvent(SyndicationEventType.RETRACTED, postId.toString(), targetUid, target?.name, sourceUrl = sourceUrl),
        )
    }

    private fun rebase(
        postId: UUID,
        event: ContentPostEvent,
        targetUid: String,
        previousUrl: String,
        canonicalUrl: String,
    ) {
        val target = properties.targetByUid(targetUid) ?: return
        if (!target.supports(SyndicationAction.DELETE) || !target.supports(SyndicationAction.CREATE)) return
        if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) return

        when (val deleteResult = httpClient.sendDelete(target, previousUrl)) {
            is SyndicationSendResult.Success -> {
                logger.info { "Syndication rebase removed old copy for post $postId at \"$targetUid\"" }
            }

            is SyndicationSendResult.Failure -> {
                logger.warn { "Syndication rebase could not remove old copy at \"$targetUid\" for post $postId" }
            }
        }
        syndicate(postId, event, targetUid, canonicalUrl)
    }
}
