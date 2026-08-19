package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
    fun `an output index outside the list reads as the default rather than throwing`() {
        val settings = ProjectionSettings().withAssignment(0, assignment(2))

        assertEquals(
            ScreenAssignment(),
            settings.getAssignment(9),
            "a stale settings file can name an output this machine no longer has",
        )
        assertEquals(ScreenAssignment(), settings.getAssignment(-1))
    }

    @Test
    fun `a browser source index outside the list reads as the default too`() {
        val settings = ProjectionSettings().addBrowserSourceOutput()

        assertEquals(ScreenAssignment(), settings.getBrowserSourceOutput(4))
        assertEquals(ScreenAssignment(), settings.getBrowserSourceOutput(-1))
        assertEquals(
            ScreenAssignment(),
            ProjectionSettings().getBrowserSourceOutput(0),
            "browser sources start empty, so reading one before any is added is the ordinary case",
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
/**
 * Swapping the first two translations.
 *
 * Swap used to exchange some ninety primary/secondary styling fields; with the ordered stack it is a
 * reorder, and each translation carries its own styling with it by construction. What still needs
 * pinning is that the styling genuinely travels with its bible rather than staying at its position,
 * and that the retained legacy names follow the stack so an older build opens the right bibles.
 */
class BibleSettingsSwapTest {

    /** Both translations set to clearly different values, so styling that stayed put is visible. */
    private fun configured() = BibleSettings(primaryBible = "kjv.spb", secondaryBible = "synodal.spb")
        .migrateTranslations()
        .updateTranslation(0) { it.copy(
            textColor = "#111111",
            textFontSize = 40,
            textFontType = "Georgia",
            textBold = true,
        ) }
        .updateTranslation(1) { it.copy(
            textColor = "#222222",
            textFontSize = 80,
            textFontType = "Arial",
            textBold = false,
        ) }

    @Test
    fun `the two bibles change places`() {
        val swapped = configured().swapped()

        assertEquals(
            listOf("synodal.spb", "kjv.spb"),
            swapped.translationList().map { it.fileName },
        )
    }

    @Test
    fun `styling travels with its bible`() {
        val swapped = configured().swapped()
        val (first, second) = swapped.translationList()

        assertEquals("#222222", first.textColor, "the Synodal text keeps its own colour")
        assertEquals(80, first.textFontSize)
        assertEquals("Arial", first.textFontType)
        assertFalse(first.textBold)
        assertEquals("#111111", second.textColor)
        assertEquals(40, second.textFontSize)
        assertEquals("Georgia", second.textFontType)
        assertTrue(second.textBold)
    }

    @Test
    fun `swapping twice puts everything back exactly as it was`() {
        val original = configured()

        assertEquals(
            original,
            original.swapped().swapped(),
            "anything moved one way only would survive the first swap and be lost on the second",
        )
    }

    @Test
    fun `the retained legacy names follow the stack`() {
        // Their styling stays frozen at whatever the conversion wrote, but the selection tracks the
        // stack -- otherwise a rollback would open bibles the operator stopped using.
        val swapped = configured().swapped()

        assertEquals("synodal.spb", swapped.primaryBible)
        assertEquals("kjv.spb", swapped.secondaryBible)
    }

    @Test
    fun `swapping a stack of fewer than two does nothing`() {
        val single = BibleSettings(primaryBible = "kjv.spb").migrateTranslations()

        assertEquals(single, single.swapped(), "there is no second translation to exchange with")
        assertEquals(BibleSettings(), BibleSettings().swapped())
    }

    @Test
    fun `settings that belong to neither bible are untouched`() {
        val original = configured().copy(storageDirectory = "/bibles")

        assertEquals("/bibles", original.swapped().storageDirectory, "the library folder is not a per-bible setting")
    }
}

/**
 * The ordered translation stack, and the one-way conversion onto it.
 *
 * The stack replaced a primary/secondary pair plus a mode toggle. What matters is that a settings
 * file written before the list existed still presents correctly, that converting it carries the
 * styling rather than resetting everyone to white Arial, and that the old fields survive the
 * conversion so a user who rolls back to an earlier build does not open a blank Bible panel.
 */
class BibleTranslationListTest {

    @Test
    fun `a settings file with no list still presents its primary and secondary pair`() {
        val settings = BibleSettings(
            primaryBible = "first.spb",
            secondaryBible = "second.spb",
            primaryBibleColor = "#111111",
            secondaryBibleColor = "#222222",
        )

        val translations = settings.translationList()

        assertEquals(listOf("first.spb", "second.spb"), translations.map { it.fileName })
        assertEquals(listOf("#111111", "#222222"), translations.map { it.textColor })
    }

    @Test
    fun `converting carries each bible's styling onto its translation`() {
        val migrated = BibleSettings(
            primaryBible = "first.spb",
            secondaryBible = "second.spb",
            primaryBibleColor = "#111111",
            primaryBibleFontSize = 88,
            primaryBibleLowerThirdColor = "#AAAAAA",
            secondaryBibleColor = "#222222",
            secondaryBibleLowerThirdEnabled = false,
        ).migrateTranslations()

        assertEquals(listOf("first.spb", "second.spb"), migrated.translations.map { it.fileName })
        assertEquals("#111111", migrated.translations[0].textColor)
        assertEquals(88, migrated.translations[0].textFontSize)
        assertEquals(
            "#AAAAAA",
            migrated.translations[0].lowerThirdTextColor,
            "lower-third styling has to come across too, or a lower third silently restyles itself",
        )
        assertEquals(
            false,
            migrated.translations[1].lowerThirdEnabled,
            "a second bible deliberately kept out of the lower third must stay out of it",
        )
    }

    @Test
    fun `converting leaves the old fields alone so an older build can still read the file`() {
        val migrated = BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb")
            .migrateTranslations()

        assertEquals("first.spb", migrated.primaryBible)
        assertEquals("second.spb", migrated.secondaryBible)
    }

    @Test
    fun `converting an already converted file changes nothing`() {
        val once = BibleSettings(primaryBible = "first.spb").migrateTranslations()
        val twice = once.addTranslation("second.spb").migrateTranslations()

        assertEquals(
            listOf("first.spb", "second.spb"),
            twice.translations.map { it.fileName },
            "a second conversion must not throw away translations added since the first",
        )
    }

    @Test
    fun `a bible with nothing configured converts to an empty stack`() {
        assertEquals(emptyList(), BibleSettings().migrateTranslations().translations)
    }

    @Test
    fun `translations keep their style while being reordered`() {
        val settings = BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb")
            .migrateTranslations()
            .addTranslation("third.spb")
            .updateTranslation(2) { it.copy(textColor = "#333333", textFontType = "Georgia") }
            .moveTranslation(2, -1)

        assertEquals(listOf("first.spb", "third.spb", "second.spb"), settings.translationList().map { it.fileName })
        assertEquals("#333333", settings.translationList()[1].textColor)
        assertEquals("Georgia", settings.translationList()[1].textFontType)
    }

    @Test
    fun `an edit aimed at a translation that is not there changes nothing`() {
        val settings = BibleSettings().addTranslation("first.spb").addTranslation("second.spb")

        // The Bible settings tab edits by row index, and a row can go while an edit is in flight.
        assertEquals(settings, settings.updateTranslation(5) { it.copy(textColor = "#FF0000") })
        assertEquals(settings, settings.updateTranslation(-1) { it.copy(textColor = "#FF0000") })
        assertEquals(
            listOf("first.spb", "second.spb"),
            settings.updateTranslation(2) { it.copy(fileName = "ghost.spb") }
                .translationList().map { it.fileName },
            "an out-of-range edit must not append a translation the operator never chose",
        )
    }

    @Test
    fun `a blank file name is not a translation`() {
        val settings = BibleSettings().addTranslation("first.spb")

        assertEquals(
            listOf("first.spb"),
            settings.addTranslation("").translationList().map { it.fileName },
            "an empty pick would occupy a stack slot and present nothing",
        )
        assertEquals(emptyList(), BibleSettings().addTranslation("").translationList())
    }

    @Test
    fun `a move that would leave the stack is refused from either end`() {
        val settings = BibleSettings()
            .addTranslation("first.spb")
            .addTranslation("second.spb")
            .addTranslation("third.spb")
        val order = settings.translationList().map { it.fileName }

        assertEquals(order, settings.moveTranslation(0, -1).translationList().map { it.fileName },
            "the first cannot move up")
        assertEquals(order, settings.moveTranslation(2, 1).translationList().map { it.fileName },
            "nor the last move down")
        assertEquals(order, settings.moveTranslation(7, -1).translationList().map { it.fileName },
            "and a row that is not there moves nothing")
        assertEquals(order, settings.moveTranslation(-1, 1).translationList().map { it.fileName })
    }

    @Test
    fun `a translation cannot be selected twice`() {
        val settings = BibleSettings(primaryBible = "first.spb")
            .migrateTranslations()
            .addTranslation("first.spb")

        assertEquals(listOf("first.spb"), settings.translationList().map { it.fileName })
    }

    @Test
    fun `the stack stops accepting translations at the cap`() {
        val full = (1..Constants.MAX_BIBLE_TRANSLATIONS).fold(BibleSettings()) { settings, index ->
            settings.addTranslation("bible$index.spb")
        }
        val refused = full.addTranslation("one-too-many.spb")

        assertEquals(Constants.MAX_BIBLE_TRANSLATIONS, full.translationList().size)
        assertEquals(
            full.translationList().map { it.fileName },
            refused.translationList().map { it.fileName },
            "past the cap the add must be refused outright, not applied and then truncated",
        )
    }

    @Test
    fun `a stack written past the cap is trimmed to it, in order`() {
        val overfull = BibleSettings().withTranslations(
            (1..Constants.MAX_BIBLE_TRANSLATIONS + 2).map {
                BibleTranslationSettings(fileName = "bible$it.spb")
            },
        )

        assertEquals(
            (1..Constants.MAX_BIBLE_TRANSLATIONS).map { "bible$it.spb" },
            overfull.translationList().map { it.fileName },
            "a hand-edited file keeps its first translations rather than an arbitrary subset",
        )
    }

    @Test
    fun `removing a translation drops only that one`() {
        val settings = BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb")
            .migrateTranslations()
            .addTranslation("third.spb")
            .removeTranslation(0)

        assertEquals(listOf("second.spb", "third.spb"), settings.translationList().map { it.fileName })
    }

    @Test
    fun `adding a translation changes the module reload key`() {
        val before = BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb")
            .migrateTranslations()
        val after = before.addTranslation("third.spb")

        assertTrue(before.translationSelectionKey() != after.translationSelectionKey())
        assertEquals(listOf("first.spb", "second.spb", "third.spb"), after.translationSelectionKey())
    }

    @Test
    fun `swapping exchanges the first two translations`() {
        val settings = BibleSettings(primaryBible = "first.spb", secondaryBible = "second.spb")
            .migrateTranslations()
            .swapped()

        assertEquals(listOf("second.spb", "first.spb"), settings.translationList().map { it.fileName })
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

    @Test
    fun `each drawable zone maps to its matching style zone`() {
        assertEquals(StageMonitorStyleZone.TOP_LEFT, StageMonitorZone.TOP_LEFT.toStyleZone())
        assertEquals(StageMonitorStyleZone.TOP_RIGHT, StageMonitorZone.TOP_RIGHT.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_LEFT, StageMonitorZone.BOTTOM_LEFT.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_MIDDLE, StageMonitorZone.BOTTOM_MIDDLE.toStyleZone())
        assertEquals(StageMonitorStyleZone.BOTTOM_RIGHT, StageMonitorZone.BOTTOM_RIGHT.toStyleZone())
        assertEquals(StageMonitorStyleZone.FULL_SCREEN, StageMonitorZone.FULL_SCREEN.toStyleZone())
    }

    @Test
    fun `NONE is the only zone with no style zone`() {
        assertNull(StageMonitorZone.NONE.toStyleZone(), "NONE means 'not drawn', so it has no style")
        val unmapped = StageMonitorZone.entries.filter { it.toStyleZone() == null }
        assertEquals(
            listOf(StageMonitorZone.NONE),
            unmapped,
            "a new drawable zone added without a toStyleZone mapping would silently render unstyled",
        )
    }

    @Test
    fun `every style zone is the target of exactly one drawable zone`() {
        val mapped = StageMonitorZone.entries.mapNotNull { it.toStyleZone() }
        assertEquals(
            StageMonitorStyleZone.entries.toSet(),
            mapped.toSet(),
            "every drawable style zone must be reachable from a content zone",
        )
        assertEquals(mapped.size, mapped.toSet().size, "no two zones may collapse onto the same style zone")
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
        assertEquals(
            4 to 8,
            settings.rowsFor(CompanionSurfacePlacement.TAB) to settings.columnsFor(CompanionSurfacePlacement.TAB),
        )
        assertEquals(
            2 to 3,
            settings.rowsFor(
                CompanionSurfacePlacement.LEFT_SIDEBAR,
            ) to settings.columnsFor(CompanionSurfacePlacement.LEFT_SIDEBAR),
        )
        assertEquals(
            6 to 1,
            settings.rowsFor(
                CompanionSurfacePlacement.RIGHT_SIDEBAR,
            ) to settings.columnsFor(CompanionSurfacePlacement.RIGHT_SIDEBAR),
        )
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
