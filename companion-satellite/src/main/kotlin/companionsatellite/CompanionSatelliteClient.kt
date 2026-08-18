package companionsatellite

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64

enum class CompanionConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** One button's state as last reported by Companion, in protocol-native form. */
data class CompanionButtonUpdate(
    val index: Int,
    val bitmapRgb: ByteArray? = null,
    val bitmapSize: Int = 0,
    val text: String = "",
    val color: String? = null,
    val textColor: String? = null,
    val pressed: Boolean = false,
    /** Parsed from `LOCATION="page/row/column"` (API >= 1.10.0) — null if Companion omits it. */
    val page: Int? = null
)

/**
 * Client for Bitfocus Companion's Satellite protocol (plain TCP, default port 16622,
 * line-based `COMMAND key=value key2="quoted value"` framing). Registers via the
 * `LAYOUT_MANIFEST` `ADD-DEVICE` form — each button explicitly declares its own
 * `(row, column)` position on Companion's real page grid — rather than the legacy
 * `KEYS_TOTAL`/`KEYS_PER_ROW` form, which always anchors at row 0/column 0 with no way to
 * offset into the page; `LAYOUT_MANIFEST` is what makes showing an arbitrary sub-rectangle of
 * a larger page possible. Reports the bitmaps Companion streams for each button (keyed by
 * `CONTROLID`, the manifest's own id scheme) and forwards button presses back. Protocol
 * confirmed against `bitfocus/companion-satellite` and `bitfocus/companion` source.
 *
 * No UI-toolkit dependency by design — [onButtonUpdated] hands back raw RGB bytes so any
 * consumer (Compose, a CLI, a test) can decode them however it likes.
 */
class CompanionSatelliteClient(
    private val onStatusChanged: (CompanionConnectionStatus, String?) -> Unit,
    private val onButtonUpdated: (CompanionButtonUpdate) -> Unit,
    private val onButtonsReset: (count: Int) -> Unit,
    private val onBrightnessChanged: (percent: Int) -> Unit = {}
) {
    companion object {
        /** Companion's TCP layer times out an idle socket after 5s — ping comfortably under that. */
        private const val PING_INTERVAL_MS = 2000L
        /** Bounds how long a dead-but-unclosed socket (cable pull, host sleep, silent NAT drop —
         * no clean FIN/RST) can block [BufferedReader.readLine] before we notice. Generous relative
         * to [PING_INTERVAL_MS] so a normal quiet period with no button-state traffic at all never
         * trips a single timeout; only [MAX_CONSECUTIVE_READ_TIMEOUTS] of them in a row — meaning
         * total silence for that whole span — is treated as the connection actually being gone. */
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_CONSECUTIVE_READ_TIMEOUTS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob: Job? = null
    private var socket: Socket? = null
    private var writer: OutputStream? = null
    private val writeLock = Any()
    private var activeDeviceId: String = ""

    /** Bumped by every [connect]/[disconnect] call. A running [connectLoop] captures the
     * generation it was started with and stops touching shared [socket]/[writer]/status state once
     * a newer generation exists — otherwise a still-unwinding old job (cancellation is cooperative;
     * a blocking [Socket] constructor or an in-flight read isn't a suspension point) can clobber the
     * very state a newer, already-connected job just set. */
    @Volatile private var generation: Long = 0

    @Volatile private var currentStatus = CompanionConnectionStatus.DISCONNECTED

    fun connect(
        host: String,
        port: Int,
        deviceId: String,
        rows: Int,
        columns: Int,
        bitmapSize: Int,
        /** Top-left corner of Companion's real page grid that this device's controls map onto —
         * 0/0 shows the page's own top-left corner (the only option the legacy KEYS_TOTAL/
         * KEYS_PER_ROW registration ever supported); any other value shows an arbitrary
         * sub-rectangle instead, which is why registration always uses LAYOUT_MANIFEST now. */
        startRow: Int = 0,
        startColumn: Int = 0,
        productName: String = "ChurchPresenter",
        reconnectDelayMs: Long = 2000L
    ) {
        disconnect()
        val myGeneration = ++generation
        activeDeviceId = deviceId
        onButtonsReset(rows * columns)
        connectJob = scope.launch {
            connectLoop(myGeneration, host, port, deviceId, rows, columns, startRow, startColumn, bitmapSize, productName, reconnectDelayMs)
        }
    }

    fun disconnect() {
        generation++
        connectJob?.cancel()
        connectJob = null
        runCatching { socket?.close() }
        socket = null
        writer = null
        activeDeviceId = ""
        setStatus(CompanionConnectionStatus.DISCONNECTED, null)
    }

    fun dispose() {
        disconnect()
        scope.cancel()
    }

    /** Sends a down-then-up press for the button at [index], as a real key press would.
     * CONTROLID (not KEY) identifies the button for a LAYOUT_MANIFEST-registered device — its
     * value is simply [index]'s string form, matching the control id assigned during
     * [buildLayoutManifest]. */
    fun pressButton(index: Int) {
        val deviceId = activeDeviceId
        if (deviceId.isEmpty() || currentStatus != CompanionConnectionStatus.CONNECTED) return
        scope.launch {
            sendMessage("KEY-PRESS", deviceId, linkedMapOf("CONTROLID" to index.toString(), "PRESSED" to true))
            delay(80)
            sendMessage("KEY-PRESS", deviceId, linkedMapOf("CONTROLID" to index.toString(), "PRESSED" to false))
        }
    }

    /** Requests [times] relative page navigations (Companion's protocol has no "go to page N" — only
     * step forward/backward), paced so Companion has time to process each before the next. */
    fun changePage(forward: Boolean, times: Int = 1) {
        val deviceId = activeDeviceId
        if (deviceId.isEmpty() || currentStatus != CompanionConnectionStatus.CONNECTED || times <= 0) return
        scope.launch {
            repeat(times) {
                sendMessage("CHANGE-PAGE", deviceId, linkedMapOf("DIRECTION" to forward))
                delay(150)
            }
        }
    }

    private fun setStatus(status: CompanionConnectionStatus, error: String?) {
        currentStatus = status
        onStatusChanged(status, error)
    }

    private suspend fun connectLoop(
        generation: Long,
        host: String,
        port: Int,
        deviceId: String,
        rows: Int,
        columns: Int,
        startRow: Int,
        startColumn: Int,
        bitmapSize: Int,
        productName: String,
        reconnectDelayMs: Long
    ) {
        while (scope.isActive && generation == this.generation) {
            setStatus(CompanionConnectionStatus.CONNECTING, null)
            try {
                Socket(host, port).use { s ->
                    // A newer connect()/disconnect() call already superseded this job while the
                    // (blocking) constructor above was running — close this now-useless socket via
                    // `use`'s auto-close and leave the newer job's state alone.
                    if (generation != this.generation) return@use
                    s.soTimeout = READ_TIMEOUT_MS
                    socket = s
                    writer = s.getOutputStream()
                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    // Companion closes the TCP socket after 5s of no traffic in either direction
                    // (net.Socket.setTimeout(5000) server-side) — ping well under that or Companion
                    // drops us and our reconnect loop kicks in, forever.
                    val pingJob = scope.launch {
                        while (isActive) {
                            delay(PING_INTERVAL_MS)
                            writeLine("PING\n")
                        }
                    }
                    try {
                        var consecutiveTimeouts = 0
                        while (scope.isActive && generation == this.generation) {
                            val line = try {
                                reader.readLine()
                            } catch (e: SocketTimeoutException) {
                                consecutiveTimeouts++
                                if (consecutiveTimeouts >= MAX_CONSECUTIVE_READ_TIMEOUTS) {
                                    throw IOException(
                                        "No data received for ${consecutiveTimeouts * READ_TIMEOUT_MS}ms — assuming dead connection",
                                        e
                                    )
                                }
                                continue
                            }
                            consecutiveTimeouts = 0
                            if (line == null) break
                            handleLine(line, deviceId, rows, columns, startRow, startColumn, bitmapSize, productName)
                        }
                    } finally {
                        pingJob.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == this.generation) setStatus(CompanionConnectionStatus.ERROR, e.message ?: "Connection failed")
            }
            if (generation == this.generation) {
                socket = null
                writer = null
                if (currentStatus != CompanionConnectionStatus.ERROR) setStatus(CompanionConnectionStatus.DISCONNECTED, null)
            }
            if (!scope.isActive || generation != this.generation) break
            delay(reconnectDelayMs)
        }
    }

    private fun handleLine(
        line: String,
        deviceId: String,
        rows: Int,
        columns: Int,
        startRow: Int,
        startColumn: Int,
        bitmapSize: Int,
        productName: String
    ) {
        val trimmed = line.removeSuffix("\r")
        val spaceIndex = trimmed.indexOf(' ')
        val cmd = if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)
        val body = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1)
        val params = parseLineParameters(body)

        when (cmd.uppercase()) {
            "PING" -> writeLine("PONG $body\n")
            "BEGIN" -> registerDevice(deviceId, rows, columns, startRow, startColumn, bitmapSize, productName)
            "ADD-DEVICE" -> {
                if ("OK" in params) {
                    setStatus(CompanionConnectionStatus.CONNECTED, null)
                } else {
                    setStatus(CompanionConnectionStatus.ERROR, params["MESSAGE"] ?: "Device registration failed")
                }
            }
            "KEY-STATE" -> handleKeyState(params, bitmapSize)
            "KEYS-CLEAR" -> onButtonsReset(rows * columns)
            "BRIGHTNESS" -> params["VALUE"]?.toIntOrNull()?.let { onBrightnessChanged(it) }
            // PONG/REMOVE-DEVICE/DEVICE-CONFIG/CAPS need no action for a plain grid client.
            else -> {}
        }
    }

    /** CONTROLID (not KEY) identifies the button for a LAYOUT_MANIFEST-registered device — its
     * value is the control id assigned during [buildLayoutManifest], which is simply our button
     * index's string form, so parsing it back to Int reproduces the original index unchanged. */
    private fun handleKeyState(params: Map<String, String>, bitmapSize: Int) {
        val keyIndex = params["CONTROLID"]?.toIntOrNull() ?: return
        val bitmapRgb = params["BITMAP"]?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        val text = params["TEXT"]?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrDefault("") } ?: ""
        val page = params["LOCATION"]?.substringBefore('/')?.toIntOrNull()
        onButtonUpdated(
            CompanionButtonUpdate(
                index = keyIndex,
                bitmapRgb = bitmapRgb,
                bitmapSize = bitmapSize,
                text = text,
                color = params["COLOR"],
                textColor = params["TEXTCOLOR"],
                pressed = params["PRESSED"] == "1",
                page = page
            )
        )
    }

    private fun registerDevice(
        deviceId: String,
        rows: Int,
        columns: Int,
        startRow: Int,
        startColumn: Int,
        bitmapSize: Int,
        productName: String
    ) {
        sendMessage(
            "ADD-DEVICE", deviceId, linkedMapOf(
                "PRODUCT_NAME" to productName,
                "LAYOUT_MANIFEST" to buildLayoutManifest(rows, columns, startRow, startColumn, bitmapSize),
                "BRIGHTNESS" to false,
                // Declares the capability so Companion's Surfaces settings panel offers a "change
                // page" permission toggle for this device — CHANGE-PAGE is a no-op until the admin
                // also enables that toggle there; this can't be turned on remotely by the client.
                "CAN_CHANGE_PAGE" to "Change page",
                "VARIABLES" to Base64.getEncoder().encodeToString("[]".toByteArray())
            )
        )
    }

    /** Builds a base64-encoded LAYOUT_MANIFEST JSON blob matching Companion's
     * `satellite-surface.schema.json` exactly (verified against a live instance — the schema's
     * `size` def requires `w`/`h`, not `width`/`height`, and rejects the whole ADD-DEVICE with
     * "Invalid LAYOUT_MANIFEST" if violated): `{stylePresets: {default: {bitmap: {w, h}, text,
     * textStyle, colors}}, controls: {"<id>": {row, column}, ...}}`. Control id `i` maps to
     * real-page position `(startRow + i/columns, startColumn + i%columns)` — this explicit
     * per-control positioning is what makes a chosen sub-rectangle of the page possible at all;
     * the legacy KEYS_TOTAL/KEYS_PER_ROW form Companion also supports has no offset concept and
     * always anchors at (0, 0). No JSON library needed — the shape is small and fixed enough to
     * build by hand. */
    private fun buildLayoutManifest(rows: Int, columns: Int, startRow: Int, startColumn: Int, bitmapSize: Int): String {
        val controls = buildString {
            for (i in 0 until rows * columns) {
                if (i > 0) append(',')
                append("\"").append(i).append("\":{\"row\":").append(startRow + i / columns)
                    .append(",\"column\":").append(startColumn + i % columns).append('}')
            }
        }
        val json = "{\"stylePresets\":{\"default\":{\"bitmap\":{\"w\":$bitmapSize,\"h\":$bitmapSize}," +
            "\"text\":true,\"textStyle\":false,\"colors\":\"hex\"}},\"controls\":{$controls}}"
        return Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    private fun sendMessage(name: String, deviceId: String?, args: Map<String, Any>) {
        val line = buildString {
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
        writeLine(line)
    }

    /** Escapes `\` and `"` so a free-text value (device id, product name) can't break out of its
     * quoted slot — [parseLineParameters] already understands these same backslash-escapes on the
     * read side, so this just makes the write side use what the protocol already supports. */
    private fun escapeValue(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun writeLine(line: String) {
        val out = writer ?: return
        synchronized(writeLock) {
            runCatching {
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }

    /** Parses `key=value key2="quoted value" boolFlag` tokens, splitting each on the first `=`
     * only (so base64 padding `=` inside a value isn't corrupted). Bare tokens map to `"true"`. */
    private fun parseLineParameters(line: String): Map<String, String> {
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
        val result = mutableMapOf<String, String>()
        for (fragment in fragments) {
            val token = fragment.toString()
            if (token.isEmpty()) continue
            val eq = token.indexOf('=')
            if (eq == -1) result[token] = "true" else result[token.substring(0, eq)] = token.substring(eq + 1)
        }
        return result
    }
}
