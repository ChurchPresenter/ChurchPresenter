@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test

/**
 * Pins the shape of the tab: how many of each repeated widget it renders, and that everything a
 * user can see is actually on screen.
 *
 * The behaviour tests in the sibling classes address the widget groups that publish no tag and no
 * text of their own — the horizontal-alignment icons, the B/I/U/S buttons, the segmented rows — by
 * their ordinal in composition order. That only stays honest while the counts hold, so this class
 * asserts them directly: add or move one of those controls and this test names the problem instead
 * of leaving a behaviour test to fail against the wrong widget.
 */
@OptIn(ExperimentalTestApi::class)
class SongSettingsTabStructureTest {

    @Test
    fun `the tab renders every section header`() = songTab { _ ->
        val sections = listOf(
            "Song Title Slide",
            "Song Number",
            "Title",
            "Transition",
            "Text Margins",
            "Lyrics",
            "Fullscreen Display",
            "Lower Third Display",
            "Look Ahead (Fullscreen)",
            "Look Ahead Next Section (Fullscreen)",
            "Look Ahead (Lower Third)",
            "Look Ahead Next Section (Lower Third)",
        )
        for (section in sections) {
            onAllNodesWithText(section).assertCountEquals(1)
        }
    }

    @Test
    fun `the tab renders one stepper field per numeric setting`() = songTab { _ ->
        // Song number (2), title (2), end-of-song spacing (1), margins (4), lyrics (2), look ahead (4).
        numberFields().assertCountEquals(15)
    }

    @Test
    fun `the tab renders one font dropdown per styled text block`() = songTab { _ ->
        fontFields().assertCountEquals(8)
    }

    /** One per styled text block, plus the chord colour the two lyric blocks each carry. */
    @Test
    fun `the tab renders one colour field per styled text block`() = songTab { _ ->
        colorFields().assertCountEquals(10)
    }

    @Test
    fun `the tab renders one style button group per styled text block`() = songTab { _ ->
        for (label in listOf("B", "I", "U", "S")) {
            onAllNodes(hasClickAction() and hasText(label)).assertCountEquals(StyleGroup.COUNT)
        }
    }

    @Test
    fun `the tab renders one horizontal alignment group per alignable block`() = songTab { _ ->
        horizontalAlignButtons().assertCountEquals(HAlignGroup.COUNT * 3)
    }

    @Test
    fun `the tab renders one above-below pair per positionable block`() = songTab { _ ->
        onAllNodesWithContentDescription("Above").assertCountEquals(PositionGroup.COUNT)
        onAllNodesWithContentDescription("Below").assertCountEquals(PositionGroup.COUNT)
    }

    @Test
    fun `the tab renders one vertical alignment group for the lyrics`() = songTab { _ ->
        onAllNodesWithContentDescription("Align Top").assertCountEquals(1)
        onAllNodesWithContentDescription("Align Middle").assertCountEquals(1)
        onAllNodesWithContentDescription("Align Bottom").assertCountEquals(1)
    }

    @Test
    fun `the tab renders one show dropdown per number and title output`() = songTab { _ ->
        showDropdowns().assertCountEquals(ShowDropdown.COUNT)
    }

    @Test
    fun `the tab renders one display mode row per output`() = songTab { _ ->
        onAllNodesWithText("1 Verse").assertCountEquals(ModeRow.COUNT)
        onAllNodesWithText("1 Line").assertCountEquals(ModeRow.COUNT)
    }

    @Test
    fun `the tab renders one language row per output`() = songTab { _ ->
        onAllNodesWithText("Both").assertCountEquals(ModeRow.COUNT)
        onAllNodesWithText("Primary").assertCountEquals(ModeRow.COUNT)
        onAllNodesWithText("Secondary").assertCountEquals(ModeRow.COUNT)
    }

    @Test
    fun `the tab renders a single bilingual layout row`() = songTab { _ ->
        onAllNodesWithText("Left / Right").assertCountEquals(1)
        onAllNodesWithText("Top / Bottom").assertCountEquals(1)
    }

    @Test
    fun `every tagged checkbox is present`() = songTab { _ ->
        val tags = listOf(
            "song_titleSlideEnabled",
            "song_fadeIn",
            "song_fadeOut",
            "song_crossfade",
            "song_wordWrap",
            "song_lyricsFontSizeAutoFit",
            "song_lyricsLowerThirdFontSizeAutoFit",
            "song_lookAheadFontSizeAutoFit",
            "song_lookAheadNextFontSizeAutoFit",
            "song_lowerThirdLookAheadFontSizeAutoFit",
            "song_lowerThirdLookAheadNextFontSizeAutoFit",
        )
        for (tag in tags) {
            onNodeWithTag(tag).assertExists("$tag must render on the tab")
        }
    }

    @Test
    fun `the shadow detail rows stay collapsed until their shadow is switched on`() = songTab { _ ->
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(0)
    }

    @Test
    fun `every shadow detail row expands when its shadow is on`() {
        val allShadows = AppSettings().let {
            it.copy(
                songSettings = it.songSettings.copy(
                    titleShadow = true,
                    titleLowerThirdShadow = true,
                    lyricsShadow = true,
                    lyricsLowerThirdShadow = true,
                    lookAheadShadow = true,
                    lookAheadNextShadow = true,
                    lowerThirdLookAheadShadow = true,
                    lowerThirdLookAheadNextShadow = true,
                ),
            )
        }
        songTab(initial = allShadows) { _ ->
            onAllNodesWithText("SIZE (%)").assertCountEquals(StyleGroup.COUNT)
            onAllNodesWithText("INTENSITY (%)").assertCountEquals(StyleGroup.COUNT)
            // Each row adds a colour field and two stepper fields to the tab. The two extra are the
            // full-screen and lower-third chord colours, which no shadow row accounts for.
            colorFields().assertCountEquals(StyleGroup.COUNT * 2 + 2)
            numberFields().assertCountEquals(15 + StyleGroup.COUNT * 2)
            // Every shadow defaults to black, and each row shows that value in its own colour field.
            onAllNodesWithText("#000000").assertCountEquals(StyleGroup.COUNT)
        }
    }

    @Test
    fun `the text margins section draws the screen mock-up it labels`() = songTab { _ ->
        onNodeWithText("Screen").assertExists("the margins diagram must render its screen box")
        for (edge in listOf("TOP", "LEFT", "RIGHT", "BOTTOM")) {
            onNodeWithText(edge).assertExists("the margins diagram must label its $edge field")
        }
    }

    @Test
    fun `the auto-fit buttons are absent without a presenter manager`() = songTab { _ ->
        // Only the six auto-fit checkbox captions remain, and those carry no click action.
        autoFitButtons().assertCountEquals(0)
        onAllNodesWithText("Auto").assertCountEquals(6)
    }

    /** Callers that have no presenter to offer omit the argument entirely rather than passing null. */
    @Test
    fun `the tab composes when the presenter manager argument is left out`() = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                SongSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                )
            }
        }
        onNodeWithText("Song Title Slide").assertExists("the tab must render on its default arguments")
        autoFitButtons().assertCountEquals(0)
    }

    @Test
    fun `the auto-fit buttons appear once a presenter manager is supplied`() {
        val withScreen = AppSettings().let {
            it.copy(projectionSettings = it.projectionSettings.copy(screenAssignments = listOf(ScreenAssignment())))
        }
        songTab(initial = withScreen, presenterManager = PresenterManager()) { _ ->
            autoFitButtons().assertCountEquals(2)
        }
    }
}
