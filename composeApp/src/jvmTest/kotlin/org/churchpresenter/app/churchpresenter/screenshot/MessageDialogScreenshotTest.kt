@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.MessageDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.messageTemplateTag
import org.churchpresenter.app.churchpresenter.dialogs.messageTokenTag
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Message dialog, in both themes: empty with its saved messages, and with one picked, its token
 * filled in and another message on air. Shot through `MessageDialogContent`, since a `DialogWindow`
 * cannot be photographed headless.
 */
class MessageDialogScreenshotTest {

    private val templates = listOf(
        MessageTemplate("message1", "Nursery", "Parent of child #{number}, please come to the nursery", 120),
        MessageTemplate("message2", "Car lights", "The car with plate {plate} has its lights on"),
    )

    private fun shoot(name: String, onAir: Cue.Message? = null, drive: ComposeUiTest.() -> Unit = {}) =
        stackedThemes(SECTION, name) { mode, file ->
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(520.dp, 560.dp)) {
                            MessageDialogContent(templates, {}, onAir, {}, {}, {})
                        }
                    }
                }
                drive()
                waitForIdle()
                captureTo(file)
            }
        }

    @Test
    fun `the dialog with saved messages`() = shoot("empty")

    @Test
    fun `a saved message picked and filled in, with another on air`() = shoot(
        "filled_on_air",
        onAir = Cue.Message("Parent of child #17, please come to the nursery"),
    ) {
        onNodeWithTag(messageTemplateTag("message1")).performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(messageTokenTag("number")))).performTextInput("42")
    }

    private companion object {
        const val SECTION = "messageDialog"
    }
}
