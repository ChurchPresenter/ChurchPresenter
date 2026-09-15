package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The content tabs' Lower Third Animation section: the switch that turns a band's type to Lottie,
 * and the dropdown that picks its template from the lower-thirds folder — for the Bible's band
 * and the songs' alike.
 */
@OptIn(ExperimentalTestApi::class)
class LowerThirdAnimationSectionTest {

    private fun section(scope: BackgroundScope, folder: File?, drive: ComposeUiTest.(() -> AppSettings) -> Unit) =
        runComposeUiTest {
            var settings by mutableStateOf(AppSettings())
            setContent {
                MaterialTheme {
                    LowerThirdAnimationSection(
                        settings = settings,
                        onSettingsChange = { transform -> settings = transform(settings) },
                        generatorDir = folder,
                        scope = scope,
                    )
                }
            }
            drive { settings }
        }

    private fun AppSettings.bibleBand() = backgroundSettings.configFor(BackgroundScope.BIBLE_LOWER_THIRD)

    @Test
    fun `the switch turns the Bible band's type to Lottie and back, and the picker only shows while it is on`() =
        withLottieFolder("sunday.json" to lottieJson()) { folder ->
            section(BackgroundScope.BIBLE_LOWER_THIRD, folder) { get ->
                onNodeWithText("No template selected").assertDoesNotExist()
                onNodeWithText("Animate the band with a Lottie template").performClick()
                assertEquals(Constants.BACKGROUND_LOTTIE, get().bibleBand().backgroundType)
                onNodeWithText("No template selected").assertExists()
                onNodeWithText("Generate…").assertExists("the folder is known, so a new one can be made")
                onNodeWithText("Animate the band with a Lottie template").performClick()
                assertEquals(Constants.BACKGROUND_DEFAULT, get().bibleBand().backgroundType, "off is the default type")
                onNodeWithText("No template selected").assertDoesNotExist()
            }
        }

    @Test
    fun `a template picked from the folder is written to the songs' band, leaving the Bible's alone`() =
        withLottieFolder("advent.json" to lottieJson()) { folder ->
            section(BackgroundScope.SONG_LOWER_THIRD, folder) { get ->
                onNodeWithText("Animate the band with a Lottie template").performClick()
                onNodeWithText("No template selected").performClick()
                onAllNodesWithText("advent").onLast().performClick()
                val stored = get().backgroundSettings
                val song = stored.configFor(BackgroundScope.SONG_LOWER_THIRD)
                assertEquals(File(folder, "advent.json").absolutePath, song.backgroundLottie)
                val bible = stored.configFor(BackgroundScope.BIBLE_LOWER_THIRD)
                assertEquals(AppSettings().backgroundSettings.configFor(BackgroundScope.BIBLE_LOWER_THIRD), bible)
            }
        }

    @Test
    fun `without a folder there is no generate button`() =
        section(BackgroundScope.BIBLE_LOWER_THIRD, folder = null) { _ ->
            onNodeWithText("Animate the band with a Lottie template").performClick()
            onNodeWithText("No template selected").assertExists()
            onNodeWithText("Generate…").assertDoesNotExist()
        }
}
