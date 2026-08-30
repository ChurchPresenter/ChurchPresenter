@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.dialogs.ABOUT_DIALOG_HEIGHT
import org.churchpresenter.app.churchpresenter.dialogs.ABOUT_DIALOG_WIDTH
import org.churchpresenter.app.churchpresenter.dialogs.AboutDialogContent
import java.io.File
import kotlin.test.Test

class AppPreviewAboutScreenshotTest {

    @Test
    fun about() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        val appSettings = library()
        THEMES.forEach { (suffix, mode) ->
            // The real window's own size, not a copy of it: the literals here said 420x490 long
            // after ABOUT_DIALOG_HEIGHT went to 560, so the image cropped the OK button off a
            // dialog that has room for it and made a fitting layout look broken.
            runSkikoComposeUiTest(
                size = Size(ABOUT_DIALOG_WIDTH.value, ABOUT_DIALOG_HEIGHT.value),
                density = Density(1f),
            ) {
                setContent {
                    Box(Modifier.size(ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT)) {
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
