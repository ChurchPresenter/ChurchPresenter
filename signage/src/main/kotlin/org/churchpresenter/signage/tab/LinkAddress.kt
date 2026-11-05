package org.churchpresenter.signage.tab

import java.net.URI
import java.net.URISyntaxException

/** An address a screen can open: a scheme and a host, such as `https://example.org` or `rtsp://camera`. */
internal fun isLinkAddress(url: String): Boolean = hostOf(url) != null

/** The host [url] opens, without `www.`; null for no whole address yet. */
internal fun hostOf(url: String): String? = try {
    // The prefilled "https://www." alone is no address yet.
    URI(url.trim()).takeIf { it.scheme != null }?.host?.removePrefix("www.")?.trim('.')?.takeIf(String::isNotEmpty)
} catch (_: URISyntaxException) {
    null
}

/**
 * [typed] without the prefilled [ADDRESS_PREFIX] when a whole address, or one starting `www.`, was
 * pasted or typed after it: `https://www.https://example.org` is `https://example.org`.
 */
internal fun withoutPrefixTwice(typed: String): String {
    if (typed == ADDRESS_PREFIX || ADDRESS_PREFIX !in typed) return typed
    val rest = typed.replaceFirst(ADDRESS_PREFIX, "")
    return when {
        "://" in rest -> rest
        rest.startsWith("www.") -> "https://$rest"
        else -> typed
    }
}

/** What a new link's address starts as, so only the rest is typed: the same in every language. */
internal const val ADDRESS_PREFIX = "https://www."
