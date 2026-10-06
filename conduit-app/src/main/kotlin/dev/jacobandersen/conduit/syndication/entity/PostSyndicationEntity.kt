package dev.jacobandersen.conduit.syndication.entity

import dev.jacobandersen.conduit.syndication.domain.PostSyndication
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "post_syndications")
class PostSyndicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    // The content service's post id. No cross-service foreign key.
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(name = "target_uid", nullable = false)
    var targetUid: String,
    @Column(name = "syndicated_url", nullable = true)
    var syndicatedUrl: String? = null,
    @Column(name = "created_at_utc", nullable = false)
    var createdAtUtc: Instant = Instant.now(),
) {
    fun toDomain(): PostSyndication =
        PostSyndication(
            id = requireNotNull(id),
            postId = postId,
            targetUid = targetUid,
            syndicatedUrl = syndicatedUrl,
            createdAtUtc = createdAtUtc,
        )
}
