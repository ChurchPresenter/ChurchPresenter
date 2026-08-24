@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.atem.formatAtemFps

/**
 * The two read-only lines under the lower-third card: how much clip the switcher can hold, and what
 * M/E and keyer hardware it has.
 *
 * Both are written by a Test Connection and then persisted, so they are standing reference for the
 * operator rather than live status — which is what makes them testable from a fixture: posing the
 * detected counts is exactly the state a past connection would have left behind.
 *
 * The clip line has three shapes, and each is a decision worth pinning:
 *
 *  * **Unknown** — nothing detected, or a nonsensical frame rate to divide by.
 *  * **Equal banks** — the usual case, one capacity quoted once with the seconds it buys.
 *  * **Mixed banks** — an ATEM whose clip pool has been re-allocated unevenly, where quoting a single
 *    figure would be a lie about the smaller bank.
 *
 * The seconds figure is what makes the line worth having: frames mean nothing to whoever is deciding
 * whether an animation will fit, so the conversion by the detected fps is asserted, not just the
 * frame count.
 */
class AtemSettingsTabDetectedTest {

    // ── Clip capacity ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `clip capacity is unknown until a Test Connection has run`() = atemTab { _ ->
        onNodeWithText(AtemLabel.CAPACITY_UNKNOWN).assertExists("nothing has been detected yet")
    }

    @Test
    fun `equal clip banks are quoted once, in frames and seconds`() = atemTab(
        initial = atemSettings { copy(clipFps = 25.0, detectedClipMaxFrames = listOf(300, 300)) },
    ) { _ ->
        onNodeWithText("Clip capacity: 2 banks, each up to 300 frames (≈12.0 s) at 25 fps")
            .assertExists("300 frames at 25 fps is 12 seconds of animation")
        onNodeWithText(AtemLabel.CAPACITY_UNKNOWN).assertDoesNotExist()
    }

    /** The same pool at an NTSC rate buys less time, and the line has to say so. */
    @Test
    fun `the seconds quoted follow the detected frame rate`() = atemTab(
        initial = atemSettings { copy(clipFps = 59.94, detectedClipMaxFrames = listOf(600)) },
    ) { _ ->
        onNodeWithText("Clip capacity: 1 banks, each up to 600 frames (≈10.0 s) at 59.94 fps")
            .assertExists("600 frames at 59.94 fps is ten seconds, and the rate prints unrounded")
    }

    @Test
    fun `unevenly allocated clip banks are quoted separately`() = atemTab(
        initial = atemSettings { copy(clipFps = 25.0, detectedClipMaxFrames = listOf(300, 150)) },
    ) { _ ->
        onNodeWithText("Clip capacity: 2 banks, up to 300 / 150 frames at 25 fps")
            .assertExists("two different capacities cannot be quoted as one figure")
    }

    /** Repeats collapse: four banks of the same size are one capacity, not four. */
    @Test
    fun `repeated bank sizes collapse to one figure`() = atemTab(
        initial = atemSettings { copy(clipFps = 30.0, detectedClipMaxFrames = listOf(90, 90, 45, 45)) },
    ) { _ ->
        onNodeWithText("Clip capacity: 4 banks, up to 90 / 45 frames at 30 fps")
            .assertExists("four banks, two distinct sizes")
    }

    @Test
    fun `frames left out of every bank are reported`() = atemTab(
        initial = atemSettings {
            copy(clipFps = 25.0, detectedClipMaxFrames = listOf(300, 300), detectedUnassignedFrames = 45)
        },
    ) { _ ->
        onNodeWithText(
            "Clip capacity: 2 banks, each up to 300 frames (≈12.0 s) at 25 fps, 45 frames unassigned",
        ).assertExists("unallocated pool space is worth reclaiming, so it is called out")
    }

    @Test
    fun `no unassigned frames means no such remark`() = atemTab(
        initial = atemSettings {
            copy(clipFps = 25.0, detectedClipMaxFrames = listOf(300, 300), detectedUnassignedFrames = 0)
        },
    ) { _ ->
        onNodeWithText("Clip capacity: 2 banks, each up to 300 frames (≈12.0 s) at 25 fps")
            .assertExists("a fully allocated pool has nothing to remark on")
    }

    /** Dividing frames by a zero rate would print an infinity, so the line falls back to unknown. */
    @Test
    fun `a zero frame rate leaves capacity unknown`() = atemTab(
        initial = atemSettings { copy(clipFps = 0.0, detectedClipMaxFrames = listOf(300, 300)) },
    ) { _ ->
        onNodeWithText(AtemLabel.CAPACITY_UNKNOWN)
            .assertExists("frames cannot be turned into seconds without a rate")
    }

    // ── Detected M/E and keyers ─────────────────────────────────────────────────────────────────

    @Test
    fun `the keyer hardware is unknown until a Test Connection has run`() = atemTab { _ ->
        onNodeWithText(AtemLabel.KEYERS_UNKNOWN).assertExists("nothing has been detected yet")
    }

    @Test
    fun `every M-E is listed with the keyers it carries`() = atemTab(
        initial = atemSettings { copy(detectedKeyersPerMe = listOf(4, 2), detectedDownstreamKeyers = 2) },
    ) { _ ->
        onNodeWithText("Detected: M/E 1: 4 keys   M/E 2: 2 keys   DSK: 2")
            .assertExists("the ranges the M/E, keyer and DSK boxes are judged against")
        onNodeWithText(AtemLabel.KEYERS_UNKNOWN).assertDoesNotExist()
    }

    @Test
    fun `a switcher with no downstream keyers is listed without a DSK count`() = atemTab(
        initial = atemSettings { copy(detectedKeyersPerMe = listOf(1), detectedDownstreamKeyers = 0) },
    ) { _ ->
        onNodeWithText("Detected: M/E 1: 1 keys")
            .assertExists("a switcher without a DSK must not be listed as having zero of them")
    }

    // ── Frame-rate formatting ───────────────────────────────────────────────────────────────────

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
