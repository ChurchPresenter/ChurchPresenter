package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import java.net.NetworkInterface

private const val FIRST_PRINTABLE_CHAR = 0x20
private const val IFACE_RANK_WIFI = 3
private const val IFACE_RANK_OTHER = 10

/**
 * Small pure helpers used by `CompanionServer` and its route groups. None of them read server
 * state, so they live here rather than as members.
 */
internal fun jsonEscape(s: String): String = buildString {
    for (c in s) {
        when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c.code < FIRST_PRINTABLE_CHAR -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
}

internal fun contentTypeForExtension(ext: String): ContentType = when (ext.lowercase()) {
    "jpg", "jpeg" -> ContentType.Image.JPEG
    "png"         -> ContentType.Image.PNG
    "gif"         -> ContentType.Image.GIF
    "webp"        -> ContentType.parse("image/webp")
    "bmp"         -> ContentType.parse("image/bmp")
    "heic", "heif"-> ContentType.parse("image/heic")
    else          -> ContentType.Image.JPEG
}
/**
 * Returns the best local IPv4 address for display in the Server URL.
 *
 * Preference order (most-stable first):
 *   1. Wired Ethernet  — eth*, en0 (macOS primary)
 *   2. Other en* interfaces (macOS: en1 = WiFi, etc.)
 *   3. wlan* / wifi*
 *   4. Everything else (non-loopback, non-virtual, non-VPN)
 *
 * Configure a static IP or use [start]'s hostOverride parameter to bypass
 * this entirely and always display a fixed address.
 */
internal fun localIpAddress(): String {
    return try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { iface -> iface.isUp && !iface.isLoopback && !iface.isVirtual }
            .sortedWith(compareBy { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("eth")  -> 0   // Linux wired
                    name == "en0"           -> 1   // macOS primary (usually wired on desktops)
                    name.startsWith("en")   -> 2   // macOS secondary (WiFi is typically en1+)
                    name.startsWith("wlan") -> IFACE_RANK_WIFI   // Linux WiFi
                    name.startsWith("wifi") -> IFACE_RANK_WIFI
                    else                    -> IFACE_RANK_OTHER  // VPNs, bridges, docker, etc.
                }
            })
            .flatMap { iface -> iface.inetAddresses.asSequence() }
            .firstOrNull { addr -> !addr.isLoopbackAddress && addr.hostAddress.contains('.') }
            ?.hostAddress ?: "localhost"
    } catch (_: Exception) {
        "localhost"
    }
}
