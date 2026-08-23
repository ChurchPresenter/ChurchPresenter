@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.dialogs.SetupWizardContent
import java.io.File
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.ui.screenshot.THEMES
import org.churchpresenter.ui.screenshot.captureTo

class AppPreviewSetupWizardScreenshotTest {

    @Test
    fun `every step of the getting started wizard`() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(size = Size(700f, 620f), density = Density(1f)) {
                setContent {
                    Box(Modifier.size(700.dp, 620.dp)) {
                        SetupWizardContent(
                            theme = mode,
                            selectedLanguage = Language.ENGLISH,
                            onLanguageSelected = {},
                            onThemeSelected = {},
                            onOpenSettings = {},
                            onDismiss = {},
                        )
                    }
                }
                waitForIdle()
                STEPS.forEachIndexed { index, step ->
                    captureTo(File("$SCREENSHOT_ROOT/previewApp/setup_${index + 1}_${step}_$suffix.png"))
                    if (index < STEPS.lastIndex) {
                        onNodeWithText("Next").performClick()
                        waitForIdle()
                    }
                }
            }
        }
    }

    private companion object {
        val STEPS = listOf(
            "language", "theme", "welcome", "bible", "songs", "projection", "vlc", "ready",
        )
    }
}
