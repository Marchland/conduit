package dev.jacobandersen.conduit.syndication.domain

import java.time.Instant
import java.util.UUID

data class PostSyndication(
    val id: UUID,
    val postId: UUID,
    val targetUid: String,
    val syndicatedUrl: String?,
    val createdAtUtc: Instant,
)
