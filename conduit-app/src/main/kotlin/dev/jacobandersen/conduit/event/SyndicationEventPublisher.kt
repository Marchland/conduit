package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.event.SyndicationEvent

/**
 * Publishes Conduit's syndication distribution events. Implemented over NATS
 * JetStream when a broker is configured; the no-op implementation keeps local
 * runs and tests working without one.
 */
fun interface SyndicationEventPublisher {
    fun publish(event: SyndicationEvent)
}

/** No-op publisher used when no event bus is configured. */
object NoopSyndicationEventPublisher : SyndicationEventPublisher {
    override fun publish(event: SyndicationEvent) = Unit
}
