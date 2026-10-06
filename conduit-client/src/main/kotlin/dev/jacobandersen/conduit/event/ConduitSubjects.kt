package dev.jacobandersen.conduit.event

/**
 * The NATS subject and stream names for Conduit's distribution events. The
 * `DISTRIBUTION` stream captures all distribution subjects; Conduit publishes
 * the `syndication.>` and `websub.>` branches.
 */
object ConduitSubjects {
    const val STREAM = "DISTRIBUTION"
    const val SYNDICATION = "syndication.>"
    const val SYNDICATED = "syndication.syndicated"
    const val RETRACTED = "syndication.retracted"
    const val WEBSUB = "websub.>"
    const val WEBSUB_PUBLISHED = "websub.published"

    fun subjectFor(type: SyndicationEventType): String =
        when (type) {
            SyndicationEventType.SYNDICATED -> SYNDICATED
            SyndicationEventType.RETRACTED -> RETRACTED
        }
}
