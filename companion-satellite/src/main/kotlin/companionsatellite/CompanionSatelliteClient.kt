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

        /** How long a simulated press is held down — long enough for Companion to register it as a
         * real press rather than a glitch, short enough not to trip its long-press handling. */
        private const val PRESS_HOLD_MS = 80L

        /** Gap between successive CHANGE-PAGE messages, so Companion finishes one step before the
         * next arrives. */
        private const val PAGE_STEP_INTERVAL_MS = 150L
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
        surface: SurfaceSpec,
        reconnectDelayMs: Long = 2000L
    ) {
        disconnect()
        val myGeneration = ++generation
        activeDeviceId = surface.deviceId
        onButtonsReset(surface.buttonCount)
        connectJob = scope.launch { connectLoop(myGeneration, host, port, surface, reconnectDelayMs) }
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
     * the layout manifest. */
    fun pressButton(index: Int) {
        val deviceId = activeDeviceId
        if (deviceId.isEmpty() || currentStatus != CompanionConnectionStatus.CONNECTED) return
        scope.launch {
            writeLine(pressMessage(deviceId, index, pressed = true))
            delay(PRESS_HOLD_MS)
            writeLine(pressMessage(deviceId, index, pressed = false))
        }
    }

    /** Requests [times] relative page navigations (Companion's protocol has no "go to page N" — only
     * step forward/backward), paced so Companion has time to process each before the next. */
    fun changePage(forward: Boolean, times: Int = 1) {
        val deviceId = activeDeviceId
        if (deviceId.isEmpty() || currentStatus != CompanionConnectionStatus.CONNECTED || times <= 0) return
        scope.launch {
            repeat(times) {
                writeLine(encodeMessage("CHANGE-PAGE", deviceId, linkedMapOf("DIRECTION" to forward)))
                delay(PAGE_STEP_INTERVAL_MS)
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
        surface: SurfaceSpec,
        reconnectDelayMs: Long
    ) {
        while (scope.isActive && generation == this.generation) {
            setStatus(CompanionConnectionStatus.CONNECTING, null)
            try {
                Socket(host, port).use { runSession(it, generation, surface) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Everything the socket, the reads and the writes can fail with. A failure of any
                // other kind is a bug rather than a bad connection, and is left to surface.
                if (generation == this.generation) {
                    setStatus(CompanionConnectionStatus.ERROR, e.message ?: "Connection failed")
                }
            }
            if (generation == this.generation) {
                socket = null
                writer = null
                if (currentStatus != CompanionConnectionStatus.ERROR) {
                    setStatus(CompanionConnectionStatus.DISCONNECTED, null)
                }
            }
            if (!scope.isActive || generation != this.generation) break
            delay(reconnectDelayMs)
        }
    }

    /** One connection, from the socket opening to the far end going quiet. */
    private suspend fun runSession(s: Socket, generation: Long, surface: SurfaceSpec) {
        // A newer connect()/disconnect() call already superseded this job while the (blocking)
        // Socket constructor was running — leave the newer job's state alone and let `use` close
        // this now-useless socket.
        if (generation != this.generation) return
        s.soTimeout = READ_TIMEOUT_MS
        socket = s
        writer = s.getOutputStream()
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        // Companion closes the TCP socket after 5s of no traffic in either direction
        // (net.Socket.setTimeout(5000) server-side) — ping well under that or Companion drops us
        // and our reconnect loop kicks in, forever.
        val pingJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                writeLine("PING\n")
            }
        }
        try {
            while (scope.isActive && generation == this.generation) {
                val line = nextLine(reader, MAX_CONSECUTIVE_READ_TIMEOUTS, READ_TIMEOUT_MS) ?: break
                handleLine(line, surface)
            }
        } finally {
            pingJob.cancel()
        }
    }

    private fun handleLine(line: String, surface: SurfaceSpec) {
        val trimmed = line.removeSuffix("\r")
        val spaceIndex = trimmed.indexOf(' ')
        val cmd = if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)
        val body = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1)
        val params = parseLineParameters(body)

        when (cmd.uppercase()) {
            "PING" -> writeLine("PONG $body\n")
            "BEGIN" -> writeLine(addDeviceMessage(surface))
            "ADD-DEVICE" -> {
                if ("OK" in params) {
                    setStatus(CompanionConnectionStatus.CONNECTED, null)
                } else {
                    setStatus(CompanionConnectionStatus.ERROR, params["MESSAGE"] ?: "Device registration failed")
                }
            }
            "KEY-STATE" -> parseButtonUpdate(params, surface.bitmapSize)?.let(onButtonUpdated)
            "KEYS-CLEAR" -> onButtonsReset(surface.buttonCount)
            "BRIGHTNESS" -> params["VALUE"]?.toIntOrNull()?.let { onBrightnessChanged(it) }
            // PONG/REMOVE-DEVICE/DEVICE-CONFIG/CAPS need no action for a plain grid client.
            else -> {}
        }
    }

    private fun writeLine(line: String) {
        val out = writer ?: return
        synchronized(writeLock) {
            runCatching {
                out.write(line.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
    }
}
