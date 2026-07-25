package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lower-third settings tab exposes the streaming (browser-source) window margins as four number
 * fields writing back into [AppSettings.streamingSettings]. These drive each field and assert the
 * margin it produces — not just that the tab renders, which never runs the onValueChange lambdas.
 */
@OptIn(ExperimentalTestApi::class)
class LowerThirdSettingsTabTest {

    private fun runTab(block: ComposeUiTest.(get: () -> AppSettings) -> Unit) = runComposeUiTest {
        var current = AppSettings()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                LowerThirdSettingsTab(
                    settings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                )
            }
        }
        block { current }
    }

    @Test
    fun `the tab shows the Lottie files section`() = runTab {
        onNodeWithText("Lottie Files", substring = true)
            .assertExists("the Lottie files section must render when the tab opens")
    }

    @Test
    fun `each streaming-window margin field writes its value back`() = runTab { get ->
        // The four number fields, in order, are the left/top/right/bottom window margins.
        onAllNodes(hasSetTextAction())[0].performTextReplacement("11")
        onAllNodes(hasSetTextAction())[1].performTextReplacement("22")
        onAllNodes(hasSetTextAction())[2].performTextReplacement("33")
        onAllNodes(hasSetTextAction())[3].performTextReplacement("44")

        val s = get().streamingSettings
        assertEquals(11, s.windowLeft, "first field is the left margin")
        assertEquals(22, s.windowTop, "second field is the top margin")
        assertEquals(33, s.windowRight, "third field is the right margin")
        assertEquals(44, s.windowBottom, "fourth field is the bottom margin")
    }
}
