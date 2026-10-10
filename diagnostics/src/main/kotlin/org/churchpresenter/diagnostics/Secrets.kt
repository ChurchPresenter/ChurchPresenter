package org.churchpresenter.diagnostics

/**
 * Masks credentials in text bound for a log line, a breadcrumb, a Sentry event or a local crash
 * report: the value of a secret query parameter (`?apiKey=…`, `&token=…`), of an `X-Api-Key:`
 * header, and of an `Authorization: Bearer|Basic` header. Everything around the value is kept, so
 * a masked line still says which request it was about.
 */
object Secrets {
    /** What a masked value is replaced with. */
    const val MASK = "<redacted>"

    private val QUERY_PARAMS = listOf(
        "apiKey", "password", "token", "access_token", "refresh_token", "client_secret",
    )

    private val queryParam = Regex(
        "(?i)([?&](?:${QUERY_PARAMS.joinToString("|") { Regex.escape(it) }})=)[^&#\\s\"']*",
    )
    private val apiKeyHeader = Regex("(?i)(X-Api-Key\\s*:\\s*)[^\\s,;\"']+")
    private val authorizationHeader = Regex("(?i)(Authorization\\s*:\\s*(?:Bearer|Basic)\\s+)[^\\s,;\"']+")

    /** [text] with every credential value replaced by [MASK]. */
    fun redact(text: String): String = text
        .replace(queryParam, "$1$MASK")
        .replace(apiKeyHeader, "$1$MASK")
        .replace(authorizationHeader, "$1$MASK")
}
