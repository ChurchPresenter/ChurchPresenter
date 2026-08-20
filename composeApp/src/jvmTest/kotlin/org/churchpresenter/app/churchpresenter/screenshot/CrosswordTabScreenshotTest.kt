@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.tabs.CrosswordTab
import org.churchpresenter.app.churchpresenter.tabs.showsExactly
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Crossword tab, in both themes.
 *
 * It was the last user-facing tab with no screenshot coverage at all — not even a `previewApp`
 * shot, because it is opt-in (`showCrosswordTab`) and filtered out of the tab-visibility menu, so
 * nothing ever rendered it for review. That is 465 lines of UI no reviewer has seen.
 *
 * The puzzles are the real bundled `.xwp` files, as in `CrosswordTabTest` — the tab decodes them
 * itself, so these shots show what a user actually gets rather than a fixture that could drift from
 * the decoder. Progress saving is not exercised: the tab debounces it behind a 500ms delay, and a
 * screenshot has no reason to buy that wait.
 */
class CrosswordTabScreenshotTest {

    private fun shoot(
        name: String,
        width: Dp? = null,
        drive: ComposeUiTest.() -> Unit = { waitForIdle() },
    ) = stackedThemes(SECTION, name) { mode, file ->
        runComposeUiTest {
            setContent {
                var settings by remember { mutableStateOf(AppSettings()) }
                ChurchPresenterTheme(themeMode = mode) {
                    // The tab paints no ground of its own — in the app it sits on the window's
                    // `colorScheme.background`. Without this the capture is transparent wherever
                    // the tab does not draw, so the dark shot came out light-text-on-white.
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                            CrosswordTab(
                                appSettings = settings,
                                onSettingsChange = { transform -> settings = transform(settings) },
                            )
                        }
                    }
                }
            }
            // Puzzles are read from resources on first composition — wait for the load to end on a
            // positive signal, never on a duration.
            waitUntil("the puzzles to load", RENDER_TIMEOUT_MS) { !showsExactly("Loading puzzles…") }
            drive()
            captureTo(file)
        }
    }

    @Test
    fun `the first level as it opens`() = shoot("level_open")

    @Test
    fun `checking an unfinished grid`() = shoot("check_unfinished") {
        onNodeWithText("Check Answers").performClick()
        waitForIdle()
    }

    @Test
    fun `a narrow panel`() = shoot("narrow_panel", width = 480.dp)

    private companion object {
        const val SECTION = "crosswordTab"
    }
}
