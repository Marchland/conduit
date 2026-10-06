package dev.jacobandersen.conduit.event

/**
 * The distribution fact a `syndication.*` event carries: a copy was created at
 * a target (`SYNDICATED`), or a copy was withdrawn (`RETRACTED`).
 */
enum class SyndicationEventType {
    SYNDICATED,
    RETRACTED,
}
