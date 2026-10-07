package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.isScreenIndexValid
import org.churchpresenter.liveoutput.keyOutputScreenIndex
import org.churchpresenter.settings.DisplayRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which attached displays the output windows leave alone: the monitors marked "Don't use".
 *
 * `PresenterWindows` asks these with the AWT devices it is handed, which a headless test cannot
 * build; the decisions are checked here against the same `screenKey`s the devices' bounds give.
 */
class UnusedScreenWindowsTest {

    /** Device 0 is the operator's own display; 1 and 2 are the audience monitors. */
    private val attached = listOf(
        DisplayRect(0, 0, 1440, 900),
        DisplayRect(1440, 0, 1920, 1080),
        DisplayRect(3360, 0, 3840, 2160),
    )
    private val screenKeys = attached.map { it.key }
    private val foyer = attached[1].key
    private val nonPrimary = listOf(1, 2)

    // ── Which displays are unused ───────────────────────────────────────────────────────────────

    @Test
    fun `a display whose key is marked is unused, and the others are not`() {
        assertTrue(isUnusedScreenIndex(1, screenKeys, listOf(foyer)))
        assertFalse(isUnusedScreenIndex(2, screenKeys, listOf(foyer)))
        assertFalse(isUnusedScreenIndex(1, screenKeys, emptyList()))
    }

    @Test
    fun `an index past the attached displays is never unused`() {
        assertFalse(isUnusedScreenIndex(7, screenKeys, listOf(foyer)))
        assertFalse(isUnusedScreenIndex(-1, screenKeys, listOf(foyer)))
    }

    @Test
    fun `a display with no geometry is never unused`() {
        // A blank key in the list must not stand for every display AWT reports without bounds.
        assertFalse(isUnusedScreenIndex(0, listOf(""), listOf("")))
    }

    @Test
    fun `the positional list leaves out unused monitors and keeps the rest in order`() {
        assertEquals(listOf(2), usableScreenIndices(nonPrimary, screenKeys, listOf(foyer)))
        assertEquals(nonPrimary, usableScreenIndices(nonPrimary, screenKeys, emptyList()))
        assertEquals(emptyList(), usableScreenIndices(nonPrimary, screenKeys, listOf(foyer, attached[2].key)))
    }

    // ── The window an output gets ───────────────────────────────────────────────────────────────

    /** What `ScreenOutputs` opens on: the resolved index, unless it names an unused monitor. */
    private fun windowScreen(
        matchedByBounds: Int?,
        savedDisplay: Int,
        slot: Int,
        unused: List<String>,
    ): Int? = primaryOutputScreenIndex(
        matchedByBounds = matchedByBounds,
        savedDisplay = savedDisplay,
        screenCount = attached.size,
        positionalFallback = usableScreenIndices(nonPrimary, screenKeys, unused).getOrNull(slot),
    )
        ?.takeIf { isScreenIndexValid(it, attached.size) }
        ?.takeUnless { isUnusedScreenIndex(it, screenKeys, unused) }

    @Test
    fun `the positional fallback never lands on an unused monitor`() {
        // Slot 0's saved display is gone; by position it would have taken the foyer monitor.
        assertEquals(2, windowScreen(null, savedDisplay = 9, slot = 0, unused = listOf(foyer)))
        assertEquals(1, windowScreen(null, savedDisplay = 9, slot = 0, unused = emptyList()))
    }

    @Test
    fun `a slot whose turn has no usable monitor left gets no window`() {
        assertNull(windowScreen(null, savedDisplay = 9, slot = 1, unused = listOf(foyer)))
    }

    @Test
    fun `a row resolving to an unused monitor by its bounds gets no window`() {
        assertNull(windowScreen(matchedByBounds = 1, savedDisplay = 1, slot = 0, unused = listOf(foyer)))
        assertEquals(2, windowScreen(matchedByBounds = 2, savedDisplay = 2, slot = 1, unused = listOf(foyer)))
    }

    @Test
    fun `a row resolving to an unused monitor by its saved index gets no window`() {
        assertNull(windowScreen(matchedByBounds = null, savedDisplay = 1, slot = 0, unused = listOf(foyer)))
    }

    // ── The key window ──────────────────────────────────────────────────────────────────────────

    /** What `KeyOutputWindow` opens on, the same way. */
    private fun keyScreen(matchedByBounds: Int?, savedIndex: Int, unused: List<String>): Int? =
        keyOutputScreenIndex(matchedByBounds, savedIndex)
            .takeIf { isScreenIndexValid(it, attached.size) }
            ?.takeUnless { isUnusedScreenIndex(it, screenKeys, unused) }

    @Test
    fun `a key resolving to an unused monitor gets no window`() {
        assertNull(keyScreen(matchedByBounds = 1, savedIndex = 1, unused = listOf(foyer)))
        assertNull(keyScreen(matchedByBounds = null, savedIndex = 1, unused = listOf(foyer)))
    }

    @Test
    fun `a key on a monitor in use still opens`() {
        assertEquals(2, keyScreen(matchedByBounds = 2, savedIndex = 2, unused = listOf(foyer)))
        assertEquals(1, keyScreen(matchedByBounds = 1, savedIndex = 1, unused = emptyList()))
    }
}
