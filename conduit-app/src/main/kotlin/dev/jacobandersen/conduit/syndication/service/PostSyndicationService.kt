package dev.jacobandersen.conduit.syndication.service

import dev.jacobandersen.conduit.syndication.domain.PostSyndication
import dev.jacobandersen.conduit.syndication.entity.PostSyndicationEntity
import dev.jacobandersen.conduit.syndication.repository.PostSyndicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PostSyndicationService(
    private val repository: PostSyndicationRepository,
) {
    @Transactional(readOnly = true)
    fun findByPostId(postId: UUID): List<PostSyndication> = repository.findByPostId(postId).map { it.toDomain() }

    /** Record a desired target without a copy yet (retained until publish). */
    @Transactional
    fun record(
        postId: UUID,
        targetUid: String,
    ) {
        if (repository.findByPostIdAndTargetUid(postId, targetUid) == null) {
            repository.save(PostSyndicationEntity(postId = postId, targetUid = targetUid))
        }
    }

    @Transactional
    fun recordOutcome(
        postId: UUID,
        targetUid: String,
        syndicatedUrl: String,
    ) {
        val entity = repository.findByPostIdAndTargetUid(postId, targetUid) ?: return
        entity.syndicatedUrl = syndicatedUrl
        repository.save(entity)
    }

    /** Clear the copy URL after a retraction while retaining the desired target. */
    @Transactional
    fun clearOutcome(
        postId: UUID,
        targetUid: String,
    ) {
        val entity = repository.findByPostIdAndTargetUid(postId, targetUid) ?: return
        entity.syndicatedUrl = null
        repository.save(entity)
    }

    @Transactional
    fun remove(
        postId: UUID,
        targetUid: String,
    ) {
        repository.deleteByPostIdAndTargetUid(postId, targetUid)
    }
}
