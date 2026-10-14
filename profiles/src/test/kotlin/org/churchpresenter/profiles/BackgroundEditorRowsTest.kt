@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.settings.utils.Constants
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundEditorRowsTest {

    private fun settingsWith(scope: BackgroundScope, config: BackgroundConfig): AppSettings {
        val base = AppSettings()
        return base.copy(backgroundSettings = base.backgroundSettings.withConfigFor(scope, config))
    }

    private fun surfaceRows(
        scope: BackgroundScope,
        config: BackgroundConfig,
        includeLottie: Boolean = false,
        lottiePickerHere: Boolean? = null,
        body: ComposeUiTest.(get: () -> BackgroundConfig) -> Unit,
    ) = runComposeUiTest {
        var settings by mutableStateOf(settingsWith(scope, config))
        setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (lottiePickerHere == null) {
                        BackgroundSurfaceRows(scope, settings, { edit -> settings = edit(settings) }, includeLottie)
                    } else {
                        BackgroundSurfaceRows(
                            scope, settings, { edit -> settings = edit(settings) }, includeLottie, lottiePickerHere,
                        )
                    }
                }
            }
        }
        waitForIdle()
        body { settings.backgroundSettings.configFor(scope) }
    }

    @Test
    fun `a colour background's field writes the new colour`() =
        surfaceRows(BackgroundScope.DEFAULT, BackgroundConfig(backgroundColor = "#112233")) { get ->
            recolor("#112233", "#AABBCC")
            assertEquals("#AABBCC", get().backgroundColor.uppercase())
        }

    @Test
    fun `a lottie band chosen elsewhere is pointed at, not picked here`() = surfaceRows(
        BackgroundScope.BIBLE_LOWER_THIRD,
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE),
        includeLottie = true,
        lottiePickerHere = false,
    ) { _ ->
        onNodeWithText("Set in Lower third band, below.").assertExists()
        onNodeWithText("No template selected").assertDoesNotExist()
    }

    @Test
    fun `a bible band's template row notes what the template ignores`() = surfaceRows(
        BackgroundScope.BIBLE_LOWER_THIRD,
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE),
        includeLottie = true,
    ) { _ ->
        onNodeWithText("the reference position", substring = true).assertExists()
    }

    @Test
    fun `a song band's template row notes what the template ignores`() = surfaceRows(
        BackgroundScope.SONG_LOWER_THIRD,
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE),
        includeLottie = true,
    ) { _ ->
        onNodeWithText("where the title sits", substring = true).assertExists()
    }

    @Test
    fun `picking a template from the band folder stores its path`() {
        val dir = SettingsManager.bibleLowerThirdsDir()
        val createdDir = !dir.exists()
        dir.mkdirs()
        val template = File(dir, "editor-rows-band.json").apply { writeText(lottieJson("editor-rows-band")) }
        try {
            surfaceRows(
                BackgroundScope.BIBLE_LOWER_THIRD,
                BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE),
                includeLottie = true,
            ) { get ->
                onNodeWithText("No template selected").performClick()
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("editor-rows-band").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithText("editor-rows-band").onLast().performClick()
                waitForIdle()
                assertEquals(template.absolutePath, get().backgroundLottie)
            }
        } finally {
            template.delete()
            if (createdDir) dir.deleteRecursively()
        }
    }

    @Test
    fun `a colour wash above the band shows its fill and writes a new one`() = surfaceRows(
        BackgroundScope.BIBLE_LOWER_THIRD,
        BackgroundConfig(aboveBandType = Constants.BACKGROUND_COLOR, aboveBandColor = "#445566"),
    ) { get ->
        onNodeWithText("FILL COLOR", ignoreCase = true).assertExists()
        recolor("#445566", "#DDEEFF")
        assertEquals("#DDEEFF", get().aboveBandColor.uppercase())
    }

    @Test
    fun `a transparent wash above the band offers no opacity`() = surfaceRows(
        BackgroundScope.DEFAULT_LOWER_THIRD,
        BackgroundConfig(aboveBandType = Constants.BACKGROUND_TRANSPARENT),
    ) { _ ->
        onNodeWithText("ABOVE THE BAND", ignoreCase = true).assertExists()
        onNodeWithText("FILL OPACITY", ignoreCase = true).assertDoesNotExist()
    }
}
