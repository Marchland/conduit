package dev.jacobandersen.conduit.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

internal object HttpUtil {
    internal fun isTransientStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599

    /** Whether the URL's host must not be contacted (SSRF guard for outbound sends). */
    internal fun isBlockedHost(
        url: String,
        failClosedOnDnsError: Boolean,
    ): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "http" && scheme != "https") return true

        val host = uri.host ?: return true
        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return true

        val addresses =
            runCatching { InetAddress.getAllByName(normalized) }.getOrNull()
                ?: return failClosedOnDnsError

        return addresses.any(::isDisallowedAddress)
    }

    private fun isDisallowedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        if (address is Inet4Address) return false
        if (address is Inet6Address) {
            val firstByte = address.address[0].toInt() and 0xfe
            return firstByte == 0xfc
        }
        return true
    }
}
