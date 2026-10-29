@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The live preview panel arranged by hand: the gear's display switches, a layout from a template, a
 * second one and switching between them, the panel turned into its own editor, and locking a screen.
 */
class PreviewLayoutDemoTour {

    @Test
    fun previewLayout() = recordTour(
        "preview-layout",
        tab = "Preview Panel",
        liveLabel = "Every screen at a glance",
        steps = 9,
        heading = "Designing the " to "",
        // The Bible, Songs and Pictures tabs raise a "Keyboard focus lost" banner whenever a menu takes
        // focus; Media has none, and the panel is what this tour is about.
        startTab = START_TAB,
        settings = ::fourScreens,
    ) { previewTour() }

    private fun DemoTour.previewTour() {
        say("intro")
        intro("Arrange the live previews the way the booth wants them.")
        say("why")
        note("The preview panel", "Every output, live", panel())
        segment("menu")
        menu()
        segment("layouts")
        layouts()
        segment("edit")
        edit()
        segment("options")
        options()
        segment("lock")
        say("lock")
        step("Lock a screen", "Marked Locked until you unlock it", find(hasContentDescription(LOCK)))
        pause(0.6)
        segment("end")
        say("outro")
        outro()
    }

    /** The gear: what each preview shows, no layout yet, and two by two from the templates. */
    private fun DemoTour.menu() {
        say("gear")
        step("Preview Settings", "At the top of the panel", find(control(PREVIEW_SETTINGS)))
        say("display")
        note("Display", "Names and display types", popup(hasText("Show output labels")))
        say("none")
        note("No layout yet", "Every output listed", popup(hasText("No layout yet", substring = true)))
        say("templates")
        note("New layout", "Pick a template", popup(hasText("New layout", substring = true)))
        say("two-by-two")
        step("Two by two", "Outputs fill it in order", popup(hasTestTag(template(TWO_BY_TWO))))
        closeMenu()
    }

    /** The grid, a second layout from Tall + 3, and switching back. */
    private fun DemoTour.layouts() {
        say("grid")
        note("A two-by-two grid", "Two across, two down", panel())
        say("second")
        step("Preview Settings", "Open the menu again", find(control(PREVIEW_SETTINGS)))
        say("tall")
        step("Tall + 3", "One tall, three stacked", popup(hasTestTag(template(TALL_PLUS_THREE))))
        closeMenu()
        say("switch")
        quietClick(find(control(PREVIEW_SETTINGS)))
        pause(0.3)
        note("Your layouts", "Click one to switch, the pencil renames", popup(hasText("Layout 1")))
    }

    /** Edit layout: the panel as its own editor — what an area shows, where, splitting, and a divider. */
    private fun DemoTour.edit() {
        say("edit")
        step("Edit layout", "The panel becomes the editor", popup(hasText("Edit layout")))
        say("picker")
        note("What it shows", "Pick the output", find(hasTestTag("preview_area_output_0")))
        say("place")
        note("Top, Middle, Bottom", "Where it sits in its area", find(hasText("Middle")))
        say("split")
        note("Split or remove", "Across, down, or take it away", find(hasText("Split across")))
        say("divider")
        val divider = find(hasTestTag("preview_divider__0"))
        step("Drag a divider", "One area bigger, another smaller", divider) {
            divider.node.performTouchInput { swipe(center, center + Offset(dp(DIVIDER_DRAG_DP), 0f), DRAG_MS) }
        }
        say("done")
        step("Done", "Back to the live panel", find(hasText("Done")) { it.left > dp(PANEL_LEFT_DP) })
    }

    /** Fill the panel, and outputs not in the layout. */
    private fun DemoTour.options() {
        quietClick(find(control(PREVIEW_SETTINGS)))
        pause(0.3)
        say("fill")
        step("Fill the panel", "All its height", popup(hasText("Fill the panel")))
        say("unplaced")
        note("Outputs not in the layout", "Listed below it", popup(hasText("List outputs not in the layout")))
        closeMenu()
    }

    private fun DemoTour.popup(matcher: SemanticsMatcher): Target = popupItem(matcher)

    /** The preview panel down the right of the window, as far as its tiles go. */
    private fun DemoTour.panel(): Target {
        val tiles = findAll(hasContentDescription(LOCK))
        val first = tiles.first()
        val box = tiles.map { it.bounds }.reduce { a, b ->
            Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }
        val pad = dp(PANEL_PAD_DP)
        val bounds = Rect(box.left - dp(TILE_W_DP), box.top - dp(TILE_H_DP), box.right + pad, box.bottom + pad)
        return Target(first.node, bounds)
    }

    /**
     * Closes an open drop-down with a click outside it, on the tab already open: the click only
     * dismisses, so the next one reaches what it is aimed at.
     */
    private fun DemoTour.closeMenu() {
        runCatching { quietClick(tab(START_TAB)) }
        giveKeyboardBack()
        pause(0.3)
    }

    private fun control(label: String) = hasContentDescription(label) or hasText(label)

    private fun template(index: Int) = "preview_template_$index"
}

/** Four screens, each its own profile: the sanctuary, the overflow room, the livestream, the stage. */
private fun fourScreens(s: AppSettings): AppSettings {
    val profiles = listOf(
        OutputProfile(id = "sanctuary", name = "Sanctuary"),
        OutputProfile(id = "overflow", name = "Overflow room"),
        OutputProfile(
            id = "livestream",
            name = "Livestream",
            displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        ),
        OutputProfile(id = "stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
    )
    return s.copy(
        projectionSettings = s.projectionSettings.copy(
            devWindowCount = profiles.size,
            outputProfiles = profiles,
            screenAssignments = profiles.map { ScreenAssignment(activeProfileId = it.id) },
            previewLayouts = emptyList(),
            activePreviewLayout = "",
            previewLayoutFillsPanel = false,
        ),
    )
}

private const val START_TAB = "Media"
private const val PREVIEW_SETTINGS = "Preview Settings"
private const val LOCK = "Lock screen to current tab"
private const val TWO_BY_TWO = 1
private const val TALL_PLUS_THREE = 5
private const val DIVIDER_DRAG_DP = 40f
private const val DRAG_MS = 400L
private const val PANEL_LEFT_DP = 1200f
private const val PANEL_PAD_DP = 6f
private const val TILE_W_DP = 140f
private const val TILE_H_DP = 80f
