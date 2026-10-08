@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Go Live key on the STT tab: Enter on the tab root asks for captions, under the same condition
 * as the Go Live button — connected, and captions not already on air.
 */
class STTGoLiveKeyTest {

    private fun ComposeUiTest.pressEnter() {
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root puts the captions live once connected`() {
        sttTab(seed = { live("a caption") }) { _, _, reports ->
            pressEnter()

            assertEquals(listOf(Presenting.STT), reports.presenting)
        }
    }

    @Test
    fun `enter does nothing while not connected`() {
        sttTab { _, _, reports ->
            pressEnter()

            assertTrue(reports.presenting.isEmpty(), "nothing to go live with: ${reports.presenting}")
        }
    }

    @Test
    fun `enter does nothing once the captions are already on air`() {
        sttTab(seed = { live("a caption") }) { _, captionsLive, reports ->
            captionsLive.value = true
            waitForIdle()

            pressEnter()

            assertTrue(reports.presenting.isEmpty(), "already live: ${reports.presenting}")
        }
    }

    /**
     * The url field locks once a connection is up, so it only ever holds the keyboard while Go Live
     * is disabled anyway — the field cannot be paired with an enabled key here. The field-versus-root
     * rule itself is covered by `GoLiveKeyTest` in `:shared-ui`.
     */
    @Test
    fun `enter typed in the server url field does not go live`() {
        sttTab { _, _, reports ->
            urlField().requestFocus()

            urlField().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()

            assertTrue(reports.presenting.isEmpty(), "the field kept the key: ${reports.presenting}")
        }
    }
}
