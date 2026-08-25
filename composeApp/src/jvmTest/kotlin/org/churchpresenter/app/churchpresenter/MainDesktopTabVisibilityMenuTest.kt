package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "Tab Visibility" gear-menu checkboxes — [visibleTabCount], [isOnlyVisibleTab] and
 * [toggleHiddenTabs] — extracted out of [MainDesktop]'s dropdown menu that lets an operator hide
 * tabs they don't use.
 *
 * The rule worth pinning down: the last remaining visible tab can't be hidden — its checkbox is
 * disabled and clicking it is a no-op — because there'd be nothing left to click to bring any tab
 * back. Crossword is excluded from the menu entirely (it's an Easter egg, not a settings toggle) so
 * it must never count toward or against that "last one" guard.
 */
class MainDesktopTabVisibilityMenuTest {

    private val allTabNames = Tabs.entries.map { it.name }.toSet()

    // ── visibleTabCount ──────────────────────────────────────────────────────────

    @Test
    fun `nothing hidden counts every tab except Crossword`() {
        assertEquals(Tabs.entries.size - 1, visibleTabCount(emptySet()))
    }

    @Test
    fun `hiding a tab lowers the count by one`() {
        assertEquals(Tabs.entries.size - 2, visibleTabCount(setOf(Tabs.MEDIA.name)))
    }

    @Test
    fun `hiding Crossword by name changes nothing -- it was never counted`() {
        assertEquals(visibleTabCount(emptySet()), visibleTabCount(setOf(Tabs.CROSSWORD.name)))
    }

    @Test
    fun `hiding every real tab leaves a count of zero`() {
        assertEquals(0, visibleTabCount(allTabNames))
    }

    // ── isOnlyVisibleTab ─────────────────────────────────────────────────────────

    @Test
    fun `a visible tab is the only one left when the count is one`() {
        assertTrue(isOnlyVisibleTab(Tabs.BIBLE, hiddenTabs = emptySet(), visibleCount = 1))
    }

    @Test
    fun `a visible tab is not the only one when other tabs remain`() {
        assertFalse(isOnlyVisibleTab(Tabs.BIBLE, hiddenTabs = emptySet(), visibleCount = 5))
    }

    @Test
    fun `an already-hidden tab is never reported as the only visible one`() {
        assertFalse(isOnlyVisibleTab(Tabs.MEDIA, hiddenTabs = setOf(Tabs.MEDIA.name), visibleCount = 1))
    }

    // ── toggleHiddenTabs ─────────────────────────────────────────────────────────

    @Test
    fun `toggling a visible tab hides it`() {
        val result = toggleHiddenTabs(emptySet(), Tabs.MEDIA)
        assertTrue(result.contains(Tabs.MEDIA.name))
    }

    @Test
    fun `toggling an already-hidden tab shows it again`() {
        val result = toggleHiddenTabs(setOf(Tabs.MEDIA.name), Tabs.MEDIA)
        assertFalse(result.contains(Tabs.MEDIA.name))
    }

    @Test
    fun `toggling one tab leaves the other hidden tabs untouched`() {
        val result = toggleHiddenTabs(setOf(Tabs.MEDIA.name, Tabs.QA.name), Tabs.STT)
        assertEquals(setOf(Tabs.MEDIA.name, Tabs.QA.name, Tabs.STT.name), result)
    }

    // ── The click callback's full rule, end to end ──────────────────────────────

    @Test
    fun `clicking the last visible tab's checkbox is guarded and would be a no-op`() {
        // Mirrors the composable's own guard: `if (!isOnlyVisible) onSettingsChange { ... }`.
        val hiddenTabs = allTabNames - Tabs.BIBLE.name
        val visibleCount = visibleTabCount(hiddenTabs)
        val isOnlyVisible = isOnlyVisibleTab(Tabs.BIBLE, hiddenTabs, visibleCount)

        assertTrue(isOnlyVisible, "with every other tab hidden, Bible must be reported as the only one left")
        // The real onClick never calls toggleHiddenTabs at all in this case; asserting what it
        // WOULD have produced shows why the guard exists -- Bible would vanish with nothing left.
        assertTrue(toggleHiddenTabs(hiddenTabs, Tabs.BIBLE).size == hiddenTabs.size + 1)
    }

    @Test
    fun `clicking any tab when others remain visible actually toggles it`() {
        val hiddenTabs = emptySet<String>()
        val visibleCount = visibleTabCount(hiddenTabs)
        val isOnlyVisible = isOnlyVisibleTab(Tabs.SONGS, hiddenTabs, visibleCount)

        assertFalse(isOnlyVisible)
        val newHidden = if (!isOnlyVisible) toggleHiddenTabs(hiddenTabs, Tabs.SONGS) else hiddenTabs
        assertTrue(newHidden.contains(Tabs.SONGS.name))
    }
}
