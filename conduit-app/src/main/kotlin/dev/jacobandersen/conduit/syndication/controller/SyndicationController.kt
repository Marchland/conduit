package dev.jacobandersen.conduit.syndication.controller

import dev.jacobandersen.conduit.api.SyndicationTarget
import dev.jacobandersen.conduit.config.SyndicationProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Conduit's control API: the configured syndication targets and their state.
 * WebSub has no state to expose; syndication dispatch is event-driven.
 */
@RestController
@RequestMapping("/syndication")
class SyndicationController(
    private val properties: SyndicationProperties,
) {
    @GetMapping("/targets")
    fun targets(): List<SyndicationTarget> = properties.targets.map { SyndicationTarget(uid = it.uid, name = it.name) }
}
