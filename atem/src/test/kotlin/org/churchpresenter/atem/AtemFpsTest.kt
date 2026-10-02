package org.churchpresenter.atem

import kotlin.test.Test
import kotlin.test.assertEquals

/** [formatAtemFps]: how a frame rate is written wherever the app shows one. */
class AtemFpsTest {

    /**
     * The rate is printed in three places — the fps box, the clip capacity line and the detected video
     * mode — and the reason it is not just `toString()` is that `30.0` must not read as "30.0" and
     * `59.94` must not read as "59". Both halves are pinned here.
     */
    @Test
    fun `whole frame rates print without a decimal point`() {
        assertEquals("30", formatAtemFps(30.0))
        assertEquals("25", formatAtemFps(25.0))
        assertEquals("50", formatAtemFps(50.0))
        assertEquals("60", formatAtemFps(60.0))
        assertEquals("0", formatAtemFps(0.0))
    }

    @Test
    fun `fractional NTSC rates keep their fraction`() {
        assertEquals("59.94", formatAtemFps(59.94))
        assertEquals("29.97", formatAtemFps(29.97))
        assertEquals("23.98", formatAtemFps(23.976), "rounded to the two places the format allows")
    }

    /** A rate with one meaningful decimal must not be padded out to two. */
    @Test
    fun `a trailing zero is trimmed rather than printed`() {
        assertEquals("50.5", formatAtemFps(50.5))
        assertEquals("24.1", formatAtemFps(24.1))
    }

    /**
     * Whatever is printed has to parse back: the same string is put into the fps box, which reads it
     * with `toDoubleOrNull`. A comma decimal — which the machine's locale would otherwise produce —
     * would not survive that round trip, so the formatter pins the point regardless of locale.
     */
    @Test
    fun `every printed rate parses back to the rate it came from`() {
        for (fps in listOf(30.0, 25.0, 59.94, 29.97, 50.5)) {
            assertEquals(fps, formatAtemFps(fps).toDoubleOrNull(), "$fps must survive the round trip")
        }
    }
}
