package dev.jacobandersen.conduit.syndication

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import dev.jacobandersen.mf24j.firstText

/**
 * Builds the downstream copy of a post for syndication targets. The copy is an
 * excerpt plus a permalink, never the full content. The [maxGraphemes] budget
 * covers the total (title + excerpt + link), counted in grapheme clusters.
 */
object SyndicationContentMapper {
    fun build(
        post: Mf2Object,
        type: String?,
        canonicalUrl: String,
        maxGraphemes: Int,
    ): Mf2Object {
        val text = excerptText(post, type, canonicalUrl, maxGraphemes)
        var mapped = post.setProperty("content", Mf2Value.String(text))
        if (post.hasProperty("summary")) {
            mapped = mapped.setProperty("summary", Mf2Value.String(text))
        }
        return mapped.setProperty("url", Mf2Value.String(canonicalUrl))
    }

    fun includesTitle(
        type: String?,
        name: String?,
    ): Boolean = type.equals("article", ignoreCase = true) && !name.isNullOrBlank()

    fun excerptText(
        post: Mf2Object,
        type: String?,
        canonicalUrl: String,
        maxGraphemes: Int,
    ): String {
        val max = maxGraphemes.coerceAtLeast(1)
        val linkSuffix = "\n\n$canonicalUrl"
        val content = normalizedOrNull(post.firstText("content"))
        val name = normalizedOrNull(post.firstText("name"))

        return if (includesTitle(type, name) && name != null) {
            val prefix = "$name: "
            val excerptAllowance = max - Graphemes.count(prefix) - Graphemes.count(linkSuffix)
            if (content != null && excerptAllowance >= MIN_EXCERPT_GRAPHEMES) {
                prefix + truncate(content, excerptAllowance) + linkSuffix
            } else {
                titleOnlyFallback(name, linkSuffix, canonicalUrl, max)
            }
        } else {
            val source = content ?: name
            val excerptAllowance = max - Graphemes.count(linkSuffix)
            if (source != null && excerptAllowance >= MIN_EXCERPT_GRAPHEMES) {
                truncate(source, excerptAllowance) + linkSuffix
            } else {
                canonicalUrl
            }
        }
    }

    private fun titleOnlyFallback(
        name: String,
        linkSuffix: String,
        canonicalUrl: String,
        max: Int,
    ): String {
        val titleAllowance = max - Graphemes.count(linkSuffix)
        return if (titleAllowance >= MIN_TITLE_GRAPHEMES) {
            truncate(name, titleAllowance) + linkSuffix
        } else {
            canonicalUrl
        }
    }

    private fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun normalizedOrNull(text: String?): String? = text?.let(::normalize)?.takeIf { it.isNotBlank() }

    private fun truncate(
        text: String,
        allowance: Int,
    ): String {
        if (allowance <= 0) return ""
        if (Graphemes.count(text) <= allowance) return text
        val roomForText = (allowance - 1).coerceAtLeast(0)
        val cut = Graphemes.take(text, roomForText).trimEnd()
        val lastSpace = cut.lastIndexOf(' ')
        val head =
            if (lastSpace >= 0 && Graphemes.count(cut.substring(0, lastSpace)) >= MIN_WORD_KEEP_GRAPHEMES) {
                cut.substring(0, lastSpace).trimEnd()
            } else {
                cut
            }
        return head + "…"
    }

    private const val MIN_EXCERPT_GRAPHEMES = 20
    private const val MIN_TITLE_GRAPHEMES = 10
    private const val MIN_WORD_KEEP_GRAPHEMES = 20
}
