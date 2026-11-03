@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.STTTabView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The STT tab section of the STT settings dialog: every control writes the tab's own view, and only it. */
class STTTabViewFieldsTest {

    private fun dialog(view: STTTabView = STTTabView(), block: ComposeUiTest.(get: () -> STTTabView) -> Unit) =
        runComposeUiTest {
            var current = AppSettings(sttSettings = STTSettings(tab = view))
            setContent {
                MaterialTheme {
                    var state by remember { mutableStateOf(current) }
                    STTSettingsDialogContent(
                        appSettings = state,
                        onSettingsChange = { change ->
                            state = change(state)
                            current = state
                        },
                        onDismiss = {},
                    )
                }
            }
            waitForIdle()
            block { current.sttSettings.tab }
        }

    /** Opens the dropdown showing [shown] and picks [option] from its menu. */
    private fun ComposeUiTest.pick(shown: String, option: String) {
        onNodeWithText(shown).performClick()
        waitForIdle()
        onNode(hasText(option) and hasClickAction()).performClick()
        waitForIdle()
    }

    @Test
    fun `what the tab shows is picked from its dropdown`() = dialog { get ->
        pick("Transcription and translation", "Translation")
        assertEquals(STTTabView.SHOWS_TRANSLATION, get().shows)
        pick("Translation", "Transcription")
        assertEquals(STTTabView.SHOWS_TRANSCRIPTION, get().shows)
    }

    @Test
    fun `the columns can go one under the other`() = dialog { get ->
        pick("Side by side", "One under the other")
        assertEquals(STTTabView.LAYOUT_STACKED, get().layout)
    }

    @Test
    fun `lines kept and text size are typed in`() = dialog { get ->
        val fields = onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("12")
        fields[1].performTextReplacement("22")
        waitForIdle()
        assertEquals(12, get().maxSegments)
        assertEquals(22, get().fontSize)
    }

    @Test
    fun `each switch turns its own part off`() = dialog { get ->
        onNode(isToggleable() and hasText("Show the line still being spoken")).performClick()
        waitForIdle()
        assertFalse(get().showInProgress)
        onNode(isToggleable() and hasText("Show the translation still being made")).performClick()
        waitForIdle()
        assertFalse(get().showTranslationInProgress)
        onNode(isToggleable() and hasText("Highlight words")).performClick()
        waitForIdle()
        assertFalse(get().wordHighlighting)
        val expected = STTTabView(showInProgress = false, showTranslationInProgress = false, wordHighlighting = false)
        assertEquals(expected, get())
    }

    @Test
    fun `a value this build does not know shows as the first choice`() =
        dialog(STTTabView(shows = "something-new")) { _ ->
            onNodeWithText("Transcription and translation").assertExists()
        }
}
