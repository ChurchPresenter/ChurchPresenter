@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.SHARE_STORY_DIALOG_HEIGHT
import org.churchpresenter.app.churchpresenter.dialogs.SHARE_STORY_DIALOG_WIDTH
import org.churchpresenter.app.churchpresenter.dialogs.ShareYourStoryContent
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import kotlin.test.Test

private const val ROOT = "$SCREENSHOT_ROOT/shareYourStory"

class ShareYourStoryDialogScreenshotTest {

    private fun shoot(name: String, height: Dp) {
        File(ROOT).mkdirs()
        THEMES.forEach { (suffix, mode) ->
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(SHARE_STORY_DIALOG_WIDTH, height)) {
                            ShareYourStoryContent(onShare = {}, onDismiss = {})
                        }
                    }
                }
                waitForIdle()
                captureTo(File("$ROOT/${name}_$suffix.png"))
            }
        }
    }

    @Test
    fun `the story prompt`() = shoot("share_your_story", SHARE_STORY_DIALOG_HEIGHT)

    @Test
    fun `the story prompt with the ask scrolling`() = shoot("share_your_story_scrolled", 320.dp)
}
