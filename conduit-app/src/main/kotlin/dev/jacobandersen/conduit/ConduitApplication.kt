package dev.jacobandersen.conduit

import dev.jacobandersen.conduit.config.ConduitEventProperties
import dev.jacobandersen.conduit.config.ReconciliationProperties
import dev.jacobandersen.conduit.config.SyndicationProperties
import dev.jacobandersen.conduit.config.WebsubProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    SyndicationProperties::class,
    WebsubProperties::class,
    ConduitEventProperties::class,
    ReconciliationProperties::class,
)
class ConduitApplication

fun main(args: Array<String>) {
    runApplication<ConduitApplication>(*args)
}
