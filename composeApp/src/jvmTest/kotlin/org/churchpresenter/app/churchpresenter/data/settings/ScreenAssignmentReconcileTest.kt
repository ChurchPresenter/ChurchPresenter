package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Bringing saved screen assignments into line with the displays actually plugged in.
 *
 * This runs once at startup, before the UI renders, and decides **which physical screen each output
 * goes to**. Getting it wrong is not a cosmetic bug: the congregation sees the wrong thing, or an
 * output lands on the operator's own laptop screen mid-service. It lived in `main.kt` inside a
 * `remember { }` at 0% coverage, mixed in with the `GraphicsEnvironment` calls that made it look
 * untestable — those stay at the call site; everything below is the decision.
 *
 * The case that matters most is the one it deliberately does *not* touch: an assignment naming a
 * display that is currently absent is left alone. See the test at the bottom.
 */
class ScreenAssignmentReconcileTest {

    private fun display(deviceIndex: Int, x: Int = 0, w: Int = 1920, h: Int = 1080) =
        ResolvedDisplay(deviceIndex = deviceIndex, x = x, y = 0, width = w, height = h)

    private fun auto() = ScreenAssignment(targetDisplay = -1)

    // ── Nothing to do ───────────────────────────────────────────────────────────

    @Test
    fun `an already-resolved set is left alone and reports no change`() {
        val saved = listOf(ScreenAssignment(targetDisplay = 1), ScreenAssignment(targetDisplay = 2))

        assertNull(
            reconcileScreenAssignments(saved, listOf(display(1), display(2)), deckLinkCount = 0),
            "a normal launch must not rewrite the settings file",
        )
    }

    @Test
    fun `no displays and no devices needs no slots`() {
        assertNull(reconcileScreenAssignments(emptyList(), emptyList(), deckLinkCount = 0))
    }

    // ── Adding missing slots ────────────────────────────────────────────────────

    @Test
    fun `a slot is created for each non-primary display, carrying its bounds`() {
        val result = assertNotNull(
            reconcileScreenAssignments(emptyList(), listOf(display(1, x = 1920), display(2, x = 3840)), 0)
        )

        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result.map { it.targetDisplay })
        assertEquals(listOf(1920, 3840), result.map { it.targetBoundsX })
        assertEquals(listOf(1920, 1920), result.map { it.targetBoundsW })
    }

    @Test
    fun `a DeckLink-only slot is created as none rather than left on auto`() {
        val result = assertNotNull(reconcileScreenAssignments(emptyList(), emptyList(), deckLinkCount = 2))

        assertEquals(2, result.size)
        assertEquals(
            listOf(Constants.KEY_TARGET_NONE, Constants.KEY_TARGET_NONE), result.map { it.targetDisplay },
            "an unresolved slot renders as if pointed at a screen — this is why it runs before the UI",
        )
    }

    @Test
    fun `displays fill the first slots and DeckLinks take the rest`() {
        val result = assertNotNull(
            reconcileScreenAssignments(emptyList(), listOf(display(1)), deckLinkCount = 1)
        )

        assertEquals(listOf(1, Constants.KEY_TARGET_NONE), result.map { it.targetDisplay })
    }

    // ── Resolving auto ──────────────────────────────────────────────────────────

    @Test
    fun `auto takes the display in its own position`() {
        val result = assertNotNull(
            reconcileScreenAssignments(listOf(auto(), auto()), listOf(display(1, x = 100), display(3, x = 200)), 0)
        )

        assertEquals(listOf(1, 3), result.map { it.targetDisplay })
        assertEquals(listOf(100, 200), result.map { it.targetBoundsX })
    }

    @Test
    fun `auto with no display behind it becomes none`() {
        val result = assertNotNull(reconcileScreenAssignments(listOf(auto()), emptyList(), deckLinkCount = 0))

        assertEquals(Constants.KEY_TARGET_NONE, result.single().targetDisplay)
    }

    @Test
    fun `the device index is the one in the full device list, not the non-primary list`() {
        // Primary is device 0, so the two extra screens are devices 1 and 2 — never 0 and 1.
        val result =
            assertNotNull(reconcileScreenAssignments(listOf(auto(), auto()), listOf(display(1), display(2)), 0))

        assertEquals(
            listOf(1, 2), result.map { it.targetDisplay },
            "using the non-primary position here sends every output to the wrong screen",
        )
    }

    // ── The case it must NOT touch ──────────────────────────────────────────────

    @Test
    fun `an assignment naming an absent display is left where the operator put it`() {
        val saved = listOf(ScreenAssignment(targetDisplay = 5, targetBoundsX = 999))

        assertNull(
            reconcileScreenAssignments(saved, emptyList(), deckLinkCount = 1),
            "a monitor unplugged today is usually plugged back in tomorrow; repointing it silently " +
                "moves the output somewhere nobody asked for",
        )
    }

    @Test
    fun `resolving auto does not disturb a neighbouring explicit assignment`() {
        val saved = listOf(ScreenAssignment(targetDisplay = 4, targetBoundsX = 777), auto())

        val result = assertNotNull(reconcileScreenAssignments(saved, listOf(display(1), display(2, x = 55)), 0))

        assertEquals(4, result[0].targetDisplay)
        assertEquals(777, result[0].targetBoundsX)
        assertEquals(2, result[1].targetDisplay)
        assertEquals(55, result[1].targetBoundsX)
    }

    @Test
    fun `other fields of an assignment survive the resolve`() {
        val saved = listOf(auto().copy(targetType = "decklink", keyTargetDisplay = 3, bibleTranslations = listOf(0, 2)))

        val result = assertNotNull(reconcileScreenAssignments(saved, listOf(display(1)), 0)).single()

        assertEquals("decklink", result.targetType)
        assertEquals(3, result.keyTargetDisplay)
        assertEquals(listOf(0, 2), result.bibleTranslations)
    }

    // ── Slots already sufficient ────────────────────────────────────────────────

    @Test
    fun `more saved slots than devices are not truncated`() {
        val saved = listOf(ScreenAssignment(targetDisplay = 1), ScreenAssignment(targetDisplay = 2))

        assertNull(
            reconcileScreenAssignments(saved, listOf(display(1)), deckLinkCount = 0),
            "unplugging a screen must not delete its saved output configuration",
        )
    }
}
