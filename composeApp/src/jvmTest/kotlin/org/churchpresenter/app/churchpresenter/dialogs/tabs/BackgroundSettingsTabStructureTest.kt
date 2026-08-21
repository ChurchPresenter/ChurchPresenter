@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.app.churchpresenter.composables.isVlcAvailable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shape of the tab — how many of each repeated widget it renders and what it says — and
 * validates the ordinals the behaviour tests use to address the six type dropdowns.
 *
 * Everything the tab shows below a dropdown is conditional on that dropdown's value, so the counts
 * are asserted per configuration rather than once: with all six on Color there are six colour fields
 * and six opacity sliders; switch one to Image and a picker row replaces its colour field.
 */
class BackgroundSettingsTabStructureTest {

    private fun withTypes(
        default: String = Constants.BACKGROUND_COLOR,
        defaultLowerThird: String = Constants.BACKGROUND_COLOR,
        bible: String = Constants.BACKGROUND_COLOR,
        bibleLowerThird: String = Constants.BACKGROUND_COLOR,
        song: String = Constants.BACKGROUND_COLOR,
        songLowerThird: String = Constants.BACKGROUND_COLOR,
    ): AppSettings = AppSettings().let {
        it.copy(
            backgroundSettings = it.backgroundSettings.copy(
                defaultBackgroundType = default,
                defaultLowerThirdBackgroundType = defaultLowerThird,
                bibleBackground = BackgroundConfig(backgroundType = bible),
                bibleLowerThirdBackground = BackgroundConfig(backgroundType = bibleLowerThird),
                songBackground = BackgroundConfig(backgroundType = song),
                songLowerThirdBackground = BackgroundConfig(backgroundType = songLowerThird),
            ),
        )
    }

    @Test
    fun `the tab renders its four cards`() = backgroundTab { _ ->
        for (card in listOf("Default Background", "Default Lower Third Background", "Bible", "Songs")) {
            onAllNodesWithText(card).assertCountEquals(1)
        }
    }

    @Test
    fun `the tab explains what the two default cards are for`() = backgroundTab { _ ->
        onNodeWithText("Used when 'Default' is selected for Bible or Songs").assertExists()
        onNodeWithText("Used when 'Default' is selected for Bible or Songs in lower third display mode")
            .assertExists()
    }

    @Test
    fun `the Bible and Songs cards each offer a full screen and a lower third column`() =
        backgroundTab { _ ->
            onAllNodesWithText("Full Screen").assertCountEquals(2)
            onAllNodesWithText("Lower Third").assertCountEquals(2)
            onAllNodesWithText("Background Type:").assertCountEquals(4)
        }

    @Test
    fun `the tab renders one type dropdown per background slot`() = backgroundTab { _ ->
        typeDropdowns().assertCountEquals(TypeDropdown.COUNT)
    }

    /**
     * The behaviour tests address the dropdowns by ordinal, so this walks all six from a fixture
     * that gives each a different value and checks each ordinal lands on the slot it is named for.
     */
    @Test
    fun `each dropdown ordinal addresses the slot it is named for`() {
        val distinct = withTypes(
            default = Constants.BACKGROUND_IMAGE,
            defaultLowerThird = Constants.BACKGROUND_FOLLOW_DEFAULT,
            bible = Constants.BACKGROUND_COLOR,
            bibleLowerThird = Constants.BACKGROUND_GRADIENT,
            song = Constants.BACKGROUND_TRANSPARENT,
            songLowerThird = Constants.BACKGROUND_DEFAULT,
        )
        backgroundTab(initial = distinct) { _ ->
            typeDropdowns()[TypeDropdown.DEFAULT].assertTextEquals(TypeLabel.IMAGE)
            typeDropdowns()[TypeDropdown.DEFAULT_LOWER_THIRD].assertTextEquals(TypeLabel.FOLLOW_DEFAULT)
            typeDropdowns()[TypeDropdown.BIBLE_FULLSCREEN].assertTextEquals(TypeLabel.COLOR)
            typeDropdowns()[TypeDropdown.BIBLE_LOWER_THIRD].assertTextEquals(TypeLabel.GRADIENT)
            typeDropdowns()[TypeDropdown.SONG_FULLSCREEN].assertTextEquals(TypeLabel.TRANSPARENT)
            typeDropdowns()[TypeDropdown.SONG_LOWER_THIRD].assertTextEquals(TypeLabel.DEFAULT)
        }
    }

    @Test
    fun `every slot on Color shows a colour field and an opacity slider`() = backgroundTab { _ ->
        colorFields().assertCountEquals(TypeDropdown.COUNT)
        onAllNodesWithText("Background Opacity").assertCountEquals(TypeDropdown.COUNT)
        percentReadouts().assertCountEquals(TypeDropdown.COUNT)
        onNodeWithTag("bg_defaultColor").assertExists("the default colour field carries the tab's only tag")
    }

    @Test
    fun `switching a slot to Image replaces its colour field with a picker row`() {
        backgroundTab(initial = withTypes(bible = Constants.BACKGROUND_IMAGE)) { _ ->
            colorFields().assertCountEquals(TypeDropdown.COUNT - 1)
            onAllNodesWithText("Background Image:").assertCountEquals(1)
            onAllNodesWithText("No image selected").assertCountEquals(1)
            // The picker row adds a library and a stock-browse button; opacity is still offered.
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(1)
            onAllNodesWithText("Background Opacity").assertCountEquals(TypeDropdown.COUNT)
        }
    }

    @Test
    fun `switching a slot to Video Loop replaces its colour field with a video picker row`() {
        backgroundTab(initial = withTypes(song = Constants.BACKGROUND_VIDEO)) { _ ->
            colorFields().assertCountEquals(TypeDropdown.COUNT - 1)
            onAllNodesWithText("Background Video:").assertCountEquals(1)
            onAllNodesWithText("No video selected").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(1)
            onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(1)
        }
    }

    @Test
    fun `a slot on Transparent or Default shows nothing beneath its dropdown`() {
        backgroundTab(
            initial = withTypes(
                bible = Constants.BACKGROUND_TRANSPARENT,
                song = Constants.BACKGROUND_DEFAULT,
            ),
        ) { _ ->
            // Two of the six slots contribute no colour field, no picker and no opacity slider.
            colorFields().assertCountEquals(TypeDropdown.COUNT - 2)
            onAllNodesWithText("Background Opacity").assertCountEquals(TypeDropdown.COUNT - 2)
            onAllNodesWithText("Background Image:").assertCountEquals(0)
            onAllNodesWithText("Background Video:").assertCountEquals(0)
        }
    }

    @Test
    fun `only a lower-third slot offers the gradient controls`() {
        backgroundTab(initial = withTypes(bibleLowerThird = Constants.BACKGROUND_GRADIENT)) { _ ->
            onAllNodesWithText("TOP COLOR:").assertCountEquals(1)
            onAllNodesWithText("BOTTOM COLOR:").assertCountEquals(1)
            onAllNodesWithText("Top Opacity").assertCountEquals(1)
            onAllNodesWithText("Bottom Opacity").assertCountEquals(1)
            onAllNodesWithText("Transition Position").assertCountEquals(1)
            // Gradient replaces the colour field but adds two of its own, and three sliders.
            colorFields().assertCountEquals(TypeDropdown.COUNT - 1 + 2)
            percentReadouts().assertCountEquals(TypeDropdown.COUNT - 1 + 3)
        }
    }

    @Test
    fun `the gradient option is offered only by the lower-third dropdowns`() {
        // Each menu is opened in its own composition: dismissing one would mean clicking something
        // else on the tab, and nothing outside the menu is unambiguously clickable.
        val menus = listOf(
            TypeDropdown.BIBLE_FULLSCREEN to false,
            TypeDropdown.BIBLE_LOWER_THIRD to true,
            TypeDropdown.SONG_FULLSCREEN to false,
            TypeDropdown.SONG_LOWER_THIRD to true,
        )
        for ((ordinal, offersGradient) in menus) {
            backgroundTab { _ ->
                typeDropdowns()[ordinal].scrollThenClick()
                waitForIdle()
                assertEquals(
                    if (offersGradient) 1 else 0,
                    onAllNodesWithText(TypeLabel.GRADIENT)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false).size,
                    "dropdown $ordinal ${if (offersGradient) "must" else "must not"} offer Gradient",
                )
            }
        }
    }

    /**
     * Video needs VLC. Where it is missing the option is disabled and relabelled to say so, which is
     * the only part of this tab that depends on the machine the tests run on — hence the branch.
     */
    @Test
    fun `the video option says so when VLC is missing`() = backgroundTab { _ ->
        typeDropdowns()[TypeDropdown.BIBLE_FULLSCREEN].scrollThenClick()
        waitForIdle()
        if (isVlcAvailable) {
            onAllNodesWithText(TypeLabel.VIDEO).assertCountEquals(1)
            onAllNodesWithText("(Install VLC)", substring = true).assertCountEquals(0)
        } else {
            onAllNodesWithText("${TypeLabel.VIDEO} (Install VLC)").assertCountEquals(1)
        }
    }

    /** Guards against a control being added to the tab without a test noticing. */
    @Test
    fun `the tab renders no text beyond the expected inventory`() = backgroundTab { _ ->
        val expected = setOf(
            "Default Background", "Default Lower Third Background", "Bible", "Songs",
            "Used when 'Default' is selected for Bible or Songs",
            "Used when 'Default' is selected for Bible or Songs in lower third display mode",
            "Full Screen", "Lower Third", "Background Type:", "Background Opacity",
            "Color", "COLOR:", "BACKGROUND COLOR:", "#000000", "100%",
        )
        val rendered = mutableSetOf<String>()
        onAllNodesWithText("", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .forEach { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.forEach { rendered += it.text }
                node.config.getOrNull(SemanticsProperties.EditableText)?.let { rendered += it.text }
            }
        assertEquals(
            emptyList(),
            rendered.filter { it.isNotBlank() && it !in expected }.sorted(),
            "the tab renders text the inventory does not list — add it, or test the control that shows it",
        )
    }
}
