@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.StreamingSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Settings → Companion → lower-third triggers: one row per lower third in the configured folder,
 * each button copying the URL Companion should call, and the ATEM key and takedown URLs beneath.
 */
class CompanionTriggersCardTest {

    private val folder: File = Files.createTempDirectory("cp-lt-triggers").toFile()
    private val server = "http://192.168.1.20:8765"

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun lottie(name: String) = File(folder, "$name.json").writeText("""{"v":"5.7.4","layers":[]}""")

    private fun settings(atemHost: String = "") = AppSettings(
        streamingSettings = StreamingSettings(lowerThirdFolder = folder.absolutePath),
        atemSettings = AtemSettings(host = atemHost),
    )

    private fun card(
        settings: AppSettings = settings(),
        isRunning: Boolean = true,
        serverUrl: String = server,
        body: ComposeUiTest.(copied: List<String>) -> Unit,
    ) = runComposeUiTest {
        val copied = mutableListOf<String>()
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CompanionTriggersCard(settings, isRunning, serverUrl, copyText = { copied += it })
            }
        }
        waitForIdle()
        body(copied)
    }

    private fun ComposeUiTest.press(label: String) {
        onNodeWithText(label).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `with the server off there are no URLs to copy`() = card(isRunning = false) {
        onNodeWithText("Start the server to get trigger URLs").assertExists()
        assertTrue(onAllNodesWithText("Hide Lower Third").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an empty folder says so, and still offers the takedown URLs`() = card { copied ->
        onNodeWithText("No lower thirds found in the configured folder").assertExists()

        press("Hide Lower Third")
        press("Clear Display")

        assertEquals(listOf(lowerThirdHideUrl(server), clearDisplayUrl(server)), copied)
    }

    @Test
    fun `each lower third copies its own go-live URLs, with and without the key`() {
        lottie("Pastor")
        File(folder, "notes.json").writeText("""{"not":"a lottie"}""")
        card { copied ->
            // The folder is scanned off the composition thread; wait for the scan, not for idle.
            waitUntil("the folder scan lists Pastor") { onAllNodesWithText("Pastor").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodesWithText("notes").fetchSemanticsNodes().isEmpty(), "only Lottie files are triggers")
            assertTrue(onAllNodesWithText("Still only").fetchSemanticsNodes().isEmpty(), "no ATEM, no media buttons")

            press("Go Live + Key")
            press("Go Live")

            assertEquals(
                listOf(
                    lowerThirdTriggerUrl(server, "Pastor", true, ""),
                    lowerThirdTriggerUrl(server, "Pastor", false, ""),
                ),
                copied,
            )
        }
    }

    @Test
    fun `with an ATEM configured each lower third also copies its still and clip URLs, and the key URLs`() {
        lottie("Welcome")
        val atem = settings(atemHost = "192.168.1.240")
        card(settings = atem) { copied ->
            waitUntil("the folder scan lists Welcome") { onAllNodesWithText("Welcome").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Clip + Key uploads the clip", substring = true).assertExists()

            listOf("Still + Key", "Still only", "Clip + Key", "Clip only", "Copy Key On", "Copy Key Off")
                .forEach { press(it) }

            val target = atemKeyTarget(atem.atemSettings)
            val keyType = atemKeyTypeParam(atem.atemSettings)
            assertEquals(
                listOf(
                    atemMediaUrl(server, "still", "Welcome", target, ""),
                    atemMediaUrl(server, "still", "Welcome", "", ""),
                    atemMediaUrl(server, "clip", "Welcome", target, ""),
                    atemMediaUrl(server, "clip", "Welcome", "", ""),
                    atemKeyUrl(server, on = true, keyTypeParam = keyType),
                    atemKeyUrl(server, on = false, keyTypeParam = keyType),
                ),
                copied,
            )
        }
    }
}
