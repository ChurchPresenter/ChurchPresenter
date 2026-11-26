@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class STTEmptyStatesTest {

    private fun androidx.compose.ui.test.ComposeUiTest.textButtonShown(label: String) =
        onAllNodes(hasText(label)).fetchSemanticsNodes(false).isNotEmpty()

    @Test
    fun `Connect in the empty state connects to the address above and saves it`() = sttTab { stt, _, reports ->
        assertTrue(showsExactly("Live captions show up here once ChurchPresenter connects to your STT server."))
        onNodeWithText("Connect").performClick()

        assertTrue(stt.connecting.value)
        assertEquals(SILENT_STT_URL, reports.settingsAfterChange?.sttSettings?.serverUrl)
    }

    @Test
    fun `while connecting the empty state names the server and holds its button`() =
        sttTab(seed = { applyConnecting() }) { _, _, _ ->
            assertTrue(showsExactly("Connecting to $SILENT_STT_URL…"), renderedText().toString())
            assertTrue(textButtonShown("Connecting…"))
            assertFalse(textButtonShown("Connect"))
        }

    @Test
    fun `with no address typed there is nothing to connect to`() =
        sttTab(settings = STTSettings(serverUrl = "")) { _, _, _ ->
            assertTrue(showsExactly("Not connected to a speech-to-text server"))
            assertFalse(textButtonShown("Connect"))
        }

    @Test
    fun `a server that cannot be reached is named, and Try again tries it`() =
        sttTab(seed = { applyConnectError() }) { stt, _, _ ->
            assertTrue(showsExactly("Can’t reach ${serverName(SILENT_STT_URL)}"), renderedText().toString())
            assertTrue(showsExactly("Check that the STT server is running and the address is right."))

            onNodeWithText("Try again").performClick()
            assertTrue(stt.connecting.value)
        }

    @Test
    fun `connected with nothing said yet, the tab is listening`() =
        sttTab(seed = { applyConnected() }) { _, _, _ ->
            assertTrue(showsExactly("Listening…"), renderedText().toString())
            assertTrue(showsExactly("Start speaking and the words appear here."))
        }

    @Test
    fun `a server is named without its scheme`() {
        assertEquals("localhost:80", serverName("http://localhost:80/"))
        assertEquals("example.org", serverName("https://example.org"))
        assertEquals("http://", serverName("http://"))
    }

    @Test
    fun `only an address with a host can be connected to`() {
        assertTrue(isConnectable("localhost:80"))
        assertTrue(isConnectable("http://10.0.0.2:5000"))
        assertFalse(isConnectable("http://"))
        assertFalse(isConnectable("https://"))
        assertFalse(isConnectable(" "))
    }
}
