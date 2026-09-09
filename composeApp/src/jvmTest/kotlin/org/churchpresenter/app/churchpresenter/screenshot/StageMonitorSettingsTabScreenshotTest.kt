@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.app.churchpresenter.dialogs.tabs.StageMonitorSettingsTab
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.withZoneWidth
import org.churchpresenter.settings.withZoneHeight

/**
 * The Stage Monitor tab of the settings dialog, in both themes.
 *
 * Two columns in **one** scroll container, so both move together and most of the tab is below the
 * fold: content routing and the Full Screen, Top-Left and Top-Right style blocks down the left, the
 * layout preview and the three bottom style blocks down the right. The scroll positions below walk
 * that the way an operator does.
 *
 * The axes worth an image of their own:
 *
 *  - **Where each of the fifteen content types is routed.** Every one carries a zone dropdown, and
 *    Bible, Songs and Next are offered one option fewer — they are meant to share the screen, so
 *    Full Screen is not on their list. The layout preview redraws from those choices.
 *  - **Six style blocks, one per drawable zone**, identical in shape but not in what they offer:
 *    only the two top zones can hold a song's chart, so only those ask for a chord color — and
 *    that renames the text color to "Lyrics Color", because once chords are on screen "Color" no
 *    longer says which.
 *  - **Four color pickers per block** — text, chord, background and shadow. Each is shot from a
 *    fixture color of its own, so the picker opens on a different hue in each image and it is
 *    visible which field it came from.
 *
 * The font dropdowns are never opened: their list is whatever `GraphicsEnvironment` reports on the
 * recording machine, so an image of one would belong to whoever recorded it.
 */
class StageMonitorSettingsTabScreenshotTest {

    /** The picker's "Recent" row is JVM-wide state — see [PinnedRecentColors]. */
    private val recents = PinnedRecentColors()

    @BeforeTest
    fun pinRecentColors() = recents.clear()

    @AfterTest
    fun unpinRecentColors() = recents.restore()

    // ── As it opens ─────────────────────────────────────────────────────────────────────────────

    /** Content routing on the left, the layout preview it feeds on the right. */
    @Test
    fun `as it opens`() = shoot("top")

    /**
     * Content spread across all five zones instead of piled into Full Screen.
     *
     * The preview cells fill up, and the Full Screen and None rows under it list what is routed
     * there — the defaults leave None empty, so this is the only image where that row says anything.
     */
    @Test
    fun `content routed across the zones`() = shoot("zones_routed", settings = routed())

    /**
     * The grid dragged away from the proportions the layout catalog gives it.
     *
     * The cells say their own size, so this is also the image that shows a row's widths still adding
     * to 100 after two of them have been traded, and it is the only one where Reset is offered —
     * the header button is disabled until something has been resized.
     */
    @Test
    fun `zones resized away from the layout`() = shoot("zones_resized", settings = resized())

    /**
     * A zone other than the first selected, which is what the width and row-height fields follow.
     *
     * Clicked rather than seeded: selection is composition state, not a setting, so there is no
     * fixture for it — and the click is also what an operator does to point the fields somewhere.
     */
    @Test
    fun `another zone selected in the grid`() = shoot("zone_selected", settings = resized()) {
        onAllNodesWithText(BOTTOM_CENTRE_CELL)[0].performScrollTo().performClick()
        waitForIdle()
    }

    // ── The shape of the output the zones are drawn on ──────────────────────────────────────────
    // The mock is the shape of the real stage monitor, not a fixed 16:9, so the same layout gives
    // very different cells on a 4:3, a portrait confidence display and an ultrawide — and the cell
    // labels have to fit all three. Churches run all three.

    /** An old 4:3 confidence monitor: the cells get taller and the bottom row squarer. */
    @Test
    fun `the zones on a four-three output`() = shoot("zones_output_four_three", settings = output(1024, 768))

    /**
     * A rotated display, where the mock is capped by [STAGE_PREVIEW_MAX_HEIGHT] rather than sized
     * by it — the one shape where the cap is what decides how big the picture is.
     */
    @Test
    fun `the zones on a portrait output`() = shoot("zones_output_portrait", settings = output(1080, 1920))

    /** An ultrawide, where the cells go long and the three-line label has the least height. */
    @Test
    fun `the zones on an ultrawide output`() = shoot("zones_output_ultrawide", settings = output(2560, 1080))

    // ── The dropdowns ───────────────────────────────────────────────────────────────────────────

    /** Every zone a content type can go to, including Full Screen and None. */
    @Test
    fun `a zone menu`() = shoot("zone_menu", rootIndex = 1) {
        openZoneMenuFor(PRESENTATION)
    }

    /**
     * Bible's menu, which is one shorter.
     *
     * Bible, Songs and Next are never allowed to take the whole screen, so Full Screen is absent
     * from their lists and only from theirs.
     */
    @Test
    fun `a zone menu without Full Screen`() = shoot("zone_menu_no_full_screen", rootIndex = 1) {
        openZoneMenuFor(BIBLE)
    }

    /** Where the metronome dot can be anchored — a free 3×3 grid, plus off. */
    @Test
    fun `the metronome position menu`() = shoot("metronome_menu", rootIndex = 1) {
        onAllNodesWithText(METRONOME)[0].performScrollTo().performClick()
        waitForIdle()
    }

    /**
     * The metronome anchored bottom-right, which puts a flashing dot in the preview.
     *
     * The dot is driven by a loop that never ends, so the clock is stopped before the tab is
     * composed and stepped by hand to a moment inside the flash — otherwise letting it run would
     * spin until the test timed out, and whichever frame the capture happened to land on would
     * differ from one recording to the next.
     */
    @Test
    fun `the metronome dot in the preview`() = shoot(
        "metronome_on",
        settings = stageMonitor { copy(metronomePosition = MetronomePosition.BOTTOM_RIGHT) },
        freezeClock = true,
    )

    // ── The style blocks, below the fold ────────────────────────────────────────────────────────
    // Scrolled by the "Shadow" row each block carries — one per block, in composition order, and the
    // only handle that is not also a zone name used by a dropdown value somewhere else on the tab.
    //
    // Not shot: the Full Screen and Top-Left blocks on their own. Both are already drawn in the
    // first viewport, so scrolling to either lands where the tab already was and produces `top`.

    /** Top-Right — and above it Top-Left, the other block that asks for a chord color. */
    @Test
    fun `the chord-carrying style blocks`() = shoot("style_top_right") { scrollToBlock(TOP_RIGHT_BLOCK) }

    /** The foot of the tab — the last of the bottom blocks. */
    @Test
    fun `the last style block`() = shoot("style_bottom_right") { scrollToBlock(BOTTOM_RIGHT_BLOCK) }

    /**
     * A zone styled away from the defaults.
     *
     * Bold, italic, underline and shadow all on and both alignments moved, so every toggle in the
     * row is lit — the defaults leave most of them dark, and an unlit toggle and a lit one are only
     * a shade apart.
     */
    @Test
    fun `a zone styled away from the defaults`() = shoot("style_customised", settings = customized())

    // ── The color pickers ──────────────────────────────────────────────────────────────────────
    // One image per field rather than one for the picker, because the field a picker opens from is
    // the only thing that tells them apart — the popup itself is the same hue strip, saturation
    // square and hex box every time, opened on whatever color that field holds.

    @Test
    fun `the text color picker`() = picker("picker_text_colour", TEXT_COLOUR)

    @Test
    fun `the chord color picker`() = picker("picker_chord_colour", CHORD_COLOUR, block = TOP_LEFT_BLOCK)

    @Test
    fun `the background color picker`() = picker("picker_background_colour", BACKGROUND_COLOUR)

    @Test
    fun `the shadow color picker`() = picker("picker_shadow_colour", SHADOW_COLOUR)

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** Scrolls the nth style block into view by the "Shadow" row it carries. */
    private fun ComposeUiTest.scrollToBlock(index: Int) {
        onAllNodesWithText(SHADOW)[index].performScrollTo()
        waitForIdle()
    }

    /** Opens the zone dropdown belonging to the content row labeled [label]. */
    private fun ComposeUiTest.openZoneMenuFor(label: String) {
        onAllNodesWithText(label)[0].performScrollTo().performClick()
        waitForIdle()
    }

    /**
     * Opens the picker on the field holding [hex], with the tab styled so that color is unique.
     *
     * Unique because a picker is addressed by the color its field displays, and the six style
     * blocks otherwise carry the same handful of colors several times over.
     */
    private fun picker(name: String, hex: String, block: Int = FULL_SCREEN_BLOCK) = shoot(
        name,
        settings = distinctColours(),
        rootIndex = 1,
    ) {
        scrollToBlock(block)
        onAllNodesWithText(hex)[0].performClick()
        waitForIdle()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        rootIndex: Int = 0,
        freezeClock: Boolean = false,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            if (freezeClock) mainClock.autoAdvance = false
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var current by remember { mutableStateOf(settings) }
                            StageMonitorSettingsTab(
                                settings = current,
                                onSettingsChange = { transform -> current = transform(current) },
                            )
                        }
                    }
                }
            }
            if (freezeClock) {
                // Inside the flash: the dot is lit for the first 150ms of every beat.
                mainClock.advanceTimeBy(64)
                mainClock.advanceTimeByFrame()
            } else {
                waitForIdle()
            }
            drive()
            if (!freezeClock) waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private fun stageMonitor(edit: StageMonitorSettings.() -> StageMonitorSettings) =
        AppSettings(stageMonitorSettings = StageMonitorSettings().edit())

    /** The tab as it opens, with the stage monitor going out on a [width] by [height] screen. */
    private fun output(width: Int, height: Int) = AppSettings(
        projectionSettings = ProjectionSettings(
            screenAssignments = listOf(
                ScreenAssignment(
                    displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
                    targetBoundsW = width,
                    targetBoundsH = height,
                ),
            ),
        ),
    )

    /**
     * A grid nobody would get from the catalog: a top row taking four fifths of the screen, split
     * three ways to one, and a bottom row whose first cell has taken half of it.
     *
     * Deliberately lopsided in both directions at once — the same numbers on every row would not
     * show that height belongs to the row while width belongs to the cell.
     */
    private fun resized() = stageMonitor {
        withZoneHeight(StageMonitorStyleZone.A, 80f)
            .withZoneWidth(StageMonitorStyleZone.A, 75f)
            .withZoneWidth(StageMonitorStyleZone.C, 50f)
    }

    /**
     * Every zone but Full Screen in use, and nothing left on Full Screen at all.
     *
     * The defaults route all but five content types to Full Screen, so the four preview cells sit
     * near-empty and the None row says nothing. Here each of the fifteen types is on one of the five
     * drawn zones or switched off — so every option a zone dropdown offers except Full Screen is
     * chosen by something, all four cells fill up, the None row lists what is off, and the Full
     * Screen row is the one reading "—".
     */
    private fun routed() = stageMonitor {
        copy(
            contentZones = mapOf(
                StageMonitorContentType.BIBLE to StageMonitorZone.A,
                StageMonitorContentType.SONGS to StageMonitorZone.A,
                StageMonitorContentType.PRESENTATION to StageMonitorZone.A,
                StageMonitorContentType.NEXT to StageMonitorZone.B,
                StageMonitorContentType.PRESENTATION_NOTES to StageMonitorZone.B,
                StageMonitorContentType.ANNOUNCEMENT_TEXT to StageMonitorZone.C,
                StageMonitorContentType.STT to StageMonitorZone.C,
                StageMonitorContentType.QA to StageMonitorZone.C,
                StageMonitorContentType.CLOCK to StageMonitorZone.D,
                StageMonitorContentType.DICTIONARY to StageMonitorZone.D,
                StageMonitorContentType.PICTURES to StageMonitorZone.E,
                StageMonitorContentType.MEDIA to StageMonitorZone.E,
                StageMonitorContentType.CANVAS to StageMonitorZone.E,
                StageMonitorContentType.LOWER_THIRD to StageMonitorZone.NONE,
                StageMonitorContentType.WEB to StageMonitorZone.NONE,
            ),
        )
    }

    /** The Full Screen block with every text style and both alignments off their defaults. */
    private fun customized() = stageMonitor {
        copy(
            zoneStyles = zoneStyles + (
                StageMonitorStyleZone.FULL_SCREEN to styleFor(StageMonitorStyleZone.FULL_SCREEN).copy(
                    fontSize = 140,
                    color = "#FFD54F",
                    bgColor = "#123A6B",
                    bold = true,
                    italic = true,
                    underline = true,
                    shadow = true,
                    shadowSize = 160,
                    shadowOpacity = 55,
                    verticalAlignment = Constants.BOTTOM,
                    horizontalAlignment = Constants.RIGHT,
                )
                ),
        )
    }

    /**
     * Every color a picker image opens from, made unique across the whole tab.
     *
     * The four fields on a block otherwise show `#FFFFFF` and `#000000` twice over, and the other
     * five blocks show the same again — so the field a picker was opened from could not be told
     * from the image, and two of the four images would have been the same picture.
     */
    private fun distinctColours() = stageMonitor {
        copy(
            zoneStyles = zoneStyles + mapOf(
                StageMonitorStyleZone.FULL_SCREEN to styleFor(StageMonitorStyleZone.FULL_SCREEN).copy(
                    color = TEXT_COLOUR,
                    bgColor = BACKGROUND_COLOUR,
                    shadowColor = SHADOW_COLOUR,
                    shadow = true,
                ),
                StageMonitorStyleZone.A to styleFor(StageMonitorStyleZone.A).copy(
                    chordColor = CHORD_COLOUR,
                ),
            ),
        )
    }

    private companion object {
        const val SECTION = "stageMonitorSettingsTab"

        // Content rows, as the tab labels them — a dropdown field draws its label upper-cased.
        const val BIBLE = "BIBLE"
        const val PRESENTATION = "PRESENTATION"
        const val METRONOME = "METRONOME POSITION"

        /** One per style block — the handle the scroll positions use. */
        const val SHADOW = "Shadow"

        /**
         * The Bottom-Center cell of the grid, addressed by what is routed there.
         *
         * Its zone name would also match the Clock row's dropdown, which is set to that zone; the
         * title-cased content name is drawn in the cell and nowhere else (the dropdown captions the
         * row "CLOCK").
         */
        const val BOTTOM_CENTRE_CELL = "Clock"

        // Style blocks in composition order: the left column's three, then the right column's.
        const val FULL_SCREEN_BLOCK = 0
        const val TOP_LEFT_BLOCK = 1
        const val TOP_RIGHT_BLOCK = 2
        const val BOTTOM_RIGHT_BLOCK = 5

        // Fixture colors, each unique on the tab so its picker can be addressed by it.
        const val TEXT_COLOUR = "#FFD54F"
        const val CHORD_COLOUR = "#7BE38F"
        const val BACKGROUND_COLOUR = "#123A6B"
        const val SHADOW_COLOUR = "#8B1E3F"
    }
}
