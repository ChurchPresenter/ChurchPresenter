package org.churchpresenter.diagnostics

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class LogTest {

    private val lines = mutableListOf<String>()
    private val trail = mutableListOf<Triple<String, String, LogLevel>>()
    private val log = Logger(sink = { lines += it }, trail = { m, c, l -> trail += Triple(m, c, l) })

    @Test
    fun `every level writes one tagged line`() {
        log.info("Camera", "First frame received")
        log.warn("Camera", "Stream interrupted")
        log.error("DeckLink", "Could not find SkiaLayer")

        assertEquals(
            listOf(
                "[Camera] First frame received",
                "[Camera] Stream interrupted",
                "[DeckLink] Could not find SkiaLayer",
            ),
            lines,
        )
    }

    @Test
    fun `progress stays out of the crash trail`() {
        log.info("BrowserSource", "CDP ready on port 9222")

        assertEquals(emptyList(), trail)
    }

    @Test
    fun `warnings and errors join the crash trail under their tag and level`() {
        log.warn("Camera", "Stream interrupted")
        log.error("DeckLink", "Could not find SkiaLayer")

        assertEquals(
            listOf(
                Triple("[Camera] Stream interrupted", "Camera", LogLevel.WARN),
                Triple("[DeckLink] Could not find SkiaLayer", "DeckLink", LogLevel.ERROR),
            ),
            trail,
        )
    }

    @Test
    fun `the app's log writes to stderr and hands warnings to the crash reporter`() {
        // Sentry is not initialised in tests, so the real trail is reached and returns without
        // sending anything; what is observable is the line on stderr.
        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured, true))
        try {
            Log.info("Camera", "First frame received")
            Log.warn("Camera", "Stream interrupted")
            Log.error("DeckLink", "Could not find SkiaLayer")
        } finally {
            System.setErr(original)
        }

        assertEquals(
            listOf(
                "[Camera] First frame received",
                "[Camera] Stream interrupted",
                "[DeckLink] Could not find SkiaLayer",
            ),
            captured.toString().lines().filter { it.isNotBlank() },
        )
    }

    @Test
    fun `a credential never reaches stderr or the crash trail`() {
        log.warn("VideoPlayer", "Playback error: http://10.0.0.2:8765/api/media?apiKey=s3cret")

        val masked = "[VideoPlayer] Playback error: http://10.0.0.2:8765/api/media?apiKey=${Secrets.MASK}"
        assertEquals(listOf(masked), lines)
        assertEquals(listOf(Triple(masked, "VideoPlayer", LogLevel.WARN)), trail)
    }
}
