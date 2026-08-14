@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundConfig
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A complete inventory of what the tab puts on screen: every heading, caption, button label, content
 * description and stored value it displays, with the exact number of times each appears.
 *
 * The behaviour tests drive the controls; this asserts the words around them are actually there. A
 * caption is as much a part of the tab as the control it names — a `stringResource` pointed at the
 * wrong key, or a row quietly dropped in a refactor, changes nothing a behaviour test would notice,
 * because those find their targets by tag, ordinal or displayed value rather than by caption.
 *
 * The counts carry as much weight as the strings. "Background Type:" appearing four times is what
 * says all four Bible/Songs columns rendered; the two default cards head their own dropdown with a
 * help line instead, which is why they do not add to it. Both lists below were generated from the
 * rendered semantics tree, so they are exhaustive by construction rather than by memory.
 */
class BackgroundSettingsTabLabelsTest {

    /** Every string the tab renders out of the box, and how many times it must appear. */
    private val outOfTheBox = mapOf(
        // Card headings and the two help lines.
        "Default Background" to 1,
        "Default Lower Third Background" to 1,
        "Bible" to 1,
        "Songs" to 1,
        "Used when 'Default' is selected for Bible or Songs" to 1,
        "Used when 'Default' is selected for Bible or Songs in lower third display mode" to 1,

        // Column subtitles — one Full Screen and one Lower Third per content card.
        "Full Screen" to 2,
        "Lower Third" to 2,

        // One "Background Type:" per Bible/Songs column; the default cards label theirs differently.
        "Background Type:" to 4,

        // Six slots, each on Color out of the box: a dropdown, a colour field and an opacity slider.
        "Color" to TypeDropdown.COUNT,
        "COLOR:" to 2,               // the two default cards' colour fields
        "BACKGROUND COLOR:" to 4,    // the four Bible/Songs columns' colour fields
        "#000000" to TypeDropdown.COUNT,
        "Background Opacity" to TypeDropdown.COUNT,
        "100%" to TypeDropdown.COUNT,
    )

    @Test
    fun `every label the tab renders is on screen the expected number of times`() = backgroundTab { _ ->
        for ((text, count) in outOfTheBox) {
            onAllNodesWithText(text).assertCountEquals(count)
        }
    }

    /** Guards against a control being added to the tab without a test noticing. */
    @Test
    fun `the tab renders no text beyond the inventory`() = backgroundTab { _ ->
        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        assertEquals(
            emptyList(),
            rendered.filter { it.isNotBlank() && it !in outOfTheBox }.sorted(),
            "the tab renders text the inventory does not list — add it, with its expected count",
        )
    }

    /**
     * The rest of the tab's vocabulary only appears once a slot is set to something other than Color:
     * the picker rows, their icon buttons, the ATEM badges and the gradient controls. This puts every
     * one of them on screen at once and checks the lot, including each slider's starting readout.
     */
    @Test
    fun `every label the conditional rows render is on screen`() {
        val everyKind = AppSettings().let {
            it.copy(
                backgroundSettings = it.backgroundSettings.copy(
                    defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                    defaultBackgroundImage = "/tmp/backdrops/still.png",
                    defaultLowerThirdBackgroundType = Constants.BACKGROUND_VIDEO,
                    defaultLowerThirdBackgroundVideo = "/tmp/backdrops/loop.mp4",
                    bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT),
                    bibleLowerThirdBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_GRADIENT,
                        gradientEnabled = true,
                    ),
                    songBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR),
                    songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
                ),
                atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
            )
        }
        backgroundTab(initial = everyKind) { _ ->
            // Picker rows and the file names they show.
            onAllNodesWithText("Background Image:").assertCountEquals(1)
            onAllNodesWithText("Background Video:").assertCountEquals(1)
            onAllNodesWithText("still.png").assertCountEquals(1)
            onAllNodesWithText("loop.mp4").assertCountEquals(1)

            // The icon buttons in those rows, named by the tooltip each also publishes.
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(2)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(2)
            onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(1)
            onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(1)
            onAllNodesWithText("1").assertCountEquals(1) // the upload buttons' slot badges
            onAllNodesWithText("2").assertCountEquals(1)

            // The type labels this configuration puts on the dropdowns.
            onAllNodesWithText(TypeLabel.IMAGE).assertCountEquals(1)
            onAllNodesWithText(TypeLabel.VIDEO).assertCountEquals(1)
            onAllNodesWithText(TypeLabel.TRANSPARENT).assertCountEquals(1)
            onAllNodesWithText(TypeLabel.GRADIENT).assertCountEquals(1)
            onAllNodesWithText(TypeLabel.DEFAULT).assertCountEquals(1)
            onAllNodesWithText(TypeLabel.COLOR).assertCountEquals(1)

            // The gradient controls, with the readouts their stored defaults produce.
            onAllNodesWithText("TOP COLOR:").assertCountEquals(1)
            onAllNodesWithText("BOTTOM COLOR:").assertCountEquals(1)
            onAllNodesWithText("Top Opacity").assertCountEquals(1)
            onAllNodesWithText("Bottom Opacity").assertCountEquals(1)
            onAllNodesWithText("Transition Position").assertCountEquals(1)
            onAllNodesWithText("0%").assertCountEquals(1)   // gradientTopOpacity   = 0.0
            onAllNodesWithText("80%").assertCountEquals(1)  // gradientBottomOpacity = 0.8
            onAllNodesWithText("50%").assertCountEquals(1)  // gradientPosition      = 0.5
        }
    }

    /** Every option each dropdown offers, checked against the slot it belongs to. */
    @Test
    fun `each dropdown menu offers exactly the types its slot supports`() {
        val menus = mapOf(
            TypeDropdown.DEFAULT to listOf(
                TypeLabel.COLOR, TypeLabel.IMAGE, TypeLabel.VIDEO, TypeLabel.TRANSPARENT,
            ),
            TypeDropdown.DEFAULT_LOWER_THIRD to listOf(
                TypeLabel.FOLLOW_DEFAULT, TypeLabel.COLOR, TypeLabel.IMAGE, TypeLabel.VIDEO,
                TypeLabel.TRANSPARENT,
            ),
            TypeDropdown.BIBLE_FULLSCREEN to listOf(
                TypeLabel.DEFAULT, TypeLabel.COLOR, TypeLabel.IMAGE, TypeLabel.VIDEO, TypeLabel.TRANSPARENT,
            ),
            TypeDropdown.SONG_LOWER_THIRD to listOf(
                TypeLabel.DEFAULT, TypeLabel.COLOR, TypeLabel.IMAGE, TypeLabel.VIDEO, TypeLabel.TRANSPARENT,
                TypeLabel.GRADIENT,
            ),
        )
        for ((ordinal, options) in menus) {
            // One composition per menu: closing one would mean clicking something else on the tab,
            // and nothing outside an open menu is unambiguously clickable.
            backgroundTab { _ ->
                typeDropdowns()[ordinal].scrollThenClick()
                waitForIdle()
                for (option in TypeLabel.all) {
                    val expected = if (option in options) 1 else 0
                    val onScreenAlready = if (option == TypeLabel.COLOR) TypeDropdown.COUNT else 0
                    // Video is the one item whose menu text depends on the machine: without VLC the
                    // tab appends "(Install VLC)" to it. It is still offered, under that label.
                    val menuText = if (option == TypeLabel.VIDEO) videoMenuLabel else option
                    onAllNodesWithText(menuText).assertCountEquals(expected + onScreenAlready)
                }
            }
        }
    }
}
