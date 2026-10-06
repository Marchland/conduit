package dev.jacobandersen.conduit.api

/**
 * An advertised syndication target: a stable [uid] referenced by
 * `mp-syndicate-to` and a display [name]. Part of Conduit's control API.
 */
data class SyndicationTarget(
    val uid: String,
    val name: String,
)
