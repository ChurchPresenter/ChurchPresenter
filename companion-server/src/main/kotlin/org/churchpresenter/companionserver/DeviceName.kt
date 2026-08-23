package org.churchpresenter.companionserver

import io.ktor.http.decodeURLPart
import io.ktor.server.request.ApplicationRequest
import org.churchpresenter.settings.utils.Constants

/** Longest device name kept. A label sits beside a UUID in a dialog; past this it is not a label. */
private const val MAX_DEVICE_NAME_LENGTH = 64

/**
 * The name this request says its device has, or null when it does not say.
 *
 * Header first, then a query parameter of the same name — the fallback exists because a browser
 * cannot set headers on a WebSocket handshake, and it is read on every route so one rule covers
 * both transports.
 */
internal fun ApplicationRequest.reportedDeviceName(): String? =
    headers[Constants.HEADER_DEVICE_NAME] ?: queryParameters[Constants.HEADER_DEVICE_NAME]

/**
 * Turns what a client sent into something worth showing an operator, or "" when there is nothing.
 *
 * Device names are user-typed, so outside English-speaking churches they are usually not ASCII —
 * and an HTTP header is not a place for arbitrary text: OkHttp refuses to send a header value with
 * a character outside printable ASCII at all, and a raw UTF-8 value that does get sent is read back
 * as ISO-8859-1 here. So a client percent-encodes anything non-printable and this undoes it.
 *
 * Everything about the decoding is deliberately forgiving, because the alternative to a name is a
 * UUID and no input is worth a failed request: a value with no `%` is passed through untouched
 * (a hand-written `curl` keeps working), a malformed escape keeps the raw text rather than
 * throwing, control characters are dropped so a name cannot break the line it is drawn on, and the
 * result is capped rather than rejected.
 */
internal fun decodeDeviceName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val decoded = if ('%' in raw) runCatching { raw.decodeURLPart() }.getOrDefault(raw) else raw
    return decoded
        .filter { it.code >= ' '.code && it.code != DEL_CHAR_CODE }
        .trim()
        .take(MAX_DEVICE_NAME_LENGTH)
        .trim()
}

private const val DEL_CHAR_CODE = 127
