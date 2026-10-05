@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.ClearGroupsDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.ClearLayersMenuItems
import org.churchpresenter.app.churchpresenter.dialogs.clearGroupEditTag
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Clear groups dialog and the Clear layers menu, in both themes. Shot through their contents,
 * since neither a `DialogWindow` nor a dropdown's popup can be photographed headless.
 */
class ClearGroupsDialogScreenshotTest {

    private val groups = listOf(
        ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES")),
        ClearGroup("clear2", "Clear graphics", listOf("GRAPHICS", "PROPS")),
    )

    private fun shoot(name: String, drive: ComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit) =
        stackedThemes(SECTION, name) { mode, file ->
            runComposeUiTest {
                setContent { ChurchPresenterTheme(themeMode = mode) { content() } }
                drive()
                waitForIdle()
                captureTo(file)
            }
        }

    @Test
    fun `the dialog with no groups`() = shoot("empty") {
        Box(Modifier.size(560.dp, 480.dp)) { ClearGroupsDialogContent(emptyList(), {}, {}, {}) }
    }

    @Test
    fun `two groups, one open for editing`() =
        shoot("editing", { onNodeWithTag(clearGroupEditTag("clear1")).performClick() }) {
        Box(Modifier.size(560.dp, 480.dp)) { ClearGroupsDialogContent(groups, {}, {}, {}) }
    }

    @Test
    fun `the menu, with the slide, the lower third and props on air`() = shoot("menu") {
        Surface(Modifier.width(240.dp)) {
            Column { ClearLayersMenuItems(groups, setOf(Layer.SLIDE, Layer.GRAPHICS, Layer.PROPS), {}, {}, {}) }
        }
    }

    private companion object {
        const val SECTION = "clearGroupsDialog"
    }
}
