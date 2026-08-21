@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.StageMonitorSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives every control in a zone-style editor, and checks each one writes into its own zone.
 *
 * The tab builds six of these from one composable, each closing over a different
 * `StageMonitorStyleZone`, and every callback goes through the same
 * `zoneStyles + (zone to styleFor(zone).copy(...))` shape. The failure that shape invites is a
 * control writing into the wrong zone's entry, or replacing the whole map instead of one key — so
 * each test asserts the target zone took the value **and** that all five others kept theirs.
 *
 * Controls that carry a value are located by it: the fixture gives the zone under test a value no
 * other control on the tab holds. The style and alignment buttons have no value to search by and are
 * addressed by ordinal, which `StageMonitorSettingsTabStructureTest` pins.
 */
class StageMonitorSettingsTabZoneStyleTest {

    private val zone = StageMonitorStyleZone.TOP_LEFT
    private val ordinal = ZoneOrdinal.of(zone)

    /** Asserts every zone but [zone] still holds the style it started with. */
    private fun assertOtherZonesUntouched(
        get: () -> AppSettings,
    ) {
        val defaults = StageMonitorSettings.defaultZoneStyles()
        for (other in StageMonitorStyleZone.entries.filter { it != zone }) {
            assertEquals(defaults.getValue(other), get().styleOf(other), "$other must be untouched")
        }
    }

    // ── Font ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the zone font dropdown stores the picked family`() {
        val target = uniquelyNamedFont()
        stageMonitorTab(initial = zoneStyled(zone) { copy(fontType = SENTINEL_FONT) }) { get ->
            pickFont(showing = SENTINEL_FONT, to = target)
            assertEquals(target, get().styleOf(zone).fontType, "the picked family must be stored")
            assertOtherZonesUntouched(get)
        }
    }

    @Test
    fun `the zone font size stores a new value`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(fontSize = 123) }) { get ->
            retypeNumberField(showing = 123, to = 210)
            assertEquals(210, get().styleOf(zone).fontSize, "the typed size must be stored")
            assertOtherZonesUntouched(get)
        }
    }

    /** The field accepts 8..300; the callback is withheld outside that, so nothing is stored. */
    @Test
    fun `a zone font size outside the range is not stored`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(fontSize = 123) }) { get ->
            retypeNumberField(showing = 123, to = 400)
            assertEquals(123, get().styleOf(zone).fontSize, "400 is above the 8..300 range")

            // The accepted value proves the field was live for the rejected one too.
            retypeNumberField(showing = 400, to = 300)
            assertEquals(300, get().styleOf(zone).fontSize, "300 is the top of the range and is accepted")
        }
    }

    /**
     * The font dropdown has three separate affordances and [pickFont] uses none of them: it types a
     * filter and commits on the IME action. These are the two a mouse user actually reaches for —
     * the expand arrow and the field itself — and the menu item they open writes the setting by its
     * own `onClick`, a different path from the keyboard commit.
     *
     * Every zone is parked on a sentinel font so the family under test appears nowhere on the tab;
     * the zone under test gets its *lowercased* spelling, which the case-insensitive filter still
     * matches, so the menu offers a value the field does not already hold.
     */
    private fun fontMenuFixture(installed: String) = stageSettings {
        copy(
            zoneStyles = StageMonitorStyleZone.entries.associateWith { styleFor(it).copy(fontType = SENTINEL_FONT) } +
                (zone to styleFor(zone).copy(fontType = installed.lowercase())),
        )
    }

    @Test
    fun `the font dropdown arrow opens the menu and a picked font is stored`() {
        val installed = mixedCaseInstalledFont()
        stageMonitorTab(initial = fontMenuFixture(installed)) { get ->
            onAllNodesWithText(installed).assertCountEquals(0)

            fontDropdownArrow(ordinal).performScrollTo().performClick()
            waitForIdle()
            onAllNodesWithText(installed).assertCountEquals(1)

            onAllNodesWithText(installed)[0].performClick()
            waitForIdle()
            assertEquals(installed, get().styleOf(zone).fontType, "picking from the menu must be stored")
            assertFontFieldShows(installed, "the zone's font dropdown")
            // Compared against the fixture, not the defaults: this fixture parks every zone on the
            // sentinel on purpose, so "untouched" here means still holding it.
            for (other in StageMonitorStyleZone.entries.filter { it != zone }) {
                assertEquals(SENTINEL_FONT, get().styleOf(other).fontType, "$other's font must be untouched")
            }
        }
    }

    /** The field itself is clickable too, and opens the same menu. */
    @Test
    fun `clicking the font field opens the menu`() {
        val installed = mixedCaseInstalledFont()
        stageMonitorTab(initial = fontMenuFixture(installed)) { _ ->
            onAllNodesWithText(installed).assertCountEquals(0)

            onAllNodes(hasClickAction() and hasText("FONT TYPE"))[ordinal].performScrollTo().performClick()
            waitForIdle()

            onAllNodesWithText(installed).assertCountEquals(1)
        }
    }

    // ── Colours ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the zone text colour stores the confirmed hex`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(color = "#123456") }) { get ->
            recolor(fromHex = "#123456", toHex = "#ABCDEF")
            assertTrue(
                get().styleOf(zone).color.equals("#ABCDEF", ignoreCase = true),
                "the confirmed hex must become the zone's text colour",
            )
            assertColorFieldShows("#ABCDEF", "the zone's text colour field")
            assertEquals("#000000", get().styleOf(zone).bgColor, "the background colour must be untouched")
            assertOtherZonesUntouched(get)
        }
    }

    @Test
    fun `the zone background colour stores the confirmed hex`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(bgColor = "#654321") }) { get ->
            recolor(fromHex = "#654321", toHex = "#FEDCBA")
            assertTrue(
                get().styleOf(zone).bgColor.equals("#FEDCBA", ignoreCase = true),
                "the confirmed hex must become the zone's background colour",
            )
            assertEquals("#FFFFFF", get().styleOf(zone).color, "the text colour must be untouched")
            assertOtherZonesUntouched(get)
        }
    }

    @Test
    fun `the zone shadow colour stores the confirmed hex`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(shadowColor = "#0F0F0F") }) { get ->
            recolor(fromHex = "#0F0F0F", toHex = "#F0F0F0")
            assertTrue(
                get().styleOf(zone).shadowColor.equals("#F0F0F0", ignoreCase = true),
                "the confirmed hex must become the zone's shadow colour",
            )
            assertEquals("#FFFFFF", get().styleOf(zone).color, "the text colour must be untouched")
            assertOtherZonesUntouched(get)
        }
    }

    // ── Shadow size and intensity ───────────────────────────────────────────────────────────────

    @Test
    fun `the zone shadow size stores a new percentage`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(shadowSize = 137) }) { get ->
            retypeNumberField(showing = 137, to = 250)
            assertEquals(250, get().styleOf(zone).shadowSize, "the typed size must be stored")
            assertEquals(80, get().styleOf(zone).shadowOpacity, "the intensity must be untouched")
            assertOtherZonesUntouched(get)
        }
    }

    @Test
    fun `the zone shadow intensity stores a new percentage`() {
        stageMonitorTab(initial = zoneStyled(zone) { copy(shadowOpacity = 63) }) { get ->
            retypeNumberField(showing = 63, to = 35)
            assertEquals(35, get().styleOf(zone).shadowOpacity, "the typed intensity must be stored")
            assertEquals(100, get().styleOf(zone).shadowSize, "the size must be untouched")
            assertOtherZonesUntouched(get)
        }
    }

    // ── Bold / Italic / Underline / Shadow ──────────────────────────────────────────────────────

    /** All four are wired here, unlike the Dictionary tab where five of eight are decoration. */
    @Test
    fun `the bold button toggles the zone's bold flag`() = stageMonitorTab { get ->
        assertEquals(false, get().styleOf(zone).bold, "not bold out of the box")
        styleButton(ordinal, "B").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().styleOf(zone).bold, "clicking B must be stored")
        assertOtherZonesUntouched(get)

        styleButton(ordinal, "B").performClick()
        waitForIdle()
        assertEquals(false, get().styleOf(zone).bold, "clicking B again must clear it")
    }

    @Test
    fun `the italic button toggles the zone's italic flag`() = stageMonitorTab { get ->
        styleButton(ordinal, "I").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().styleOf(zone).italic, "clicking I must be stored")
        assertEquals(false, get().styleOf(zone).bold, "and must not also set bold")
        assertOtherZonesUntouched(get)
    }

    @Test
    fun `the underline button toggles the zone's underline flag`() = stageMonitorTab { get ->
        styleButton(ordinal, "U").performScrollTo().performClick()
        waitForIdle()
        assertEquals(true, get().styleOf(zone).underline, "clicking U must be stored")
        assertOtherZonesUntouched(get)
    }

    @Test
    fun `the shadow button toggles the zone's shadow flag`() = stageMonitorTab { get ->
        // Top-Left is the one zone whose default has shadow on, so this clears it first.
        assertEquals(true, get().styleOf(zone).shadow, "Top-Left ships with shadow on")
        styleButton(ordinal, "S").performScrollTo().performClick()
        waitForIdle()
        assertEquals(false, get().styleOf(zone).shadow, "clicking S must clear it")
        assertOtherZonesUntouched(get)

        styleButton(ordinal, "S").performClick()
        waitForIdle()
        assertEquals(true, get().styleOf(zone).shadow, "and clicking again must set it")
    }

    // ── Alignment ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each vertical alignment button stores its own value`() {
        val cases = listOf(
            VAlign.MIDDLE to Constants.MIDDLE,
            VAlign.BOTTOM to Constants.BOTTOM,
            VAlign.TOP to Constants.TOP,
        )
        for ((which, expected) in cases) {
            stageMonitorTab(initial = zoneStyled(zone) { copy(verticalAlignment = Constants.BOTTOM) }) { get ->
                verticalAlignButton(ordinal, which).performScrollTo().performClick()
                waitForIdle()
                assertEquals(expected, get().styleOf(zone).verticalAlignment, "the $expected button must store it")
                assertEquals(
                    Constants.LEFT,
                    get().styleOf(zone).horizontalAlignment,
                    "the horizontal alignment must be untouched",
                )
            }
        }
    }

    /**
     * The horizontal row is laid out **right-first**, so `HAlign.RIGHT` is the leftmost ordinal —
     * the Song tab's finding, which holds here because both use the same shared composable.
     */
    @Test
    fun `each horizontal alignment button stores its own value`() {
        val cases = listOf(
            HAlign.RIGHT to Constants.RIGHT,
            HAlign.CENTER to Constants.CENTER,
            HAlign.LEFT to Constants.LEFT,
        )
        for ((which, expected) in cases) {
            stageMonitorTab(initial = zoneStyled(zone) { copy(horizontalAlignment = Constants.CENTER) }) { get ->
                horizontalAlignButton(ordinal, which).performScrollTo().performClick()
                waitForIdle()
                assertEquals(expected, get().styleOf(zone).horizontalAlignment, "the $expected button must store it")
                assertEquals(
                    Constants.TOP,
                    get().styleOf(zone).verticalAlignment,
                    "the vertical alignment must be untouched",
                )
            }
        }
    }
}
