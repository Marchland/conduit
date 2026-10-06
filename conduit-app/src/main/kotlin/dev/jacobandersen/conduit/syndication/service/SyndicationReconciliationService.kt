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
import dev.jacobandersen.content.client.PostDto
import dev.jacobandersen.content.event.ContentPostEvent
import dev.jacobandersen.microformats2.Mf2Object
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * The neutral input the reconciler works from: a post's identity, visibility
 * and the targets it desires. Built from either a `content.post.*` event or the
 * read model (reconciliation sweeps).
 */
data class SyndicationReconcileInput(
    val postId: String,
    val url: String,
    val previousUrl: String?,
    val status: String,
    val visibility: String,
    val deleted: Boolean,
    val type: String?,
    val post: Mf2Object?,
    val syndicationTargets: List<String>,
)

/**
 * Reconciles a post's actual syndicated copies against the targets it desires.
 * On a public post, every desired target gets a copy (or is re-based on a
 * rename); targets no longer desired are retracted. On a non-public/deleted
 * post, copies are retracted but desired targets are retained so a re-publish
 * re-syndicates. Idempotent: already-syndicated targets are left alone. Emits
 * `syndication.*`.
 */
@Service
class SyndicationReconciliationService(
    private val properties: SyndicationProperties,
    private val postSyndicationService: PostSyndicationService,
    private val httpClient: SyndicationHttpClient,
    private val eventPublisher: SyndicationEventPublisher,
) {
    fun reconcile(event: ContentPostEvent) {
        reconcile(
            SyndicationReconcileInput(
                postId = event.id,
                url = event.url,
                previousUrl = event.previousUrl,
                status = event.status,
                visibility = event.visibility,
                deleted = event.deleted,
                type = event.type,
                post = event.post,
                syndicationTargets = event.syndicationTargets,
            ),
        )
    }

    fun reconcile(post: PostDto) {
        reconcile(
            SyndicationReconcileInput(
                postId = post.id,
                url = post.url,
                previousUrl = null,
                status = post.status,
                visibility = post.visibility,
                deleted = post.deleted,
                type = post.type,
                post = post.post,
                syndicationTargets = post.desiredSyndicationTargets,
            ),
        )
    }

    fun reconcile(input: SyndicationReconcileInput) {
        val postId = runCatching { UUID.fromString(input.postId) }.getOrNull() ?: return
        val canonicalUrl = input.url
        val public = input.status == "PUBLISHED" && input.visibility == "PUBLIC" && !input.deleted
        val desired = desiredTargets(input.syndicationTargets)
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
                    syndicate(postId, input, target.uid, canonicalUrl)
                }

                existing.syndicatedUrl == null -> {
                    syndicate(postId, input, target.uid, canonicalUrl)
                }

                input.previousUrl != null && input.previousUrl != canonicalUrl -> {
                    rebase(postId, input, target.uid, input.previousUrl!!, canonicalUrl)
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
        input: SyndicationReconcileInput,
        targetUid: String,
        canonicalUrl: String,
    ) {
        val target = properties.targetByUid(targetUid) ?: return
        val post = input.post ?: return
        if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) {
            logger.warn { "Skipping syndication to blocked target \"$targetUid\" for post $postId" }
            return
        }

        val payload =
            SyndicationContentMapper.build(post, input.type, canonicalUrl, properties.effectiveMaxGraphemes(target))
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
        input: SyndicationReconcileInput,
        targetUid: String,
        previousUrl: String,
        canonicalUrl: String,
    ) {
        val target = properties.targetByUid(targetUid) ?: return
        if (!target.supports(SyndicationAction.DELETE) || !target.supports(SyndicationAction.CREATE)) return
        if (HttpUtil.isBlockedHost(target.endpoint, failClosedOnDnsError = false)) return

        when (httpClient.sendDelete(target, previousUrl)) {
            is SyndicationSendResult.Success -> {
                logger.info { "Syndication rebase removed old copy for post $postId at \"$targetUid\"" }
            }

            is SyndicationSendResult.Failure -> {
                logger.warn { "Syndication rebase could not remove old copy at \"$targetUid\" for post $postId" }
            }
        }
        syndicate(postId, input, targetUid, canonicalUrl)
    }
}
