package dev.jacobandersen.conduit.sub

import dev.jacobandersen.content.client.ContentReadClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Conduit: WebSub publisher/subscriber facade. Projects syndication and
 * subscription events from Bastion's content event stream into WebSub calls.
 */
@RestController
@RequestMapping("/websub")
class WebSubController(
    private val contentReadClient: ContentReadClient,
) {
    @PostMapping("/subscribe")
    fun subscribe() = "subscribed to Bastion content events"
}