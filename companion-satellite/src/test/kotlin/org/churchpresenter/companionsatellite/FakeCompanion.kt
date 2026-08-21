package org.churchpresenter.companionsatellite

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * A loopback Bitfocus Companion speaking the Satellite protocol, so [CompanionSatelliteClient]
 * can be driven end to end with no Companion installed.
 *
 * **Every line here was taken from a capture of a real Companion** (4.3.3, API 1.10.1) recorded
 * through a transparent TCP proxy, not from reading the client. A fake derived from the code
 * under test encodes that code's own assumptions, so a misreading of the protocol would be baked
 * into both sides of every assertion and the suite would be green and worthless.
 *
 * What the real instance sent, and what this reproduces:
 * ```
 * BEGIN CompanionVersion="4.3.3+9230-stable-06a7406709" ApiVersion="1.10.1"
 * CAPS SUBSCRIPTIONS=0
 * ADD-DEVICE OK DEVICEID="..."
 * BRIGHTNESS DEVICEID="..." VALUE=100
 * KEY-STATE DEVICEID="..." CONTROLID="1" PRESSED=0 TYPE="BUTTON" BITMAP="<base64>"
 *           COLOR="#000000" TEXTCOLOR="#ffffff" TEXT="<base64>" LOCATION="1/0/1"
 * ```
 * and it answers `KEY-PRESS` / `CHANGE-PAGE` with `<COMMAND> OK DEVICEID="..."`, which the real
 * one does and the client ignores.
 *
 * Everything is event-driven: responses are written in reaction to a line arriving, so tests wait
 * on a positive signal instead of outlasting a timeout.
 */
internal class FakeCompanion(
    /** When false, ADD-DEVICE is answered with a failure instead of OK. */
    private val acceptRegistration: Boolean = true,
    private val registrationError: String = "Invalid LAYOUT_MANIFEST",
    /** Sent immediately after a successful registration; 0 disables. */
    private val brightness: Int = 100,
) : AutoCloseable {

    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort

    private val running = AtomicBoolean(true)
    private val out = AtomicReference<Socket?>(null)

    /** Every line the client sent, in order. */
    val received: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Connections accepted so far — a reconnect increments this. */
    @Volatile var connectionCount: Int = 0
        private set

    private val acceptor = thread(isDaemon = true, name = "fake-companion") {
        while (running.get()) {
            val sock = runCatching { server.accept() }.getOrElse { return@thread }
            connectionCount++
            out.set(sock)
            thread(isDaemon = true, name = "fake-companion-session") { serve(sock) }
        }
    }

    private fun serve(sock: Socket) {
        runCatching {
            val reader = sock.getInputStream().bufferedReader()
            send("BEGIN CompanionVersion=\"4.3.3+9230-stable-06a7406709\" ApiVersion=\"1.10.1\" ")
            send("CAPS SUBSCRIPTIONS=0 ")
            while (running.get()) {
                val line = reader.readLine() ?: break
                received.add(line)
                respond(line)
            }
        }
        runCatching { sock.close() }
    }

    private fun respond(line: String) {
        val command = line.substringBefore(' ')
        val deviceId = Regex("""DEVICEID="((?:[^"\\]|\\.)*)"""").find(line)?.groupValues?.get(1) ?: ""
        when (command.uppercase()) {
            "PING" -> send("PONG ")
            "ADD-DEVICE" -> {
                if (acceptRegistration) {
                    send("ADD-DEVICE OK DEVICEID=\"$deviceId\" ")
                    if (brightness > 0) send("BRIGHTNESS DEVICEID=\"$deviceId\" VALUE=$brightness ")
                } else {
                    send("ADD-DEVICE ERROR DEVICEID=\"$deviceId\" MESSAGE=\"$registrationError\" ")
                }
            }
            "KEY-PRESS" -> send("KEY-PRESS OK DEVICEID=\"$deviceId\" ")
            "CHANGE-PAGE" -> send("CHANGE-PAGE OK DEVICEID=\"$deviceId\" ")
        }
    }

    // ── Things a test makes Companion do ──────────────────────────────────────

    /** One KEY-STATE, in the exact field order and encoding the real instance used. */
    fun sendKeyState(
        deviceId: String,
        controlId: Int,
        text: String = "",
        color: String = "#000000",
        textColor: String = "#ffffff",
        pressed: Boolean = false,
        page: Int = 1,
        row: Int = 0,
        column: Int = 0,
        bitmapBytes: Int = 72 * 72 * 3,
    ) {
        val bitmap = Base64.getEncoder().encodeToString(ByteArray(bitmapBytes))
        val encodedText = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        send(
            "KEY-STATE DEVICEID=\"$deviceId\" CONTROLID=\"$controlId\" PRESSED=${if (pressed) 1 else 0} " +
                "TYPE=\"BUTTON\" BITMAP=\"$bitmap\" COLOR=\"$color\" TEXTCOLOR=\"$textColor\" " +
                "TEXT=\"$encodedText\" LOCATION=\"$page/$row/$column\" "
        )
    }

    fun sendKeysClear(deviceId: String) = send("KEYS-CLEAR DEVICEID=\"$deviceId\" ")

    fun sendBrightness(deviceId: String, value: Int) =
        send("BRIGHTNESS DEVICEID=\"$deviceId\" VALUE=$value ")

    /** A raw line, for protocol shapes a helper does not cover. */
    fun sendRaw(line: String) = send(line)

    /** Drops the current connection, so the client's reconnect loop runs. */
    fun dropConnection() {
        runCatching { out.getAndSet(null)?.close() }
    }

    private fun send(line: String) {
        val sock = out.get() ?: return
        runCatching {
            sock.getOutputStream().apply { write((line + "\n").toByteArray(Charsets.UTF_8)); flush() }
        }.onFailure { if (it !is IOException) throw it }
    }

    fun linesStartingWith(prefix: String): List<String> =
        synchronized(received) { received.filter { it.startsWith(prefix) } }

    override fun close() {
        running.set(false)
        runCatching { out.get()?.close() }
        runCatching { server.close() }
        acceptor.join(1_000)
    }
}
