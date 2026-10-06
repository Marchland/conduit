package dev.jacobandersen.conduit.event

/**
 * Conduit -> distribution event: a fact about a syndicated copy of one of this
 * site's posts. Producer-owned; Bastion's projector consumes it to keep the
 * public read-model summaries current. The post is identified by its content id
 * ([postId]) with no cross-service foreign key.
 */
data class SyndicationEvent(
    val eventType: SyndicationEventType,
    /** The content service's post id the copy belongs to. */
    val postId: String,
    /** The configured syndication target uid. */
    val targetUid: String,
    /** The downstream URL of the copy (for `SYNDICATED`). */
    val syndicatedUrl: String? = null,
    /** The post URL the copy was made from. */
    val sourceUrl: String? = null,
)
