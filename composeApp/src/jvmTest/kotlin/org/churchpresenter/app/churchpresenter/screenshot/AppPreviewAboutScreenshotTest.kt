@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialogContent
import java.io.File
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.ui.screenshot.THEMES
import org.churchpresenter.ui.screenshot.captureTo

class AppPreviewAboutScreenshotTest {

    @Test
    fun about() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        val appSettings = library()
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(size = Size(420f, 490f), density = Density(1f)) {
                setContent {
                    Box(Modifier.size(420.dp, 490.dp)) {
                        AboutDialogContent(
                            onDismiss = {},
                            appSettings = appSettings,
                            theme = mode,
                            // Pinned: the real one carries the build's git hash, so this image
                            // would differ from the committed one after every single commit.
                            versionDisplay = "26.8.0 (0000000)",
                        )
                    }
                }
                waitForIdle()
                captureTo(File("$SCREENSHOT_ROOT/previewApp/about_$suffix.png"))
            }
        }
    }
}
