@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.ProfilesSettingsTab
import java.io.File
import kotlin.test.Test

/** Settings → Profiles: a lower third, and how Bible verses, lyrics and subtitles look on each screen. */
class StylingDemoTour {

    @Test
    fun styling() = recordTour(
        "styling",
        tab = "Screens",
        liveLabel = "Style your screens",
        steps = 12,
        heading = "Styling Your " to "",
        settings = ::threeScreens,
        panel = { settings, change ->
            Box(Modifier.size(PROFILES_W.dp, PROFILES_H.dp)) { ProfilesSettingsTab(settings, change) }
        },
    ) { stylingTour() }

    private fun DemoTour.stylingTour() {
        say("intro")
        intro("Lower thirds, fonts, colors, spacing and transitions.")
        say("open")
        step("Settings", "Then Profiles", find(hasContentDescription("Settings", substring = true)), numbered = true) {}
        openPanel("Settings — Profiles", PROFILES_W, PROFILES_H)
        menusInPlace = true
        say("profiles")
        panelNote("Profiles", "One look per screen", panelItem(hasText("PROFILES")))
        lowerThird()
        say("preview")
        panelNote("Live preview", "Short, medium or long text", panelItem(hasText("Long")), click = true)
        say("basic-advanced")
        panelNote("Basic or Advanced", "Every option, or the common ones", panelItem(hasText("Advanced")), click = true)
        quietPanelClick(hasText("Open all"))
        bible()
        say("songs-page")
        panelNote("Songs", "The same settings, and more", rail("Songs"), click = true, numbered = true)
        quietPanelClick(hasText("Open all"))
        say("song-options")
        point("Just for songs", "Section label, title slide, chorus", hasText("Section label"))
        say("subtitles-page")
        panelNote("Subtitles", "Text, lines, position, background", rail("Subtitles"), click = true, numbered = true)
        say("defaults")
        point("Different from defaults", "Revert any change", hasText("Different from defaults"))
        say("each-screen")
        panelNote("Each profile its own look", "Projector one way, lower third another", panelItem(hasText("PROFILES")))
        closePanel()
        say("outro")
        outro()
    }

    /** The second screen's profile, switched to a band across the bottom. */
    private fun DemoTour.lowerThird() {
        say("lower-third-mode")
        quietPanelClick(hasText("Lower Third"))
        val mode = seek(hasText("Lower third"))
        panelNote("Display mode: Lower third", "A band across the bottom", mode, click = true, numbered = true)
        say("outputs")
        panelNote("Outputs", "Which screen uses it", rail("Outputs"), click = true)
        say("over-content")
        quietPanelClick(hasText("Content & sources"))
        panelNote("Over the content", "Or in its place", seek(hasText("Lower third goes over the content")))
        quietPanelClick(hasText("Default"))
    }

    private fun DemoTour.bible() {
        say("bible-page")
        panelNote("Bible", "How verses look", rail("Bible"), click = true, numbered = true)
        quietPanelClick(hasText("Open all"))
        say("font")
        point("Font and size", "Any font on this computer", hasText("Font"))
        say("style")
        point("Bold, italic, underline, outline", "Stands out on any background", hasText("Style"))
        say("color")
        point("Color and shadow", "The words and what's behind them", hasText("Color"))
        say("alignment")
        panelNote("Alignment", "Left, center or right", seek(hasText("Center")), click = true)
        say("letter-case")
        panelNote("Letter case", "As typed, capitals, lowercase", seek(hasText("UPPERCASE")), click = true)
        say("spacing")
        point("Letter and word spacing", "Room between letters and words", hasText("Letter spacing"))
        say("reference")
        // The reference's own settings show once it is the element being edited.
        quietPanelClick(hasText("Reference"))
        point("The reference", "Above the verse or after it", hasText("After verse"))
        say("place")
        val placement = hasText("Place on screen") or hasText("Position") or hasText("Vertical alignment")
        point("Place on screen", "Where the text sits", placement)
        say("margins")
        point("Margins", "Away from the edges", hasText("Margins"))
        say("box")
        point(
            "Text box and auto-fit",
            "Long verses always fit",
            hasText("Auto-fit") or hasText("Text box", substring = true),
        )
        say("move")
        point("Move X / Y", "Nudge it into place", hasText("Move X / Y"))
        say("translations")
        point("Translations", "Stacked or side by side", hasText("Divider between translations"))
        say("background")
        point("Background", "Color, gradient, picture or video", hasText("BACKGROUND"))
        say("transition")
        point("Transition", "Fade in, fade out, crossfade", hasText("Crossfade between items"))
    }

    /** Points at [m] in the settings column, or, if this page has no such control, says the line over a caption. */
    private fun DemoTour.point(heading: String, sub: String, m: SemanticsMatcher) {
        val target = runCatching { seek(m) }.getOrNull()
        if (target != null) {
            panelNote(heading, sub, target)
        } else {
            File(System.getenv("CP_DEMO_OUT"), "styling-missed.txt").appendText("$heading\n")
            caption(heading, sub)
        }
    }

    /** An entry of the page list down the left of the settings. */
    private fun DemoTour.rail(label: String) = panelItems(hasText(label)).minBy { it.bounds.left }

    private fun DemoTour.quietPanelClick(m: SemanticsMatcher) {
        panelItems(m).firstOrNull()?.node?.performClick()
        ui.waitForIdle()
    }

    /**
     * [m] in the settings column, scrolled into view: down the page first, then back up for what sat
     * above where it was left.
     */
    private fun DemoTour.seek(m: SemanticsMatcher): Target {
        fun found() = panelItems(m).filter { it.bounds.width > 0f }
        for (direction in listOf(1f, -1f)) {
            repeat(SEEK_STEPS) {
                found().firstOrNull()?.let { return panelReveal(m) }
                panelItems(hasScrollAction()).forEach {
                    it.node.performSemanticsAction(SemanticsActions.ScrollBy) { by -> by(0f, direction * dp(SEEK_PX)) }
                }
                ui.waitForIdle()
            }
        }
        return panelReveal(m)
    }
}

// The real Settings window opens at this size (OptionsDialog).
private const val PROFILES_W = 1400f
private const val PROFILES_H = 900f
private const val SEEK_STEPS = 40
private const val SEEK_PX = 220f
