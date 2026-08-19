package companionsatellite

import java.io.BufferedReader
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * Reads the next line, riding out quiet periods and giving up on silence.
 *
 * A read timeout is not a dead connection — Companion sends nothing at all when no button changes —
 * so [maxTimeouts] of them in a row is what counts as gone. Returns null at end of stream; throws
 * [IOException] once the silence has gone on too long, which is what puts the caller back in its
 * reconnect loop.
 */
internal fun nextLine(reader: BufferedReader, maxTimeouts: Int, timeoutMs: Int): String? {
    var timeouts = 0
    while (true) {
        try {
            return reader.readLine()
        } catch (e: SocketTimeoutException) {
            timeouts++
            if (timeouts >= maxTimeouts) {
                throw IOException("No data received for ${timeouts * timeoutMs}ms — assuming dead connection", e)
            }
        }
    }
}

/** One `COMMAND key=value key2="quoted value"` line, terminated, ready to write. */
internal fun encodeMessage(name: String, deviceId: String?, args: Map<String, Any>): String = buildString {
    append(name)
    if (deviceId != null) append(" DEVICEID=\"").append(escapeValue(deviceId)).append('"')
    for ((key, value) in args) {
        append(' ').append(key).append('=')
        when (value) {
            is Boolean -> append(if (value) "1" else "0")
            is Number -> append(value.toString())
            else -> append('"').append(escapeValue(value.toString())).append('"')
        }
    }
    append('\n')
}

/** A down or up for one button, as a real key press sends. */
internal fun pressMessage(deviceId: String, index: Int, pressed: Boolean): String =
    encodeMessage("KEY-PRESS", deviceId, linkedMapOf("CONTROLID" to index.toString(), "PRESSED" to pressed))

/** The ADD-DEVICE line that registers [spec] with Companion. */
internal fun addDeviceMessage(spec: SurfaceSpec): String = encodeMessage(
    "ADD-DEVICE",
    spec.deviceId,
    linkedMapOf(
        "PRODUCT_NAME" to spec.productName,
        "LAYOUT_MANIFEST" to buildLayoutManifest(spec),
        "BRIGHTNESS" to false,
        // Declares the capability so Companion's Surfaces settings panel offers a "change page"
        // permission toggle for this device — CHANGE-PAGE is a no-op until the admin also enables
        // that toggle there; this can't be turned on remotely by the client.
        "CAN_CHANGE_PAGE" to "Change page",
        "VARIABLES" to Base64.getEncoder().encodeToString("[]".toByteArray()),
    ),
)

/**
 * Builds a base64-encoded LAYOUT_MANIFEST JSON blob matching Companion's
 * `satellite-surface.schema.json` exactly (verified against a live instance — the schema's `size`
 * def requires `w`/`h`, not `width`/`height`, and rejects the whole ADD-DEVICE with "Invalid
 * LAYOUT_MANIFEST" if violated): `{stylePresets: {default: {bitmap: {w, h}, text, textStyle,
 * colors}}, controls: {"<id>": {row, column}, ...}}`. Control id `i` maps to real-page position
 * `(startRow + i/columns, startColumn + i%columns)` — this explicit per-control positioning is what
 * makes a chosen sub-rectangle of the page possible at all; the legacy KEYS_TOTAL/KEYS_PER_ROW form
 * Companion also supports has no offset concept and always anchors at (0, 0). No JSON library
 * needed — the shape is small and fixed enough to build by hand.
 */
internal fun buildLayoutManifest(spec: SurfaceSpec): String {
    val controls = buildString {
        for (i in 0 until spec.buttonCount) {
            if (i > 0) append(',')
            append("\"").append(i).append("\":{\"row\":").append(spec.startRow + i / spec.columns)
                .append(",\"column\":").append(spec.startColumn + i % spec.columns).append('}')
        }
    }
    val json = "{\"stylePresets\":{\"default\":{\"bitmap\":{\"w\":${spec.bitmapSize},\"h\":${spec.bitmapSize}}," +
        "\"text\":true,\"textStyle\":false,\"colors\":\"hex\"}},\"controls\":{$controls}}"
    return Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
}

/**
 * Escapes `\` and `"` so a free-text value (device id, product name) can't break out of its quoted
 * slot — [parseLineParameters] already understands these same backslash-escapes on the read side,
 * so this just makes the write side use what the protocol already supports.
 */
internal fun escapeValue(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

/**
 * One button's state out of a KEY-STATE line, or null when the line names no control.
 *
 * CONTROLID (not KEY) identifies the button for a LAYOUT_MANIFEST-registered device — its value is
 * the control id assigned in [buildLayoutManifest], which is simply the button index's string form,
 * so parsing it back to Int reproduces the original index unchanged.
 */
internal fun parseButtonUpdate(params: Map<String, String>, bitmapSize: Int): CompanionButtonUpdate? {
    val index = params["CONTROLID"]?.toIntOrNull() ?: return null
    val decoder = Base64.getDecoder()
    return CompanionButtonUpdate(
        index = index,
        bitmapRgb = params["BITMAP"]?.let { runCatching { decoder.decode(it) }.getOrNull() },
        bitmapSize = bitmapSize,
        text = params["TEXT"]?.let { runCatching { String(decoder.decode(it)) }.getOrDefault("") } ?: "",
        color = params["COLOR"],
        textColor = params["TEXTCOLOR"],
        pressed = params["PRESSED"] == "1",
        page = params["LOCATION"]?.substringBefore('/')?.toIntOrNull(),
    )
}

/**
 * Parses `key=value key2="quoted value" boolFlag` tokens, splitting each on the first `=` only (so
 * base64 padding `=` inside a value isn't corrupted). Bare tokens map to `"true"`.
 */
internal fun parseLineParameters(line: String): Map<String, String> =
    splitTokens(line).mapNotNull { token ->
        if (token.isEmpty()) return@mapNotNull null
        val equals = token.indexOf('=')
        if (equals == -1) token to "true" else token.substring(0, equals) to token.substring(equals + 1)
    }.toMap()

/** The line broken on unquoted spaces, with backslash escapes applied and quotes removed. */
private fun splitTokens(line: String): List<String> {
    val fragments = mutableListOf(StringBuilder())
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '\\' && i + 1 < line.length -> {
                fragments.last().append(line[i + 1])
                i += 2
            }
            c == '"' -> {
                inQuotes = !inQuotes
                i++
            }
            c == ' ' && !inQuotes -> {
                fragments.add(StringBuilder())
                i++
            }
            else -> {
                fragments.last().append(c)
                i++
            }
        }
    }
    return fragments.map { it.toString() }
}
