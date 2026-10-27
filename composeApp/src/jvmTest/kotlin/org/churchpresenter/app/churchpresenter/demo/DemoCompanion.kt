package org.churchpresenter.app.churchpresenter.demo

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A stand-in Bitfocus Companion on the loopback, speaking the Satellite protocol as a real one does
 * (the lines `:companion-satellite`'s `FakeCompanion` was captured from), so the app connects to it
 * for real. Every device that registers is sent one page of [BUTTONS], drawn as Companion draws
 * them; a press lights its button while held and is remembered in [pressed].
 */
internal class DemoCompanion : AutoCloseable {
    private val server = runCatching { ServerSocket(DEFAULT_PORT, BACKLOG, InetAddress.getLoopbackAddress()) }
        .getOrElse { ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress()) }
    val port: Int get() = server.localPort

    private val running = AtomicBoolean(true)
    private val sockets = Collections.synchronizedList(mutableListOf<Socket>())

    private val sizes = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** The labels of the buttons pressed so far, in order. */
    val pressed: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val acceptor = thread(isDaemon = true, name = "demo-companion") {
        while (running.get()) {
            val sock = runCatching { server.accept() }.getOrElse { return@thread }
            sockets += sock
            thread(isDaemon = true, name = "demo-companion-session") { serve(sock) }
        }
    }

    private fun serve(sock: Socket) {
        runCatching {
            val reader = sock.getInputStream().bufferedReader()
            send(sock, "BEGIN CompanionVersion=\"4.3.3+9230-stable-06a7406709\" ApiVersion=\"1.10.1\" ")
            send(sock, "CAPS SUBSCRIPTIONS=0 ")
            while (running.get()) {
                val line = reader.readLine() ?: break
                respond(sock, line)
            }
        }
        runCatching { sock.close() }
    }

    private fun respond(sock: Socket, line: String) {
        val deviceId = Regex("""DEVICEID="((?:[^"\\]|\\.)*)"""").find(line)?.groupValues?.get(1) ?: ""
        when (line.substringBefore(' ').uppercase()) {
            "PING" -> send(sock, "PONG ")
            "ADD-DEVICE" -> {
                send(sock, "ADD-DEVICE OK DEVICEID=\"$deviceId\" ")
                send(sock, "BRIGHTNESS DEVICEID=\"$deviceId\" VALUE=100 ")
                // The surface's shape is in its layout manifest: a control per button, and the bitmap size.
                val manifest = Regex("""LAYOUT_MANIFEST="([^"]*)"""").find(line)?.groupValues?.get(1)
                    ?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }.orEmpty()
                val keys = Regex(""""(\d+)":\{"row"""").findAll(manifest).count().takeIf { it > 0 } ?: BUTTONS.size
                val size = Regex(""""w":(\d+)""").find(manifest)?.groupValues?.get(1)?.toIntOrNull() ?: BITMAP_SIZE
                sizes[deviceId] = size
                for (index in 0 until keys) keyState(sock, deviceId, index, size, held = false)
            }
            "KEY-PRESS" -> {
                val index = Regex("""CONTROLID="?(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return
                val down = Regex("""PRESSED=(\w+)""").find(line)?.groupValues?.get(1)?.let { it == "true" || it == "1" }
                    ?: true
                if (down) BUTTONS.getOrNull(index)?.let { pressed += it.label }
                keyState(sock, deviceId, index, sizes[deviceId] ?: BITMAP_SIZE, held = down)
                send(sock, "KEY-PRESS OK DEVICEID=\"$deviceId\" ")
            }
        }
    }

    private fun keyState(sock: Socket, deviceId: String, index: Int, size: Int, held: Boolean) {
        val button = BUTTONS.getOrNull(index) ?: DemoButton("", BLANK)
        val text = Base64.getEncoder().encodeToString(button.label.toByteArray(Charsets.UTF_8))
        val bitmap = Base64.getEncoder().encodeToString(draw(button, size, held))
        send(
            sock,
            "KEY-STATE DEVICEID=\"$deviceId\" CONTROLID=\"$index\" PRESSED=${if (held) 1 else 0} " +
                "TYPE=\"BUTTON\" BITMAP=\"$bitmap\" COLOR=\"${hex(button.color)}\" TEXTCOLOR=\"#ffffff\" " +
                "TEXT=\"$text\" LOCATION=\"1/${index / COLUMNS}/${index % COLUMNS}\" ",
        )
    }

    private fun send(sock: Socket, line: String) {
        runCatching {
            synchronized(sock) { sock.getOutputStream().apply { write((line + "\n").toByteArray()); flush() } }
        }
    }

    override fun close() {
        running.set(false)
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } } }
        runCatching { server.close() }
        acceptor.join(JOIN_MS)
    }
}

/** One button as Companion would draw it: its color, its label in white, and a ring while held. */
private fun draw(button: DemoButton, size: Int, held: Boolean): ByteArray {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = button.color
    g.fillRect(0, 0, size, size)
    if (held) {
        g.color = Color(255, 255, 255, HELD_ALPHA)
        g.fillRect(0, 0, size, size)
        g.color = Color.YELLOW
        g.stroke = java.awt.BasicStroke(size / RING_DIVISOR)
        g.drawRect(1, 1, size - 3, size - 3)
    }
    if (button.label.isNotEmpty()) {
        g.color = if (button.color == Color.BLACK) Color(0xF8, 0x71, 0x71) else Color.WHITE
        g.font = Font(Font.SANS_SERIF, Font.BOLD, size / FONT_DIVISOR)
        val lines = button.label.split('\n')
        val fm = g.fontMetrics
        val top = (size - lines.size * fm.height) / 2 + fm.ascent
        lines.forEachIndexed { i, line -> g.drawString(line, (size - fm.stringWidth(line)) / 2, top + i * fm.height) }
    }
    g.dispose()
    val out = ByteArray(size * size * RGB)
    var at = 0
    for (y in 0 until size) {
        for (x in 0 until size) {
            val rgb = img.getRGB(x, y)
            out[at++] = (rgb shr RED_SHIFT and BYTE).toByte()
            out[at++] = (rgb shr GREEN_SHIFT and BYTE).toByte()
            out[at++] = (rgb and BYTE).toByte()
        }
    }
    return out
}

private fun hex(c: Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)

internal data class DemoButton(val label: String, val color: Color)

private val BLANK = Color(0x2a, 0x2a, 0x30)

/** One page of a church's Stream Deck: the service's moments, then the booth's switches. */
internal val BUTTONS = listOf(
    DemoButton("Walk-in", Color(0x2e, 0x7d, 0x32)),
    DemoButton("Worship", Color(0x15, 0x65, 0xc0)),
    DemoButton("Sermon", Color(0x6a, 0x1b, 0x9a)),
    DemoButton("Clear", Color.BLACK),
    DemoButton("Cam 1", Color(0x37, 0x47, 0x4f)),
    DemoButton("Cam 2", Color(0x37, 0x47, 0x4f)),
    DemoButton("Lights", Color(0xef, 0x6c, 0x00)),
    DemoButton("Stream", Color(0xc6, 0x28, 0x28)),
)

private const val DEFAULT_PORT = 16622
private const val BACKLOG = 4
private const val JOIN_MS = 1_000L
private const val COLUMNS = 4
private const val BITMAP_SIZE = 72
private const val HELD_ALPHA = 60
private const val RING_DIVISOR = 18f
private const val FONT_DIVISOR = 6
private const val RGB = 3
private const val BYTE = 0xff
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
