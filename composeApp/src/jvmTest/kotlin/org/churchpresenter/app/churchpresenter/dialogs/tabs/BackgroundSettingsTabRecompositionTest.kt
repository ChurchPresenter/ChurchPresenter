@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.ui.colorFields

/**
 * Covers what the tab does when its *surroundings* recompose rather than its controls being used.
 *
 * The settings dialog re-renders the whole tab whenever anything above it changes, and this tab is
 * six copies of a widget set whose contents are rebuilt from scratch each time the background type
 * changes. Sections whose own inputs have not moved need to drop out of that work. These tests hold
 * the settings instance and the change callback fixed, move something else, and assert the tab still
 * shows what it should — exercising the skip paths the control tests never reach, since those
 * produce a new settings object on every interaction.
 */
class BackgroundSettingsTabRecompositionTest {

    /** A change callback with a stable identity, so recomposing the caller does not disturb the tab. */
    private val inertCallback: (((AppSettings) -> AppSettings) -> Unit) = { }

    @Test
    fun `the tab survives a recomposition that changes none of its inputs`() = runComposeUiTest {
        val settings = AppSettings()
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
                }
            }
        }
        onNodeWithText("tick 0").assertExists("the surrounding content must render")
        onNodeWithText("Default Background").assertExists("and so must the tab")
        typeDropdowns().assertCountEquals(TypeDropdown.COUNT)

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists("the surrounding content must have re-rendered")
        onNodeWithText("Default Background").assertExists("the tab must still be on screen after skipping")
        onNodeWithTag("bg_defaultColor").assertExists("and still hold its controls")
        typeDropdowns().assertCountEquals(TypeDropdown.COUNT)
        colorFields().assertCountEquals(TypeDropdown.COUNT)
    }

    /**
     * Changing one slot's type rebuilds that card's contents and leaves the other five alone. This is
     * the everyday case — the operator picks a type — driven here from the top rather than through a
     * dropdown, so the whole tab re-renders around a single changed field.
     */
    @Test
    fun `changing one slot's type re-renders that card and leaves the others intact`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        colorFields().assertCountEquals(TypeDropdown.COUNT)
        onAllNodesWithText("Background Image:").assertCountEquals(0)

        runOnIdle {
            settings = settings.copy(
                backgroundSettings = settings.backgroundSettings.copy(
                    bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE),
                ),
            )
        }
        waitForIdle()

        onAllNodesWithText("Background Image:").assertCountEquals(1)
        onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(1)
        colorFields().assertCountEquals(TypeDropdown.COUNT - 1)
        typeDropdowns()[TypeDropdown.BIBLE_FULLSCREEN].assertTextEquals(TypeLabel.IMAGE)
        typeDropdowns()[TypeDropdown.SONG_FULLSCREEN].assertTextEquals(TypeLabel.COLOR)
        onNodeWithTag("bg_defaultColor").assertExists("the default card must be untouched")
    }

    /**
     * Configuring a switcher adds two buttons to every image row without otherwise disturbing the
     * tab — the ATEM host lives outside `backgroundSettings`, so this is a change to a part of the
     * settings most of the tab does not read.
     */
    @Test
    fun `configuring an ATEM host adds the upload buttons to the image rows`() = runComposeUiTest {
        var settings by mutableStateOf(
            AppSettings().let {
                it.copy(
                    backgroundSettings = it.backgroundSettings.copy(
                        defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                        defaultBackgroundImage = "/tmp/a.png",
                    ),
                )
            },
        )
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)

        runOnIdle {
            settings = settings.copy(atemSettings = settings.atemSettings.copy(host = "10.0.0.5"))
        }
        waitForIdle()

        onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(1)
        onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(1)
        onNodeWithText("Background Image:").assertExists("the rest of the row must be untouched")
    }

    /**
     * Turning a lower-third column to Gradient adds five controls to it; turning it back removes them
     * again. Doing both in one composition checks the column tears its own contents down cleanly.
     */
    @Test
    fun `a column adds and removes its gradient controls as the type changes`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        onAllNodesWithText("Top Opacity").assertCountEquals(0)

        runOnIdle {
            settings = settings.copy(
                backgroundSettings = settings.backgroundSettings.copy(
                    songLowerThirdBackground = BackgroundConfig(
                        backgroundType = Constants.BACKGROUND_GRADIENT,
                        gradientEnabled = true,
                    ),
                ),
            )
        }
        waitForIdle()
        onAllNodesWithText("Top Opacity").assertCountEquals(1)
        onAllNodesWithText("TOP COLOR:").assertCountEquals(1)
        percentReadouts().assertCountEquals(TypeDropdown.COUNT - 1 + 3)

        runOnIdle {
            settings = settings.copy(
                backgroundSettings = settings.backgroundSettings.copy(
                    songLowerThirdBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR),
                ),
            )
        }
        waitForIdle()
        onAllNodesWithText("Top Opacity").assertCountEquals(0)
        onAllNodesWithText("TOP COLOR:").assertCountEquals(0)
        colorFields().assertCountEquals(TypeDropdown.COUNT)
    }

    /**
     * Every kind of row the tab can build, all on screen at once: an image row with its ATEM upload
     * buttons, a video row, a colour field, and a gradient column. A recomposition that changes none
     * of their inputs has to leave all of it standing.
     *
     * The plain-defaults skip test above only ever has colour fields on screen, so it says nothing
     * about whether the picker rows, upload buttons and gradient controls drop out of recomposition
     * correctly. This is that test.
     */
    private fun everyRowKind(): AppSettings = AppSettings().let {
        it.copy(
            backgroundSettings = it.backgroundSettings.copy(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "/tmp/backdrops/default.png",
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultLowerThirdBackgroundVideo = "/tmp/backdrops/lower.mp4",
                bibleBackground = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR),
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                ),
                // A column on Video Loop and a column on Image: the card-level pickers above use the
                // same rows but a different callback path, so both have to be on screen for a skip
                // test to say anything about either.
                songBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_VIDEO,
                    backgroundVideo = "/tmp/backdrops/song.mp4",
                ),
                songLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = "/tmp/backdrops/song.png",
                ),
            ),
            atemSettings = it.atemSettings.copy(host = "10.0.0.5"),
        )
    }

    private fun assertEveryRowKindIsIntact(test: ComposeUiTest) = with(test) {
        onAllNodesWithText("Background Image:").assertCountEquals(2)
        onAllNodesWithText("Background Video:").assertCountEquals(2)
        onAllNodesWithContentDescription("Browse downloaded library").assertCountEquals(4)
        onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(4)
        onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(2)
        onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(2)
        onAllNodesWithText("Top Opacity").assertCountEquals(1)
        onAllNodesWithText("TOP COLOR:").assertCountEquals(1)
        onAllNodesWithText("default.png").assertCountEquals(1)
        onAllNodesWithText("lower.mp4").assertCountEquals(1)
        onAllNodesWithText("song.mp4").assertCountEquals(1)
        onAllNodesWithText("song.png").assertCountEquals(1)
        typeDropdowns().assertCountEquals(TypeDropdown.COUNT)
    }

    @Test
    fun `a fully populated tab survives a recomposition that changes nothing`() = runComposeUiTest {
        val settings = everyRowKind()
        var tick by mutableStateOf(0)
        setContent {
            MaterialTheme {
                Column {
                    Text("tick $tick")
                    BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
                }
            }
        }
        assertEveryRowKindIsIntact(this)

        runOnIdle { tick = 1 }
        waitForIdle()

        onNodeWithText("tick 1").assertExists("the surrounding content must have re-rendered")
        assertEveryRowKindIsIntact(this)
    }

    /**
     * The stock-photo API keys are threaded down into every picker row but read by nothing else, so
     * changing one must leave every column's own contents exactly as they were.
     */
    @Test
    fun `changing a stock photo API key leaves every row standing`() = runComposeUiTest {
        var settings by mutableStateOf(everyRowKind())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        assertEveryRowKindIsIntact(this)

        runOnIdle {
            settings = settings.copy(
                stockPhotoSettings = settings.stockPhotoSettings.copy(pexelsApiKey = "test-key"),
            )
        }
        waitForIdle()
        assertEveryRowKindIsIntact(this)

        runOnIdle {
            settings = settings.copy(
                stockPhotoSettings = settings.stockPhotoSettings.copy(pixabayApiKey = "other-key"),
            )
        }
        waitForIdle()
        assertEveryRowKindIsIntact(this)
    }

    /** Clearing the switcher host takes the upload buttons away and leaves the rows themselves. */
    @Test
    fun `clearing the ATEM host removes the upload buttons from every image row`() = runComposeUiTest {
        var settings by mutableStateOf(everyRowKind())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(2)

        runOnIdle { settings = settings.copy(atemSettings = settings.atemSettings.copy(host = "")) }
        waitForIdle()

        onAllNodesWithContentDescription("Upload to Background Slot 1").assertCountEquals(0)
        onAllNodesWithContentDescription("Upload to Background Slot 2").assertCountEquals(0)
        onAllNodesWithText("Background Image:").assertCountEquals(2)
        onAllNodesWithText("default.png").assertCountEquals(1)
    }

    /** Choosing a different file re-renders just that row's name. */
    @Test
    fun `a stored image path change reaches only its own row`() = runComposeUiTest {
        var settings by mutableStateOf(everyRowKind())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        onAllNodesWithText("default.png").assertCountEquals(1)

        runOnIdle {
            settings = settings.copy(
                backgroundSettings = settings.backgroundSettings.copy(
                    defaultBackgroundImage = "/tmp/backdrops/replaced.png",
                ),
            )
        }
        waitForIdle()

        onAllNodesWithText("replaced.png").assertCountEquals(1)
        onAllNodesWithText("default.png").assertCountEquals(0)
        onAllNodesWithText("song.png").assertCountEquals(1)
        onAllNodesWithText("lower.mp4").assertCountEquals(1)
    }

    @Test
    fun `a stored colour change reaches the field without any interaction`() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            MaterialTheme {
                BackgroundSettingsTab(settings = settings, onSettingsChange = inertCallback)
            }
        }
        assertEquals(6, colorFields().fetchSemanticsNodes(atLeastOneRootRequired = false).size)
        onAllNodesWithText("#000000").assertCountEquals(TypeDropdown.COUNT)

        runOnIdle {
            settings = settings.copy(
                backgroundSettings = settings.backgroundSettings.copy(defaultBackgroundColor = "#ABCDEF"),
            )
        }
        waitForIdle()

        onNodeWithText("#ABCDEF").assertExists("the field must show the newly stored colour")
        onAllNodesWithText("#000000").assertCountEquals(TypeDropdown.COUNT - 1)
    }
}
