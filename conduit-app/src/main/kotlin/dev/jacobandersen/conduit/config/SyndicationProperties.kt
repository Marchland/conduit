package dev.jacobandersen.conduit.config

import dev.jacobandersen.conduit.syndication.SyndicationAction
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Downstream micropub syndication targets (such as Bridgy). Moved out of
 * Bastion: Conduit owns syndication state and dispatch.
 */
@ConfigurationProperties(prefix = "conduit.syndication")
data class SyndicationProperties(
    val targets: List<Target> = emptyList(),
    val defaultMaxGraphemes: Int = DEFAULT_MAX_GRAPHEMES,
) {
    data class Target(
        val uid: String,
        val name: String,
        val endpoint: String,
        val token: String? = null,
        val actions: Set<SyndicationAction> = setOf(SyndicationAction.CREATE, SyndicationAction.DELETE),
        val maxGraphemes: Int? = null,
    ) {
        fun supports(action: SyndicationAction): Boolean = action in actions
    }

    fun effectiveMaxGraphemes(target: Target): Int = (target.maxGraphemes ?: defaultMaxGraphemes).coerceAtLeast(1)

    fun targetByUid(uid: String): Target? = targets.firstOrNull { it.uid == uid }

    companion object {
        const val DEFAULT_MAX_GRAPHEMES = 300
    }
}
