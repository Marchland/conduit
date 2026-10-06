package dev.jacobandersen.conduit.syndication

/**
 * The syndication lifecycle actions a target may support. A target only ever
 * receives posts for actions it explicitly declares.
 */
enum class SyndicationAction {
    CREATE,
    DELETE,
    UPDATE,
}
