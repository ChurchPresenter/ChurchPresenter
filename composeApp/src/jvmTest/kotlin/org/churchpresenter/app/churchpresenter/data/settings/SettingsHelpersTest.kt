package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Per-output projection configuration.
 *
 * Outputs are addressed by index, and the list is grown on demand rather than pre-sized — so
 * assigning to output 3 when only one exists has to fill the gap rather than throw or overwrite the
 * wrong one. Removing an output shifts every later index down, which is the same operation that
 * decides which physical screen shows what.
 */
class ProjectionSettingsTest {

    private fun assignment(display: Int) = ScreenAssignment(targetDisplay = display)

    // ── Reading an output ───────────────────────────────────────────────────────

    @Test
    fun `an output that was never configured reads as the default`() {
        assertEquals(
            ScreenAssignment(),
            ProjectionSettings().getAssignment(0),
            "a machine with more screens than the settings file knew about must still start",
        )
    }

    @Test
    fun `a configured output reads back`() {
        val settings = ProjectionSettings().withAssignment(0, assignment(2))

        assertEquals(2, settings.getAssignment(0).targetDisplay)
    }

    // ── Assigning an output ─────────────────────────────────────────────────────

    @Test
    fun `assigning past the end fills the gap with defaults`() {
        val settings = ProjectionSettings().withAssignment(3, assignment(7))

        assertEquals(4, settings.screenAssignments.size)
        assertEquals(7, settings.getAssignment(3).targetDisplay)
        assertTrue(
            (0..2).all { settings.getAssignment(it) == ScreenAssignment() },
            "the outputs in between are unconfigured, not broken",
        )
    }

    @Test
    fun `assigning an output leaves the others alone`() {
        val settings = ProjectionSettings()
            .withAssignment(0, assignment(1))
            .withAssignment(1, assignment(2))
            .withAssignment(0, assignment(9))

        assertEquals(9, settings.getAssignment(0).targetDisplay)
        assertEquals(2, settings.getAssignment(1).targetDisplay, "changing one screen must not move another")
    }

    @Test
    fun `assigning returns a new settings object rather than changing this one`() {
        // NB: a fresh ProjectionSettings already carries one screen, so "unchanged" is compared
        // against what it started with rather than against an empty list.
        val original = ProjectionSettings()
        val before = original.screenAssignments

        val updated = original.withAssignment(2, assignment(3))

        assertEquals(before, original.screenAssignments, "settings are copied on write, not mutated in place")
        assertEquals(3, updated.screenAssignments.size, "the copy grew to reach output 2")
        assertEquals(3, updated.getAssignment(2).targetDisplay)
    }

    // ── Browser-source outputs ──────────────────────────────────────────────────

    @Test
    fun `a browser source output is added at the end`() {
        val settings = ProjectionSettings().addBrowserSourceOutput().addBrowserSourceOutput()

        assertEquals(2, settings.browserSourceOutputs.size)
    }

    @Test
    fun `a browser source output can be configured by index`() {
        val settings = ProjectionSettings()
            .addBrowserSourceOutput()
            .withBrowserSourceOutput(0, assignment(4))

        assertEquals(4, settings.getBrowserSourceOutput(0).targetDisplay)
    }

    @Test
    fun `configuring a browser source output past the end fills the gap`() {
        val settings = ProjectionSettings().withBrowserSourceOutput(2, assignment(5))

        assertEquals(3, settings.browserSourceOutputs.size)
        assertEquals(5, settings.getBrowserSourceOutput(2).targetDisplay)
    }

    @Test
    fun `removing an output shifts the ones after it down`() {
        val settings = ProjectionSettings()
            .withBrowserSourceOutput(0, assignment(1))
            .withBrowserSourceOutput(1, assignment(2))
            .withBrowserSourceOutput(2, assignment(3))

        val afterRemoval = settings.removeBrowserSourceOutput(1)

        assertEquals(2, afterRemoval.browserSourceOutputs.size)
        assertEquals(1, afterRemoval.getBrowserSourceOutput(0).targetDisplay)
        assertEquals(
            3,
            afterRemoval.getBrowserSourceOutput(1).targetDisplay,
            "the third output becomes the second — the list is the numbering",
        )
    }

    @Test
    fun `removing an output that is not there changes nothing`() {
        val settings = ProjectionSettings().addBrowserSourceOutput()

        assertEquals(1, settings.removeBrowserSourceOutput(5).browserSourceOutputs.size)
        assertEquals(1, settings.removeBrowserSourceOutput(-1).browserSourceOutputs.size)
    }

    @Test
    fun `the two kinds of output are kept apart`() {
        val settings = ProjectionSettings()
            .withAssignment(0, assignment(1))
            .addBrowserSourceOutput()

        assertEquals(1, settings.screenAssignments.size)
        assertEquals(1, settings.browserSourceOutputs.size)
        assertNotEquals(
            settings.getAssignment(0).targetDisplay,
            settings.getBrowserSourceOutput(0).targetDisplay,
            "a screen and a browser source are configured separately",
        )
    }
}

/**
 * Swapping the two Bibles.
 *
 * The swap button exchanges primary and secondary — and with them every piece of styling that
 * belongs to each, some thirty paired fields written out by hand. A field missed from one direction
 * leaves the swapped Bible wearing the other's font, colour or alignment, which looks like a
 * rendering bug rather than a settings one.
 */
class BibleSettingsSwapTest {

    /** Both sides set to clearly different values, so a field copied one way is visible. */
    private fun configured() = BibleSettings(
        primaryBible = "kjv.spb",
        secondaryBible = "synodal.spb",
        primaryBibleColor = "#111111",
        secondaryBibleColor = "#222222",
        primaryBibleFontSize = 40,
        secondaryBibleFontSize = 80,
        primaryBibleFontType = "Georgia",
        secondaryBibleFontType = "Arial",
        primaryBibleBold = true,
        secondaryBibleBold = false,
    )

    @Test
    fun `the two bibles change places`() {
        val swapped = configured().swapped()

        assertEquals("synodal.spb", swapped.primaryBible)
        assertEquals("kjv.spb", swapped.secondaryBible)
    }

    @Test
    fun `styling travels with its bible`() {
        val swapped = configured().swapped()

        assertEquals("#222222", swapped.primaryBibleColor, "the Synodal text keeps its own colour")
        assertEquals("#111111", swapped.secondaryBibleColor)
        assertEquals(80, swapped.primaryBibleFontSize)
        assertEquals(40, swapped.secondaryBibleFontSize)
        assertEquals("Arial", swapped.primaryBibleFontType)
        assertEquals("Georgia", swapped.secondaryBibleFontType)
        assertTrue(swapped.secondaryBibleBold)
        assertFalse(swapped.primaryBibleBold)
    }

    @Test
    fun `swapping twice puts everything back exactly as it was`() {
        // The strongest check available: any field copied in only one direction shows up here as a
        // value that did not come home, without having to name all thirty pairs.
        val original = configured()

        assertEquals(
            original,
            original.swapped().swapped(),
            "a field swapped one way only would survive the first swap and be lost on the second",
        )
    }

    /**
     * Note that this is NOT a no-op: the two sides ship with different lower-third text sizes — the
     * secondary line is smaller, because it is usually the translation under the primary reading —
     * so swapping default settings exchanges those defaults along with everything else.
     */
    @Test
    fun `swapping an unconfigured pair exchanges the two sides' own defaults`() {
        val defaults = BibleSettings()
        assertEquals(32, defaults.primaryBibleLowerThirdFontSize)
        assertEquals(28, defaults.secondaryBibleLowerThirdFontSize)

        val swapped = defaults.swapped()

        assertEquals(28, swapped.primaryBibleLowerThirdFontSize)
        assertEquals(32, swapped.secondaryBibleLowerThirdFontSize)
        assertEquals(defaults, swapped.swapped(), "and swapping back restores them")
    }

    @Test
    fun `settings that belong to neither bible are untouched`() {
        val original = configured().copy(storageDirectory = "/bibles")

        assertEquals("/bibles", original.swapped().storageDirectory, "the library folder is not a per-bible setting")
    }
}

/**
 * The stage monitor's layout defaults.
 *
 * Both lookups fall back to a built-in default for anything missing from a saved file, which is
 * what lets an older settings file survive a release that adds a content type. That fallback uses
 * `getValue`, so it throws rather than degrading if the default map itself is missing an entry —
 * making "every enum entry has a default" a real requirement rather than a tidiness one.
 */
class StageMonitorSettingsTest {

    @Test
    fun `every kind of content has a default zone`() {
        val defaults = StageMonitorSettings.defaultContentZones()

        val missing = StageMonitorContentType.entries.filter { it !in defaults }
        assertTrue(
            missing.isEmpty(),
            "zoneFor() reads these with getValue(), so a missing one throws when that content goes live: $missing",
        )
    }

    @Test
    fun `every drawn zone has a default style`() {
        val defaults = StageMonitorSettings.defaultZoneStyles()

        val missing = StageMonitorStyleZone.entries.filter { it !in defaults }
        assertTrue(missing.isEmpty(), "styleFor() would throw for these: $missing")
    }

    @Test
    fun `a content type missing from a saved file falls back to its default`() {
        val settings = StageMonitorSettings(contentZones = emptyMap())

        assertEquals(
            StageMonitorSettings.defaultContentZones().getValue(StageMonitorContentType.BIBLE),
            settings.zoneFor(StageMonitorContentType.BIBLE),
            "a file written before this content type existed must not break the stage monitor",
        )
    }

    @Test
    fun `a zone missing from a saved file falls back to its default style`() {
        val settings = StageMonitorSettings(zoneStyles = emptyMap())

        assertEquals(
            StageMonitorSettings.defaultZoneStyles().getValue(StageMonitorStyleZone.TOP_LEFT),
            settings.styleFor(StageMonitorStyleZone.TOP_LEFT),
        )
    }

    @Test
    fun `a configured zone wins over the default`() {
        val settings = StageMonitorSettings(
            contentZones = mapOf(StageMonitorContentType.BIBLE to StageMonitorZone.BOTTOM_RIGHT)
        )

        assertEquals(StageMonitorZone.BOTTOM_RIGHT, settings.zoneFor(StageMonitorContentType.BIBLE))
        assertEquals(
            StageMonitorSettings.defaultContentZones().getValue(StageMonitorContentType.CLOCK),
            settings.zoneFor(StageMonitorContentType.CLOCK),
            "configuring one zone must not clear the rest",
        )
    }

    @Test
    fun `the defaults put the reading and what is next side by side`() {
        val defaults = StageMonitorSettings.defaultContentZones()

        assertEquals(StageMonitorZone.TOP_LEFT, defaults.getValue(StageMonitorContentType.BIBLE))
        assertEquals(StageMonitorZone.TOP_LEFT, defaults.getValue(StageMonitorContentType.SONGS))
        assertEquals(
            StageMonitorZone.TOP_RIGHT,
            defaults.getValue(StageMonitorContentType.NEXT),
            "what is coming next sits beside what is live — that is the point of the screen",
        )
        assertEquals(StageMonitorZone.BOTTOM_MIDDLE, defaults.getValue(StageMonitorContentType.CLOCK))
    }
}

/**
 * Which grid a Companion surface registers, per placement.
 *
 * Each placement carries its own rows, columns and bitmap size so a sidebar can show a compact grid
 * while the tab shows a full one. The four lookups are near-identical `when` blocks over the same
 * enum, which is exactly where a copy-paste hands the sidebar the tab's numbers.
 */
class CompanionSatelliteSettingsTest {

    private val settings = CompanionSatelliteSettings(
        showInTab = true,
        showInLeftSidebar = false,
        showInRightSidebar = true,
        tabRows = 4, tabColumns = 8, tabBitmapSize = 72, tabMaxButtonSizeDp = 0,
        leftSidebarRows = 2, leftSidebarColumns = 3, leftSidebarBitmapSize = 48, leftSidebarMaxButtonSizeDp = 40,
        rightSidebarRows = 6, rightSidebarColumns = 1, rightSidebarBitmapSize = 96, rightSidebarMaxButtonSizeDp = 60,
    )

    @Test
    fun `each placement reports whether it is shown`() {
        assertTrue(settings.isEnabled(CompanionSurfacePlacement.TAB))
        assertFalse(settings.isEnabled(CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertTrue(settings.isEnabled(CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `each placement reports its own grid`() {
        assertEquals(4 to 8, settings.rowsFor(CompanionSurfacePlacement.TAB) to settings.columnsFor(CompanionSurfacePlacement.TAB))
        assertEquals(2 to 3, settings.rowsFor(CompanionSurfacePlacement.LEFT_SIDEBAR) to settings.columnsFor(CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertEquals(6 to 1, settings.rowsFor(CompanionSurfacePlacement.RIGHT_SIDEBAR) to settings.columnsFor(CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `each placement reports its own bitmap size`() {
        // This one is sent to Companion at registration, so a wrong value means wrong-sized buttons.
        assertEquals(72, settings.bitmapSizeFor(CompanionSurfacePlacement.TAB))
        assertEquals(48, settings.bitmapSizeFor(CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertEquals(96, settings.bitmapSizeFor(CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `each placement reports its own display cap`() {
        assertEquals(0, settings.maxButtonSizeDpFor(CompanionSurfacePlacement.TAB), "0 means grow to fill")
        assertEquals(40, settings.maxButtonSizeDpFor(CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertEquals(60, settings.maxButtonSizeDpFor(CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `every placement is answered by every lookup`() {
        // The `when` blocks are exhaustive, so this is really a guard for a placement added later.
        CompanionSurfacePlacement.entries.forEach {
            settings.isEnabled(it)
            assertTrue(settings.rowsFor(it) > 0, "$it has no rows")
            assertTrue(settings.columnsFor(it) > 0, "$it has no columns")
            assertTrue(settings.bitmapSizeFor(it) > 0, "$it has no bitmap size")
        }
    }

    @Test
    fun `a connection shown nowhere is enabled nowhere`() {
        val hidden = CompanionSatelliteSettings()

        assertTrue(CompanionSurfacePlacement.entries.none { hidden.isEnabled(it) }, "a new connection starts hidden")
    }
}
