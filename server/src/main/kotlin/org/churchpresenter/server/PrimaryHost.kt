package org.churchpresenter.server

/**
 * A primary's host as Instance Link connects to it, and the port the operator's text carried, if any.
 *
 * @property host the host alone: a name, an IPv4 address, or an IPv6 address in brackets.
 * @property port the port that followed the host in the text, or null when it named none.
 */
data class PrimaryHost(val host: String, val port: Int?)

private val SCHEME = Regex("""^(?:https?|wss?)://""", RegexOption.IGNORE_CASE)
private const val MAX_PORT = 65_535

/**
 * What an operator typed into Instance Link's Host field, reduced to the host the client connects to.
 *
 * The field asks for a host, but what people paste is often the address the primary's Server page
 * shows: `192.168.0.112:8765`, or `http://192.168.2.119:8765`. Passed on as it is, the client asked
 * for `ws://http://192.168.2.119:8765:8765/ws`, which ktor cannot resolve, and the link never came up.
 *
 * So a scheme (`http`, `https`, `ws`, `wss`), anything from the first `/`, `?` or `#`, and a trailing
 * `:port` are taken off, and the port is returned so it can fill an empty Port field. A bracketed
 * IPv6 address keeps its brackets (`[::1]:8765` is `[::1]` and 8765), which every URL built from the
 * host needs; a bare one gains them, and is never read as having a port.
 */
fun normalizePrimaryHost(raw: String): PrimaryHost {
    val authority = raw.trim()
        .replaceFirst(SCHEME, "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    return when {
        authority.startsWith("[") -> bracketed(authority)
        authority.count { it == ':' } > 1 -> PrimaryHost("[$authority]", null)
        else -> {
            val tail = authority.substringAfter(':', missingDelimiterValue = "x")
            if (tail.all(Char::isDigit)) PrimaryHost(authority.substringBefore(':'), portOrNull(tail))
            else PrimaryHost(authority, null)
        }
    }
}

/** `[addr]` or `[addr]:port`. */
private fun bracketed(authority: String): PrimaryHost {
    val close = authority.indexOf(']')
    if (close < 0) return PrimaryHost(authority, null)
    val rest = authority.substring(close + 1)
    return PrimaryHost(authority.substring(0, close + 1), portOrNull(rest.removePrefix(":")))
}

/** [text] as a port, or null when it is empty, not a number, or out of range. */
private fun portOrNull(text: String): Int? =
    if (text.all(Char::isDigit)) text.toIntOrNull()?.takeIf { it in 1..MAX_PORT } else null
