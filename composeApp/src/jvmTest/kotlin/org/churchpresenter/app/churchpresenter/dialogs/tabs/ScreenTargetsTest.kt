package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where each output slot's picture and key go, on the Projection tab: which choice a stored target
 * is, and that one display or DeckLink port only ever has one owner -- choosing it for one slot
 * takes it off every other slot's picture and key.
 */
class ScreenTargetsTest {

    private val none =
        DisplayOption("None", targetDisplay = Constants.KEY_TARGET_NONE, targetType = Constants.TARGET_TYPE_SCREEN)
    private val left = screen(1, x = 0)
    private val right = screen(2, x = 1920)
    private val port0 = DisplayOption("DeckLink 1", targetDisplay = 0, targetType = Constants.TARGET_TYPE_DECKLINK)
    private val port1 = DisplayOption("DeckLink 2", targetDisplay = 1, targetType = Constants.TARGET_TYPE_DECKLINK)
    private val options = listOf(none, left, right, port0, port1)

    private fun screen(index: Int, x: Int) = DisplayOption(
        "Screen $index", targetDisplay = index, targetType = Constants.TARGET_TYPE_SCREEN,
        boundsX = x, boundsY = 0, boundsW = 1920, boundsH = 1080,
    )

    private fun showing(option: DisplayOption) = ScreenAssignment(
        targetDisplay = option.targetDisplay, targetType = option.targetType,
        targetBoundsX = option.boundsX, targetBoundsY = option.boundsY,
        targetBoundsW = option.boundsW, targetBoundsH = option.boundsH,
    )

    private fun ScreenAssignment.keyingTo(option: DisplayOption) = copy(
        keyTargetDisplay = option.targetDisplay, keyTargetType = option.targetType,
        keyTargetBoundsX = option.boundsX, keyTargetBoundsY = option.boundsY,
        keyTargetBoundsW = option.boundsW, keyTargetBoundsH = option.boundsH,
    )

    private fun proj(vararg slots: ScreenAssignment) = ProjectionSettings(screenAssignments = slots.toList())

    // ── Which choice a stored target is ────────────────────────────────────────

    @Test
    fun `a DeckLink port is matched by its index, and a screen by where it sits`() {
        assertEquals(port1, currentPrimaryOption(options, showing(port1)))
        assertEquals(right, currentPrimaryOption(options, showing(right)))
        assertEquals(left, currentKeyOption(options, ScreenAssignment().keyingTo(left)))
        assertEquals(port0, currentKeyOption(options, ScreenAssignment().keyingTo(port0)))
    }

    @Test
    fun `a screen that moved is still found by its index, and an unknown one falls back to the first choice`() {
        val moved = showing(right).copy(targetBoundsX = 5000)
        assertEquals(right, currentPrimaryOption(options, moved), "the same display, rearranged on the desktop")
        assertEquals(none, currentPrimaryOption(options, showing(screen(9, x = 9000))))
    }

    // ── One owner per output ───────────────────────────────────────────────────

    @Test
    fun `a screen chosen for one slot's picture is taken off the slot that had it, and off any key`() {
        val before = proj(showing(left), showing(right).keyingTo(left))

        val after = withPrimaryTarget(before, 1, before.getAssignment(1), left, numScreens = 2)

        assertEquals(1, after.getAssignment(1).targetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).targetDisplay, "slot 1 no longer has it")
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(1).keyTargetDisplay, "nor does its own key")
    }

    @Test
    fun `a DeckLink port chosen for a picture is taken off every other picture and key on that port`() {
        val before = proj(showing(port0), ScreenAssignment().keyingTo(port0), showing(port1))

        val after = withPrimaryTarget(before, 2, before.getAssignment(2), port0, numScreens = 3)

        assertEquals(Constants.TARGET_TYPE_DECKLINK, after.getAssignment(2).targetType)
        assertEquals(0, after.getAssignment(2).targetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).targetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(1).keyTargetDisplay)
    }

    @Test
    fun `choosing nothing frees the slot and takes nothing from the others`() {
        val before = proj(showing(left), showing(right))

        val after = withPrimaryTarget(before, 0, before.getAssignment(0), none, numScreens = 2)

        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).targetDisplay)
        assertEquals(before.getAssignment(1), after.getAssignment(1))
    }

    @Test
    fun `a key chosen for a screen takes it off other pictures and keys, and off this slot's own picture`() {
        val before = proj(showing(left), showing(right).keyingTo(left), showing(left))

        val after = withKeyTarget(before, 2, before.getAssignment(2), left, numScreens = 3)

        assertEquals(1, after.getAssignment(2).keyTargetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).targetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(1).keyTargetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(2).targetDisplay, "a slot cannot key over itself")
    }

    @Test
    fun `a key chosen for a DeckLink port takes it off the other slot keying to it`() {
        val before = proj(ScreenAssignment().keyingTo(port1), showing(left))

        val after = withKeyTarget(before, 1, before.getAssignment(1), port1, numScreens = 2)

        assertEquals(1, after.getAssignment(1).keyTargetDisplay)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).keyTargetDisplay)
        assertEquals(1, after.getAssignment(1).targetDisplay, "a screen picture is not the same output as a port")
    }

    @Test
    fun `choosing no key leaves everyone else alone`() {
        val before = proj(showing(left).keyingTo(right), showing(right))
        val after = withKeyTarget(before, 0, before.getAssignment(0), none, numScreens = 2)
        assertEquals(Constants.KEY_TARGET_NONE, after.getAssignment(0).keyTargetDisplay)
        assertEquals(before.getAssignment(1), after.getAssignment(1))
    }

    // ── DeckLink inputs ────────────────────────────────────────────────────────

    @Test
    fun `a DeckLink port a scene takes input from is in conflict, and a screen never is`() {
        val camera = SceneSource.CameraSource(id = "c", name = "Camera", isDeckLink = true, deckLinkIndex = 1)
        val scenes = listOf(Scene(sources = listOf(camera)))

        assertTrue(hasDeckLinkInputConflict(port1, scenes))
        assertFalse(hasDeckLinkInputConflict(port0, scenes))
        assertFalse(hasDeckLinkInputConflict(left, scenes))
        assertFalse(hasDeckLinkInputConflict(none.copy(targetType = Constants.TARGET_TYPE_DECKLINK), scenes))
    }

    private val oneEdgeOff = listOf<(DisplayOption) -> DisplayOption>(
        { it.copy(boundsX = it.boundsX + 1) },
        { it.copy(boundsY = it.boundsY + 1) },
        { it.copy(boundsW = it.boundsW + 1) },
        { it.copy(boundsH = it.boundsH + 1) },
    )

    @Test
    fun `a stored screen that differs from every option in one edge is found by its index instead`() {
        oneEdgeOff.forEach { nudge ->
            val stored = showing(nudge(right))
            assertEquals(right, currentPrimaryOption(options, stored))
            assertEquals(right, currentKeyOption(options, ScreenAssignment().keyingTo(nudge(right))))
        }
    }

    @Test
    fun `a screen whose bounds differ in one edge is another screen, and keeps its owner`() {
        oneEdgeOff.forEach { nudge ->
            val other = showing(nudge(left)).keyingTo(nudge(left))
            val projection = ProjectionSettings().withAssignment(1, other)

            val primary = withPrimaryTarget(projection, 0, ScreenAssignment(), left, numScreens = 2)
            assertEquals(other, primary.getAssignment(1), "picture: $other")

            val key = withKeyTarget(projection, 0, ScreenAssignment(), left, numScreens = 2)
            assertEquals(other, key.getAssignment(1), "key: $other")
        }
    }

    @Test
    fun `a DeckLink port is only claimed from a slot on that same port`() {
        val onOtherPort = showing(port1).keyingTo(port1)
        val onScreen = showing(port0).copy(targetType = Constants.TARGET_TYPE_SCREEN)
            .keyingTo(port0).copy(keyTargetType = Constants.TARGET_TYPE_SCREEN)
        val projection = ProjectionSettings().withAssignment(1, onOtherPort).withAssignment(2, onScreen)

        val after = withKeyTarget(projection, 0, ScreenAssignment(), port0, numScreens = 3)

        assertEquals(onOtherPort, after.getAssignment(1))
        assertEquals(onScreen, after.getAssignment(2))
    }
}
