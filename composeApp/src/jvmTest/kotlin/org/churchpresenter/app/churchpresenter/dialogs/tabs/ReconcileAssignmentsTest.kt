package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Matching the stored output slots up against the monitors actually attached.
 *
 * This runs once per change to the display list, and every decision it makes is invisible until the
 * next service: a slot resolved to the wrong monitor sends the congregation's screen to the stage
 * display, and a slot left pointing at a monitor that has been unplugged reports that monitor's old
 * resolution for ever after — which is how a dev-fallback window ends up rendering at 3840x2160 on
 * a laptop.
 *
 * It was the body of a `LaunchedEffect` until the tab it lived in had to be broken up; pulling it
 * out is what makes these cases reachable without composing anything.
 */
class ReconcileAssignmentsTest {

    private fun screen(index: Int, primary: Boolean = false, w: Int = 1920, h: Int = 1080) =
        DetectedScreen(index = index, isPrimary = primary, boundsX = index * w, boundsY = 0, boundsW = w, boundsH = h)

    private val primary = screen(0, primary = true)
    private val projector = screen(1, w = 1280, h = 720)
    private val balcony = screen(2, w = 3840, h = 2160)

    private fun reconcile(
        stored: List<ScreenAssignment>,
        windows: Int,
        nonPrimary: List<DetectedScreen> = listOf(projector, balcony),
        all: List<DetectedScreen> = listOf(primary, projector, balcony),
    ) = reconcileAssignments(stored, windows, nonPrimary, all)

    @Test
    fun `a list that already matches is left alone`() {
        // Null rather than an equal copy: the caller writes settings on anything non-null, and a
        // write per recomposition is a write loop.
        val settled = listOf(ScreenAssignment(targetDisplay = 1), ScreenAssignment(targetDisplay = 2))

        assertNull(reconcile(settled, windows = 2))
    }

    @Test
    fun `the list grows to one slot per presenter window`() {
        val reconciled = reconcile(emptyList(), windows = 2)

        assertEquals(2, reconciled?.size)
    }

    @Test
    fun `a new slot takes the monitor at its own position`() {
        val reconciled = reconcile(emptyList(), windows = 2).orEmpty()

        assertEquals(projector.index, reconciled[0].targetDisplay)
        assertEquals(1280, reconciled[0].targetBoundsW, "its bounds come with it")
        assertEquals(balcony.index, reconciled[1].targetDisplay)
        assertEquals(3840, reconciled[1].targetBoundsW)
    }

    @Test
    fun `a slot with no monitor left for it is created as None`() {
        // A DeckLink-only machine, or more windows than screens.
        val reconciled = reconcile(emptyList(), windows = 3).orEmpty()

        assertEquals(Constants.KEY_TARGET_NONE, reconciled[2].targetDisplay)
        assertEquals(0, reconciled[2].targetBoundsW, "and with no bounds to report")
    }

    // ── auto (-1) ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an auto slot resolves to the monitor at its position`() {
        val reconciled = reconcile(listOf(ScreenAssignment(targetDisplay = -1)), windows = 1).orEmpty()

        assertEquals(projector.index, reconciled[0].targetDisplay)
        assertEquals(1280, reconciled[0].targetBoundsW)
        assertEquals(720, reconciled[0].targetBoundsH)
    }

    @Test
    fun `an auto slot with no monitor for it becomes None and loses its bounds`() {
        val stale = ScreenAssignment(targetDisplay = -1, targetBoundsW = 3840, targetBoundsH = 2160)

        val reconciled = reconcile(listOf(stale), windows = 1, nonPrimary = emptyList()).orEmpty()

        assertEquals(Constants.KEY_TARGET_NONE, reconciled[0].targetDisplay)
        // The bounds matter as much as the target: `outputSizeOf` prefers real bounds whenever they
        // are non-zero, so a slot that keeps them goes on claiming a 4K monitor it no longer drives.
        assertEquals(0, reconciled[0].targetBoundsW)
        assertEquals(0, reconciled[0].targetBoundsH)
    }

    @Test
    fun `None is preserved rather than resolved`() {
        // -2 is a choice the operator made; only -1 means "work it out for me".
        val none = ScreenAssignment(targetDisplay = Constants.KEY_TARGET_NONE)

        assertNull(reconcile(listOf(none), windows = 1))
    }

    // ── a monitor that has gone ─────────────────────────────────────────────────────────────────

    @Test
    fun `a slot pointed at a monitor that is no longer attached is reset`() {
        val unplugged = ScreenAssignment(targetDisplay = 7, targetBoundsW = 3840, targetBoundsH = 2160)

        val reconciled = reconcile(listOf(unplugged), windows = 1).orEmpty()

        assertEquals(Constants.KEY_TARGET_NONE, reconciled[0].targetDisplay)
        assertEquals(0, reconciled[0].targetBoundsW, "its old resolution goes with it")
    }

    @Test
    fun `a DeckLink target is left alone however few monitors there are`() {
        // Only "screen" targets are checked against the monitor list; a DeckLink index means
        // something else entirely and is not this function's to second-guess.
        val deckLink = ScreenAssignment(targetDisplay = 0, targetType = "decklink")

        assertNull(reconcile(listOf(deckLink), windows = 1, all = emptyList()))
    }

    @Test
    fun `one stale slot does not disturb the slot beside it`() {
        val good = ScreenAssignment(targetDisplay = 1, targetBoundsW = 1280)
        val unplugged = ScreenAssignment(targetDisplay = 7, targetBoundsW = 3840)

        val reconciled = reconcile(listOf(good, unplugged), windows = 2).orEmpty()

        assertEquals(1, reconciled[0].targetDisplay, "the attached one is untouched")
        assertEquals(1280, reconciled[0].targetBoundsW)
        assertEquals(Constants.KEY_TARGET_NONE, reconciled[1].targetDisplay)
    }
}
