package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tab bar's visibility, ordering, index safety and selection logic — extracted out of
 * [MainDesktop] as [computeVisibleTabs], [clampedTabIndex] and [resolveTabSelection] so they can
 * be driven directly instead of through the whole 2000-line composable.
 *
 * These three decide what a user actually sees across the top of the app and what every tab
 * shortcut — F6 through F12, the Developer menu, a remote "project this" request, a Companion
 * Surface connect/disconnect — actually lands on. Getting any of them wrong either hides a tab the
 * settings say should be visible, points the UI at an index that no longer exists (crash), or
 * leaves a keyboard shortcut silently doing nothing.
 */
class MainDesktopTabsTest {

    // ── computeVisibleTabs ───────────────────────────────────────────────────────

    @Test
    fun `every tab is visible with nothing hidden and no extras enabled`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = emptySet(),
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )

        assertEquals(Tabs.entries.filter { it != Tabs.CROSSWORD && it != Tabs.COMPANION_SURFACE }, tabs)
    }

    @Test
    fun `a hidden tab from settings is excluded`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = setOf(Tabs.MEDIA.name),
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )

        assertFalse(tabs.contains(Tabs.MEDIA))
    }

    @Test
    fun `several hidden tabs are all excluded at once`() {
        val hidden = setOf(Tabs.MEDIA.name, Tabs.QA.name, Tabs.STT.name)
        val tabs = computeVisibleTabs(hiddenTabs = hidden, showCrosswordTab = false, hasCompanionTabConnections = false)

        assertFalse(tabs.contains(Tabs.MEDIA))
        assertFalse(tabs.contains(Tabs.QA))
        assertFalse(tabs.contains(Tabs.STT))
        assertTrue(tabs.contains(Tabs.BIBLE), "unrelated tabs must survive")
    }

    @Test
    fun `crossword never appears through the hidden-tabs list, only through its own flag`() {
        // CROSSWORD is filtered out unconditionally in the base list, then appended separately —
        // trying to hide it via settings must be a no-op either way.
        val hiddenAttempt = computeVisibleTabs(
            hiddenTabs = setOf(Tabs.CROSSWORD.name),
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )
        assertFalse(hiddenAttempt.contains(Tabs.CROSSWORD))

        val unlocked = computeVisibleTabs(
            hiddenTabs = setOf(Tabs.CROSSWORD.name),
            showCrosswordTab = true,
            hasCompanionTabConnections = false,
        )
        assertTrue(unlocked.contains(Tabs.CROSSWORD), "the Easter egg flag must win regardless of the hidden-tabs list")
    }

    @Test
    fun `crossword appears last once its Easter egg is unlocked`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = emptySet(),
            showCrosswordTab = true,
            hasCompanionTabConnections = false,
        )

        assertEquals(Tabs.CROSSWORD, tabs.last(), "the unlocked tab must not jump ahead of the regular tabs")
    }

    @Test
    fun `companion surface is hidden with no connections configured to show in the tab`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = emptySet(),
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )

        assertFalse(tabs.contains(Tabs.COMPANION_SURFACE))
    }

    @Test
    fun `companion surface appears once a connection asks to show in the tab`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = emptySet(),
            showCrosswordTab = false,
            hasCompanionTabConnections = true,
        )

        assertTrue(tabs.contains(Tabs.COMPANION_SURFACE))
    }

    @Test
    fun `hiding companion surface in settings still wins even with a live connection`() {
        val tabs = computeVisibleTabs(
            hiddenTabs = setOf(Tabs.COMPANION_SURFACE.name),
            showCrosswordTab = false,
            hasCompanionTabConnections = true,
        )

        assertFalse(tabs.contains(Tabs.COMPANION_SURFACE), "an explicit hide must not be overridden by a connection")
    }

    @Test
    fun `hiding every tab falls back to Bible rather than leaving nothing to click`() {
        val allTabNames = Tabs.entries.map { it.name }.toSet()
        val tabs = computeVisibleTabs(
            hiddenTabs = allTabNames,
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )

        assertEquals(listOf(Tabs.BIBLE), tabs)
    }

    // ── clampedTabIndex ──────────────────────────────────────────────────────────

    @Test
    fun `an index already inside range is left untouched`() {
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA)
        assertEquals(1, clampedTabIndex(1, tabs))
    }

    @Test
    fun `an index past the end of a shrunk tab list clamps to the last tab`() {
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS)
        assertEquals(1, clampedTabIndex(5, tabs), "a stale index must land on a real tab, not point past the list")
    }

    @Test
    fun `a negative index clamps to the first tab`() {
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS)
        assertEquals(0, clampedTabIndex(-1, tabs))
    }

    @Test
    fun `a single remaining tab clamps every index to zero`() {
        val tabs = listOf(Tabs.BIBLE)
        assertEquals(0, clampedTabIndex(4, tabs))
        assertEquals(0, clampedTabIndex(0, tabs))
    }

    // ── resolveTabSelection ──────────────────────────────────────────────────────

    @Test
    fun `selecting a visible tab moves the index to it`() {
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA)
        assertEquals(2, resolveTabSelection(Tabs.MEDIA, tabs, currentIndex = 0))
    }

    @Test
    fun `selecting a tab that is currently hidden leaves the index unchanged`() {
        // Every F-key shortcut and menu item routes through this — a hidden tab's shortcut must do
        // nothing instead of throwing or landing on the wrong tab.
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS)
        assertEquals(1, resolveTabSelection(Tabs.QA, tabs, currentIndex = 1))
    }

    @Test
    fun `re-selecting the already-active tab is idempotent`() {
        val tabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA)
        assertEquals(1, resolveTabSelection(Tabs.SONGS, tabs, currentIndex = 1))
    }

    @Test
    fun `every keyboard-shortcut tab resolves to its own distinct index`() {
        // Mirrors the F6-F12 dispatch in MainDesktop: each key must land on its own tab, not
        // silently fall through to whatever was previously selected.
        val tabs = computeVisibleTabs(
            hiddenTabs = emptySet(),
            showCrosswordTab = false,
            hasCompanionTabConnections = false,
        )
        val shortcutTabs = listOf(
            Tabs.BIBLE, Tabs.SONGS, Tabs.PICTURES, Tabs.PRESENTATION,
            Tabs.MEDIA, Tabs.LOWER_THIRD, Tabs.ANNOUNCEMENTS,
        )

        shortcutTabs.forEach { tab ->
            assertEquals(
                tabs.indexOf(tab),
                resolveTabSelection(tab, tabs, currentIndex = 0),
                "$tab must resolve to its own position",
            )
        }
    }
}
