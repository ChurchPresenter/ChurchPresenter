@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Media tab's seek bar — the widest single piece of the tab that was never driven.
 *
 * It is a hand-drawn control rather than a `Slider`: a `Canvas` track inside a `Box` whose only
 * modifiers are `hoverable` and two `pointerInput` blocks. That means **it contributes no semantics
 * node**, so there is nothing to select it by and nothing to read back from it — no value, no range,
 * no text of its own. Everything below therefore works the way an operator does: find where the bar
 * is on screen, click or drag there, and check where playback ended up.
 *
 * The bar is located from the two time labels that flank it in the same `Row`, which *are* selectable,
 * and the click lands on the root at that position. The assertions are proportional with tolerance —
 * "the middle of the bar seeks to about half way" — never a pixel or a millisecond count: the labels
 * are laid out by font metrics, which differ across the three target platforms, and there is 10dp of
 * arrangement spacing between each label and the track that the tolerance absorbs.
 *
 * **Not asserted: the hover handle.** It is drawn straight onto the `Canvas`, so it changes no node
 * and no state a test can read. Dragging still exercises the same branch (`dragging` sets `active`
 * exactly as hovering does) and dragging *does* have a readable outcome, which is why the drag tests
 * below are the ones that carry it.
 */
class MediaTabSeekBarTest {

    /** A tab with something loaded and a known duration — the only state the bar needs to appear. */
    private fun seekBarTab(durationMs: Long = 200_000L, block: ComposeUiTest.(vm: MediaViewModel) -> Unit) =
        mediaTab { vm, _ ->
            vm.loadMedia("http://example.test/service.mp4", Constants.MEDIA_TYPE_URL)
            vm.setDuration(durationMs)
            waitForIdle()
            block(vm)
        }

    /**
     * Where the track is: the gap between the elapsed label and the total label, which sit at either
     * end of the same centred `Row`. Their own bounds move with the font, so the region is derived
     * rather than assumed.
     */
    private fun ComposeUiTest.trackBounds(elapsed: String, total: String): Rect {
        val left = onNodeWithText(elapsed).fetchSemanticsNode().boundsInRoot
        val right = onNodeWithText(total).fetchSemanticsNode().boundsInRoot
        assertTrue(right.left > left.right, "the two time labels should flank the track, got $left and $right")
        return Rect(left = left.right, top = left.top, right = right.left, bottom = left.bottom)
    }

    /** Clicks at [fraction] across the track region. */
    private fun ComposeUiTest.clickTrackAt(fraction: Float, elapsed: String, total: String) {
        val track = trackBounds(elapsed, total)
        onRoot().performMouseInput {
            moveTo(Offset(track.left + track.width * fraction, track.center.y))
            press()
            release()
        }
        waitForIdle()
    }

    private fun assertNear(expectedFraction: Float, position: Long, duration: Long, tolerance: Float = 0.06f) {
        val actual = position.toFloat() / duration
        assertTrue(
            abs(actual - expectedFraction) <= tolerance,
            "expected about ${expectedFraction * 100}% of $duration ms, got $position (${actual * 100}%)"
        )
    }

    // ── Presence ────────────────────────────────────────────────────────────────

    @Test
    fun `the bar appears with the duration only once one is known`() = mediaTab { vm, _ ->
        vm.loadMedia("http://example.test/service.mp4", Constants.MEDIA_TYPE_URL)
        waitForIdle()
        assertTrue(
            onAllNodesWithText("3:20").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
            "with no duration reported yet there is nothing to seek along"
        )

        vm.setDuration(200_000L)
        waitForIdle()
        onNodeWithText("3:20").assertExists()
        onNodeWithText("0:00").assertExists()
    }

    @Test
    fun `the elapsed label follows playback without any interaction`() = seekBarTab { vm ->
        vm.setCurrentPosition(65_000L)
        waitForIdle()

        onNodeWithText("1:05").assertExists()
        onNodeWithText("3:20").assertExists()
    }

    @Test
    fun `an hour-long file is labelled in hours at both ends`() = seekBarTab(3_725_000L) { vm ->
        vm.setCurrentPosition(3_600_000L)
        waitForIdle()

        onNodeWithText("1:00:00").assertExists()
        onNodeWithText("1:02:05").assertExists()
    }

    // ── Clicking to seek ────────────────────────────────────────────────────────

    @Test
    fun `clicking the middle of the bar seeks to about half way`() = seekBarTab { vm ->
        clickTrackAt(0.5f, elapsed = "0:00", total = "3:20")

        assertNear(0.5f, vm.currentPosition, vm.duration)
    }

    @Test
    fun `clicking further along seeks further along`() = seekBarTab { vm ->
        clickTrackAt(0.25f, elapsed = "0:00", total = "3:20")
        val quarter = vm.currentPosition
        assertNear(0.25f, quarter, vm.duration)

        // The label has moved, so the track is re-derived against what is on screen now.
        clickTrackAt(0.75f, elapsed = vm.formatTime(quarter), total = "3:20")
        assertNear(0.75f, vm.currentPosition, vm.duration)
        assertTrue(vm.currentPosition > quarter, "seeking right of the last position must move forward")
    }

    @Test
    fun `clicking near the left end rewinds to near the start`() = seekBarTab { vm ->
        vm.setCurrentPosition(150_000L)
        waitForIdle()

        // Not 0f: the track begins one arrangement gap right of the label's edge, so a click at
        // the very start of the derived region lands in the spacing and reaches no gesture at all.
        clickTrackAt(0.05f, elapsed = "2:30", total = "3:20")

        assertTrue(
            vm.currentPosition < vm.duration / 10,
            "a click at the left end should rewind to near the start, got ${vm.currentPosition}"
        )
    }

    @Test
    fun `a seek is reported to the player rather than only moving the label`() = seekBarTab { vm ->
        // seekVersion is what VideoPlayer watches; a bar that updated only the displayed position
        // would look identical on screen and never move the actual playback.
        val before = vm.seekVersion

        clickTrackAt(0.5f, elapsed = "0:00", total = "3:20")

        assertTrue(vm.seekVersion > before, "the seek must be signalled to the player, not just drawn")
    }

    // ── Dragging to scrub ───────────────────────────────────────────────────────

    @Test
    fun `dragging across the bar scrubs to where the drag ends`() = seekBarTab { vm ->
        val track = trackBounds("0:00", "3:20")
        val y = track.center.y

        onRoot().performMouseInput {
            moveTo(Offset(track.left + track.width * 0.2f, y))
            press()
            moveTo(Offset(track.left + track.width * 0.5f, y))
            moveTo(Offset(track.left + track.width * 0.8f, y))
            release()
        }
        waitForIdle()

        assertNear(0.8f, vm.currentPosition, vm.duration)
    }

    @Test
    fun `dragging back towards the start scrubs backwards`() = seekBarTab { vm ->
        vm.setCurrentPosition(180_000L)
        waitForIdle()
        val track = trackBounds("3:00", "3:20")
        val y = track.center.y

        onRoot().performMouseInput {
            moveTo(Offset(track.left + track.width * 0.9f, y))
            press()
            moveTo(Offset(track.left + track.width * 0.3f, y))
            release()
        }
        waitForIdle()

        assertNear(0.3f, vm.currentPosition, vm.duration)
    }

    @Test
    fun `dragging past the ends clamps rather than running off the file`() = seekBarTab { vm ->
        val track = trackBounds("0:00", "3:20")
        val y = track.center.y

        onRoot().performMouseInput {
            moveTo(Offset(track.left + track.width * 0.5f, y))
            press()
            moveTo(Offset(track.right + track.width, y))
            release()
        }
        waitForIdle()

        assertEquals(vm.duration, vm.currentPosition, "a drag off the right end stops at the last frame")

        onRoot().performMouseInput {
            moveTo(Offset(track.left + track.width * 0.5f, y))
            press()
            moveTo(Offset(track.left - track.width, y))
            release()
        }
        waitForIdle()

        assertEquals(0L, vm.currentPosition, "and a drag off the left end stops at the start")
    }
}
