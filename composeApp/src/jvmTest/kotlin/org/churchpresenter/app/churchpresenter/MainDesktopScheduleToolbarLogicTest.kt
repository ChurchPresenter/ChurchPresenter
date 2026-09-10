package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two decisions the main window makes on its own: which schedule toolbar entries the operator has
 * hidden, and whether it — rather than the Bible tab — resolves a schedule item's verse.
 *
 * The second exists because `BibleTab` sits inside an `AnimatedContent` and leaves the composition
 * when the operator switches away, taking its own handler with it. Doing the work in both places
 * would put the verse live twice.
 */
class MainDesktopScheduleToolbarLogicTest {

    @Test
    fun `hiding a visible button adds it to the hidden set`() {
        val hidden = toggleHiddenScheduleButton(emptySet(), ScheduleToolbarButton.UNDO)
        assertEquals(setOf("UNDO"), hidden)
    }

    @Test
    fun `showing a hidden button takes it back out`() {
        val hidden = toggleHiddenScheduleButton(setOf("UNDO"), ScheduleToolbarButton.UNDO)
        assertEquals(emptySet(), hidden)
    }

    @Test
    fun `toggling twice returns to where it started`() {
        val once = toggleHiddenScheduleButton(setOf("SAVE"), ScheduleToolbarButton.UNDO)
        assertEquals(setOf("SAVE"), toggleHiddenScheduleButton(once, ScheduleToolbarButton.UNDO))
    }

    @Test
    fun `the other entries are left alone`() {
        val hidden = toggleHiddenScheduleButton(setOf("NEW", "OPEN"), ScheduleToolbarButton.SAVE)
        assertEquals(setOf("NEW", "OPEN", "SAVE"), hidden)
    }

    @Test
    fun `buttons are stored by name, so the set survives being written to settings`() {
        val hidden = toggleHiddenScheduleButton(emptySet(), ScheduleToolbarButton.PLANNING_CENTER)
        assertEquals(setOf(ScheduleToolbarButton.PLANNING_CENTER.name), hidden)
    }

    @Test
    fun `every toolbar entry can be hidden and shown again`() {
        ScheduleToolbarButton.entries.forEach { button ->
            val hidden = toggleHiddenScheduleButton(emptySet(), button)
            assertTrue(button.name in hidden, button.name)
            assertFalse(button.name in toggleHiddenScheduleButton(hidden, button), button.name)
        }
    }

    @Test
    fun `hiding every entry in turn leaves them all hidden`() {
        val hidden = ScheduleToolbarButton.entries.fold(emptySet<String>(), ::toggleHiddenScheduleButton)
        assertEquals(ScheduleToolbarButton.entries.map { it.name }.toSet(), hidden)
    }

    @Test
    fun `an unrecognised name already in the set is not disturbed`() {
        val hidden = toggleHiddenScheduleButton(setOf("FROM_AN_OLDER_BUILD"), ScheduleToolbarButton.NEW)
        assertEquals(setOf("FROM_AN_OLDER_BUILD", "NEW"), hidden)
    }

    // ── shouldMainResolveScheduleVerse ────────────────────────────────────────────────────────

    @Test
    fun `the main window resolves the verse while the Bible tab is elsewhere`() {
        assertTrue(shouldMainResolveScheduleVerse(activeTabIndex = 0, bibleTabIndex = 2))
    }

    @Test
    fun `the Bible tab resolves its own verse while it is the active one`() {
        assertFalse(shouldMainResolveScheduleVerse(activeTabIndex = 2, bibleTabIndex = 2))
    }

    @Test
    fun `with the Bible tab hidden the main window always resolves it`() {
        (0..5).forEach { active ->
            assertTrue(shouldMainResolveScheduleVerse(activeTabIndex = active, bibleTabIndex = -1), "$active")
        }
    }
}
