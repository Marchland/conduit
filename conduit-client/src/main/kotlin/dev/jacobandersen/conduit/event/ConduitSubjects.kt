package dev.jacobandersen.conduit.event

/**
 * The NATS subject and stream names for Conduit's distribution events. Conduit
 * owns its `SYNDICATION` stream (`syndication.>`); streams are per-producer, so
 * Conduit never shares a stream's lifecycle with another service.
 */
object ConduitSubjects {
    const val STREAM = "SYNDICATION"
    const val SYNDICATION = "syndication.>"
    const val SYNDICATED = "syndication.syndicated"
    const val RETRACTED = "syndication.retracted"

    fun subjectFor(type: SyndicationEventType): String =
        when (type) {
            SyndicationEventType.SYNDICATED -> SYNDICATED
            SyndicationEventType.RETRACTED -> RETRACTED
        }
}
