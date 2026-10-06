package dev.jacobandersen.conduit.event

import dev.jacobandersen.conduit.config.ConduitEventProperties
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/**
 * Wires the NATS JetStream connection and Conduit's event publisher. When the
 * bus is disabled (default), Conduit uses a no-op publisher and does not
 * connect, so local runs and tests work without a broker.
 */
@Configuration
class NatsEventConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "conduit.events.nats", name = ["enabled"], havingValue = "true")
    fun natsConnection(properties: ConduitEventProperties): Connection = Nats.connect(Options.builder().server(properties.nats.url).build())

    @Bean
    @ConditionalOnProperty(prefix = "conduit.events.nats", name = ["enabled"], havingValue = "true")
    fun natsSyndicationEventPublisher(
        connection: Connection,
        properties: ConduitEventProperties,
        objectMapper: ObjectMapper,
    ): SyndicationEventPublisher =
        NatsSyndicationEventPublisher(
            connection = connection,
            streamName = properties.nats.distributionStream,
            subjectFilter = properties.nats.distributionSubject,
            objectMapper = objectMapper,
            replicas = properties.nats.replicas,
        )

    @Bean
    @ConditionalOnMissingBean(SyndicationEventPublisher::class)
    fun noopSyndicationEventPublisher(): SyndicationEventPublisher = NoopSyndicationEventPublisher
}
