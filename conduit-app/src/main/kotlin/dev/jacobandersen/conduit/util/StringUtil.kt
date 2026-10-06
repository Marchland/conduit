package dev.jacobandersen.conduit.util

object StringUtil {
    /** An excerpt of at most [maxLen] characters. */
    fun String.excerpt(maxLen: Int): String = substring(0, maxLen.coerceIn(0, length))
}
