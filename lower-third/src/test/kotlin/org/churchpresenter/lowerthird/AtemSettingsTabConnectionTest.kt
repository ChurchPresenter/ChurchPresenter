@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.atem.FakeAtemSwitcher

/**
 * The connection card: the four boxes that say where the ATEM is and what to render for it, plus the
 * Test Connection button beside them.
 *
 * Each box gets its own test because each guards its input differently — the address takes any text,
 * the two size boxes only store what parses as a whole number, and fps is the only one that takes a
 * decimal. The rule they all share is that **a box keeps what was typed even when the setting does
 * not**, so a rejected value stays visible instead of being silently reverted; that is asserted
 * alongside every guard.
 *
 * The button is driven three ways: against a [FakeAtemSwitcher] that answers (connected), against a
 * socket that never does (in flight), and at a port no socket can be opened on (failed).
 */
class AtemSettingsTabConnectionTest {

    /** The button, told apart from the status line beside it — which shows the same word — by its role. */
    private fun ComposeUiTest.testButton(caption: String = AtemLabel.TEST) =
        onNode(hasText(caption) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    // ── Where the ATEM is ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the IP address box stores what is typed`() = atemTab { get ->
        assertEquals("", get().atemSettings.host, "no address out of the box")

        type(atemHostBox(), "192.168.1.100")

        assertEquals("192.168.1.100", get().atemSettings.host, "the typed address must be stored")
        atemHostBox().assertShows("192.168.1.100", "the IP address box")
    }

    /** The address is free text — a hostname is as valid as a dotted quad, and clearing it is allowed. */
    @Test
    fun `the IP address box takes a hostname and can be cleared`() = atemTab(
        initial = atemSettings { copy(host = "192.168.1.100") },
    ) { get ->
        type(atemHostBox(), "atem.local")
        assertEquals("atem.local", get().atemSettings.host, "a hostname must be stored as typed")

        type(atemHostBox(), "")
        assertEquals("", get().atemSettings.host, "an emptied address must be stored as blank")
        atemHostBox().assertShows("", "the emptied IP address box")
    }

    @Test
    fun `the empty IP address box offers an example`() = atemTab { _ ->
        onNodeWithText(AtemLabel.HOST_HINT).assertExists("the placeholder shown while no address is set")

        type(atemHostBox(), "10.0.0.5")
        onNodeWithText(AtemLabel.HOST_HINT).assertDoesNotExist() // the example gives way to the address
    }

    // ── Port ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the port box stores what is typed`() = atemTab { get ->
        assertEquals(9910, get().atemSettings.port, "the ATEM control port is the default")

        type(atemPortBox(), "9920")

        assertEquals(9920, get().atemSettings.port, "the typed port must be stored")
        atemPortBox().assertShows("9920", "the port box")
    }

    @Test
    fun `a port that is not a number leaves the stored port alone`() = atemTab { get ->
        type(atemPortBox(), "nine-nine-one-zero")

        assertEquals(9910, get().atemSettings.port, "nonsense must not reach the stored port")
        atemPortBox().assertShows("nine-nine-one-zero", "the box, which does keep what was typed")

        type(atemPortBox(), "9920")
        assertEquals(9920, get().atemSettings.port, "and a real port must still land afterwards")
    }

    // ── Render size ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the render width box stores what is typed`() = atemTab { get ->
        assertEquals(1920, get().atemSettings.renderWidth, "1080p is the default")

        type(atemFieldUnder(AtemLabel.WIDTH), "1280")

        assertEquals(1280, get().atemSettings.renderWidth, "the typed width must be stored")
        assertEquals(1080, get().atemSettings.renderHeight, "and the height must be untouched")
        atemFieldUnder(AtemLabel.WIDTH).assertShows("1280", "the render width box")
    }

    @Test
    fun `a render width that is not a number leaves the stored width alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.WIDTH), "wide")

        assertEquals(1920, get().atemSettings.renderWidth, "nonsense must not reach the stored width")
        atemFieldUnder(AtemLabel.WIDTH).assertShows("wide", "the box, which does keep what was typed")
    }

    @Test
    fun `the render height box stores what is typed`() = atemTab { get ->
        assertEquals(1080, get().atemSettings.renderHeight, "1080p is the default")

        type(atemFieldUnder(AtemLabel.HEIGHT), "720")

        assertEquals(720, get().atemSettings.renderHeight, "the typed height must be stored")
        assertEquals(1920, get().atemSettings.renderWidth, "and the width must be untouched")
        atemFieldUnder(AtemLabel.HEIGHT).assertShows("720", "the render height box")
    }

    @Test
    fun `a render height that is not a number leaves the stored height alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.HEIGHT), "tall")

        assertEquals(1080, get().atemSettings.renderHeight, "nonsense must not reach the stored height")
        atemFieldUnder(AtemLabel.HEIGHT).assertShows("tall", "the box, which does keep what was typed")
    }

    // ── Clip fps ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the clip fps box stores whole rates`() = atemTab { get ->
        assertEquals(30.0, get().atemSettings.clipFps, "30 is the default")

        type(atemFieldUnder(AtemLabel.FPS), "50")

        assertEquals(50.0, get().atemSettings.clipFps, "the typed rate must be stored")
        atemFieldUnder(AtemLabel.FPS).assertShows("50", "the fps box, with no decimal point on a whole rate")
    }

    /**
     * The NTSC rates are why this box parses a decimal at all: truncating 59.94 to 59 makes every
     * uploaded clip the wrong length on air.
     */
    @Test
    fun `the clip fps box stores a fractional NTSC rate exactly`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.FPS), "59.94")

        assertEquals(59.94, get().atemSettings.clipFps, "the fractional rate must be stored unrounded")
        atemFieldUnder(AtemLabel.FPS).assertShows("59.94", "the fps box")
    }

    @Test
    fun `a clip fps that is not a number leaves the stored rate alone`() = atemTab { get ->
        type(atemFieldUnder(AtemLabel.FPS), "fast")

        assertEquals(30.0, get().atemSettings.clipFps, "nonsense must not reach the stored rate")
        atemFieldUnder(AtemLabel.FPS).assertShows("fast", "the box, which does keep what was typed")
    }

    @Test
    fun `an emptied clip fps box suggests the usual rates`() = atemTab { get ->
        onNodeWithText(AtemLabel.FPS_HINT).assertDoesNotExist() // a rate is always set to begin with

        type(atemFieldUnder(AtemLabel.FPS), "")

        assertEquals(30.0, get().atemSettings.clipFps, "an empty box must not wipe the stored rate")
        onNodeWithText(AtemLabel.FPS_HINT).assertExists("the placeholder shown while the box is empty")
    }

    // ── Test Connection ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `Test Connection is refused until an address is entered`() = atemTab { _ ->
        testButton().assertIsNotEnabled()

        type(atemHostBox(), "127.0.0.1")
        testButton().assertIsEnabled()

        type(atemHostBox(), "   ")
        testButton().assertIsNotEnabled() // blank, not merely empty
    }

    @Test
    fun `no status is reported until Test Connection is pressed`() = atemTab(
        initial = atemSettings { copy(host = "127.0.0.1") },
    ) { _ ->
        onNodeWithText(AtemLabel.NOT_CONNECTED).assertDoesNotExist()
        onNodeWithText(AtemLabel.CONNECTED).assertDoesNotExist()
        onNodeWithText(AtemLabel.CONNECTING).assertDoesNotExist()
    }

    /**
     * Pressed against a switcher that never answers, the button must show it is working and refuse a
     * second press — each attempt holds a socket of its own, so presses piling up would leak them.
     */
    @Test
    fun `Test Connection reports itself in flight and locks the button`() {
        SilentAtem().use { silent ->
            atemTab(initial = atemSettings { copy(host = "127.0.0.1", port = silent.port) }) { _ ->
                testButton().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(hasText(AtemLabel.CONNECTING)).fetchSemanticsNodes().size == 2
                }

                testButton(AtemLabel.CONNECTING).assertIsNotEnabled()
                onNodeWithText(AtemLabel.TEST).assertDoesNotExist() // the button renames itself
                onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                    useUnmergedTree = true,
                ).onFirst().assertExists("a spinner must sit in the button while the attempt is open")
            }
        }
    }

    /**
     * An attempt that is still open has learned nothing yet, so it must not have written anything —
     * least of all clear the counts a previous successful connection left behind, which are what the
     * slot and keyer boxes are ranged against.
     */
    @Test
    fun `an in-flight Test Connection writes nothing`() {
        SilentAtem().use { silent ->
            val initial = atemSettings {
                copy(
                    host = "127.0.0.1",
                    port = silent.port,
                    detectedStillSlots = 20,
                    detectedClipSlots = 2,
                    detectedMixEffects = 2,
                    detectedKeyersPerMe = listOf(4, 2),
                    detectedDownstreamKeyers = 2,
                    detectedClipMaxFrames = listOf(300, 300),
                )
            }
            atemTab(initial = initial) { get ->
                testButton().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(hasText(AtemLabel.CONNECTING)).fetchSemanticsNodes().size == 2
                }

                assertEquals(
                    initial.atemSettings,
                    get().atemSettings,
                    "an attempt in flight must leave every stored setting as it was",
                )
            }
        }
    }

    /**
     * Pressed against a switcher that answers, the button must say so, say what the switcher is, and
     * write what it found back to the settings: the detected counts are what every slot and keyer range
     * on the tab is judged against, and the switcher's own frame rate replaces whatever was typed.
     */
    @Test
    fun `a successful Test Connection reports the switcher and records what it detected`() {
        FakeAtemSwitcher(mixEffects = 2, keyersPerMe = 4, downstreamKeyers = 2, stillSlotCount = 20).use { switcher ->
            val initial = atemSettings { copy(host = "127.0.0.1", port = switcher.port, clipFps = 25.0) }
            atemTab(initial = initial) { get ->
                testButton().performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodes(hasText(AtemLabel.CONNECTED)).fetchSemanticsNodes().isNotEmpty()
                }

                onNodeWithText(
                    "Detected: 1080p59.94 (59.94 fps) — 2 clips × up to 720 frames (≈12.0s), 1000 frames unassigned",
                ).assertExists("the switcher's video mode and how much clip memory it holds")
                testButton().assertIsEnabled() // and the button is handed back

                val saved = get().atemSettings
                assertEquals(20, saved.detectedStillSlots, "the still slots the media pool holds")
                assertEquals(2, saved.detectedClipSlots, "the clip slots")
                assertEquals(2, saved.detectedMixEffects, "the M/Es")
                assertEquals(listOf(4, 4), saved.detectedKeyersPerMe, "the keyers on each M/E")
                assertEquals(2, saved.detectedDownstreamKeyers, "the downstream keyers")
                assertEquals(listOf(720, 720), saved.detectedClipMaxFrames, "the frames each clip bank holds")
                assertEquals(1000, saved.detectedUnassignedFrames, "the frames not yet given to a bank")
                assertEquals(60000.0 / 1001.0, saved.clipFps, 1e-9, "the switcher's rate, not the typed one")
                assertEquals("127.0.0.1", saved.host, "where the switcher is must be left alone")
                assertEquals(switcher.port, saved.port, "and so must the port")

                atemFieldUnder(AtemLabel.FPS).assertShows("59.94", "the fps box, re-formatted to the detected rate")
                assertEquals(
                    "${AtemLabel.STILL_SLOT} (1–20)",
                    captionOf(AtemLabel.STILL_SLOT),
                    "and the ranges the detected counts drive must now be on screen",
                )
                onNodeWithText("Detected: M/E 1: 4 keys   M/E 2: 4 keys   DSK: 2")
                    .assertExists("as must the detected hardware line")
            }
        }
    }

    /**
     * A failed attempt has to say so and hand the button back, or the operator is left looking at a
     * dead control. A port outside the legal range fails as the first packet is built, which is the
     * one failure a unit test can produce without waiting on a network.
     */
    @Test
    fun `a failed Test Connection reports the error and releases the button`() = atemTab(
        initial = atemSettings { copy(host = "127.0.0.1") },
    ) { get ->
        type(atemPortBox(), "70000")
        assertEquals(70000, get().atemSettings.port, "the out-of-range port must have been stored")

        testButton().performClick()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText("Error:", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText(AtemLabel.CONNECTED).assertDoesNotExist()
        onNodeWithText(AtemLabel.CONNECTING).assertDoesNotExist() // and it stops claiming to be working
        testButton().assertIsEnabled()
    }

    /**
     * The detected counts are the only record of what the switcher has, and they are what every slot
     * and keyer range is judged against. A connection that fails has learned nothing, so wiping them
     * would silently un-range every box on the tab — the boxes are read back to prove it did not.
     */
    @Test
    fun `a failed Test Connection keeps what an earlier connection detected`() {
        val initial = atemSettings {
            copy(
                host = "127.0.0.1",
                port = 70000, // out of range: the attempt fails as the first packet is built
                detectedStillSlots = 20,
                detectedClipSlots = 2,
                detectedMixEffects = 2,
                detectedKeyersPerMe = listOf(4, 2),
                detectedDownstreamKeyers = 2,
                detectedClipMaxFrames = listOf(300, 300),
                detectedUnassignedFrames = 12,
                clipFps = 25.0,
            )
        }
        atemTab(initial = initial) { get ->
            testButton().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasText("Error:", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }

            assertEquals(
                initial.atemSettings,
                get().atemSettings,
                "a failed attempt must leave every stored setting as it was",
            )
            assertEquals(
                "${AtemLabel.STILL_SLOT} (1–20)",
                captionOf(AtemLabel.STILL_SLOT),
                "and the ranges those counts drive must still be on screen",
            )
            onNodeWithText("Detected: M/E 1: 4 keys   M/E 2: 2 keys   DSK: 2")
                .assertExists("as must the detected hardware line")
        }
    }
}
