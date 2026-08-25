@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * What the STT tab shows and what its controls do.
 *
 * The tab is a connection row over a live-caption preview, and almost everything it draws is decided
 * by the four connection flags plus `displayMode`. Those states are reached through [STTManager]'s
 * own socket transitions (`applyConnected` and friends) rather than through a socket — see
 * `STTTabTestSupport.kt`.
 *
 * Not covered here: the settings dialog the Tune button opens is a real `DialogWindow`, which cannot
 * be composed headless; its body is covered by `STTSettingsContentTest` /
 * `STTSettingsDialogContentTest` instead, so only the click that sets the flag is untested.
 */
class STTTabTest {

    // ── Disconnected ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `before connecting the tab explains how to connect`() {
        sttTab { _, _, _ ->
            assertTrue(showsExactly(STTLabel.NOT_CONNECTED), renderedText().toString())
            assertTrue(showsExactly(STTLabel.LIVE_PREVIEW))
            // Neither caption column exists until there is a connection.
            assertFalse(showsExactly(STTLabel.TRANSCRIPTION))
            assertFalse(showsExactly(STTLabel.WAITING))
        }
    }

    @Test
    fun `the url field is seeded from settings and Connect offers to use it`() {
        sttTab(settings = STTSettings(serverUrl = "http://stt.example:9000")) { _, _, _ ->
            assertEquals("http://stt.example:9000", urlFieldText())
            sttButton(STTLabel.CONNECT).assertIsEnabled()
            assertFalse(hasSttButton(STTLabel.DISCONNECT))
        }
    }

    @Test
    fun `an empty url field disables Connect and hides its clear button`() {
        sttTab { _, _, _ ->
            assertTrue(hasSttButton(STTLabel.CLEAR))

            urlField().performTextClearance()

            sttButton(STTLabel.CONNECT).assertIsNotEnabled()
            assertFalse(hasSttButton(STTLabel.CLEAR), "clear icon should go with the text")
        }
    }

    @Test
    fun `the clear button empties the url field`() {
        sttTab { _, _, _ ->
            sttButton(STTLabel.CLEAR).performClick()

            assertEquals("", urlFieldText())
            sttButton(STTLabel.CONNECT).assertIsNotEnabled()
        }
    }

    // ── Connecting ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Connect persists the url and starts connecting`() {
        sttTab { stt, _, reports ->
            sttButton(STTLabel.CONNECT).performClick()

            // connect() sets `connecting` before it launches anything, so this needs no waiting.
            assertTrue(stt.connecting.value, "clicking Connect should start a connection attempt")
            assertEquals(1, reports.settingsChanges)
            assertEquals(
                SILENT_STT_URL,
                reports.settingsAfterChange?.sttSettings?.serverUrl,
                "the url that was connected to should be the one that gets saved",
            )
        }
    }

    @Test
    fun `a url typed without a scheme is saved with http prefixed`() {
        // Host and port come from the silent loopback endpoint like every other Connect here: the
        // click really does start an attempt, so the target must not be somewhere that takes a route
        // timeout to fail.
        val hostAndPort = SILENT_STT_URL.removePrefix("http://")

        sttTab { _, _, reports ->
            urlField().performTextClearance()
            urlField().performTextInput(hostAndPort)

            sttButton(STTLabel.CONNECT).performClick()

            assertEquals("http://$hostAndPort", reports.settingsAfterChange?.sttSettings?.serverUrl)
        }
    }

    @Test
    fun `an https url is left alone`() {
        val httpsUrl = SILENT_STT_URL.replace("http://", "https://")

        sttTab(settings = STTSettings(serverUrl = httpsUrl)) { _, _, reports ->
            sttButton(STTLabel.CONNECT).performClick()

            assertEquals(httpsUrl, reports.settingsAfterChange?.sttSettings?.serverUrl)
        }
    }

    @Test
    fun `while connecting the tab says so and the field is locked`() {
        sttTab { _, _, _ ->
            sttButton(STTLabel.CONNECT).performClick()

            assertTrue(showsExactly(STTLabel.CONNECTING), renderedText().toString())
            assertFalse(urlFieldIsEditable(), "the url can't be edited mid-attempt")
            assertFalse(hasSttButton(STTLabel.CLEAR))
        }
    }

    @Test
    fun `an unreachable server is named as unreachable`() {
        sttTab(seed = { applyConnectError() }) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.UNREACHABLE), renderedText().toString())
            // Still offering to connect, not to disconnect.
            assertTrue(hasSttButton(STTLabel.CONNECT))
        }
    }

    @Test
    fun `an unexpected drop is named as reconnecting, and takes precedence over unreachable`() {
        sttTab(seed = { applyConnectError(); applyDisconnected(reason = "transport close") }) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.RECONNECTING), renderedText().toString())
            assertFalse(showsExactly(STTLabel.UNREACHABLE))
        }
    }

    // ── Connected ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `once connected the tab waits for the first caption`() {
        sttTab(seed = { applyConnected() }) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertFalse(showsExactly(STTLabel.NOT_CONNECTED))
            assertTrue(hasSttButton(STTLabel.DISCONNECT))
            assertFalse(hasSttButton(STTLabel.CONNECT))
        }
    }

    @Test
    fun `Disconnect drops the connection and the tab goes back to explaining itself`() {
        sttTab(seed = { live("a caption") }) { stt, _, _ ->
            sttButton(STTLabel.DISCONNECT).performClick()

            assertFalse(stt.connected.value)
            assertTrue(showsExactly(STTLabel.NOT_CONNECTED), renderedText().toString())
            assertTrue(hasSttButton(STTLabel.CONNECT))
        }
    }

    @Test
    fun `captions replace the waiting message`() {
        sttTab(seed = { live("first thing said", "second thing said") }) { _, _, _ ->
            assertFalse(showsExactly(STTLabel.WAITING))
            assertTrue(showsExactly(STTLabel.TRANSCRIPTION), renderedText().toString())
            assertTrue(showsExactly("first thing said"))
            assertTrue(showsExactly("second thing said"))
        }
    }

    @Test
    fun `an in-progress phrase alone counts as content`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, showInProgress = true),
            seed = { applyConnected(); transcribeInProgress("half a sen") },
        ) { _, _, _ ->
            assertFalse(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertTrue(showsExactly("half a sen"))
        }
    }

    @Test
    fun `the in-progress phrase is hidden when the setting is off`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, showInProgress = false),
            seed = { live("said already"); transcribeInProgress("half a sen") },
        ) { _, _, _ ->
            assertTrue(showsExactly("said already"))
            assertFalse(showsExactly("half a sen"), renderedText().toString())
        }
    }

    // ── Go Live ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Go Live is only offered once connected`() {
        sttTab { _, _, _ ->
            sttButton(STTLabel.GO_LIVE).assertIsNotEnabled()
        }
    }

    @Test
    fun `Go Live asks for STT and then stops offering`() {
        sttTab(seed = { live("a caption") }) { _, output, _ ->
            sttButton(STTLabel.GO_LIVE).assertIsEnabled()

            sttButton(STTLabel.GO_LIVE).performClick()

            assertEquals(1, output.goLiveCalls)

            // The tab dims Go Live off what the outputs report, not off its own click — so the
            // button goes disabled because `isLive` turned true, not because it was pressed.
            waitForIdle()
            sttButton(STTLabel.GO_LIVE).assertIsNotEnabled()
        }
    }

    // ── Display modes ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `transcribe mode draws only the transcription column`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = "transcribe"),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.TRANSCRIPTION), renderedText().toString())
            assertFalse(showsExactly(STTLabel.TRANSLATION))
            assertTrue(showsExactly("spoken"))
            assertFalse(showsExactly("translated"))
        }
    }

    @Test
    fun `translate mode draws only the translation column`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = "translate"),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.TRANSLATION), renderedText().toString())
            assertFalse(showsExactly(STTLabel.TRANSCRIPTION))
            assertTrue(showsExactly("translated"))
            assertFalse(showsExactly("spoken"))
        }
    }

    @Test
    fun `both mode draws the two columns side by side`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = "both"),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.TRANSCRIPTION), renderedText().toString())
            assertTrue(showsExactly(STTLabel.TRANSLATION))
            assertTrue(showsExactly("spoken"))
            assertTrue(showsExactly("translated"))
        }
    }

    @Test
    fun `translate mode still waits when only a transcription has arrived`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = "translate"),
            seed = { live("spoken but not translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertFalse(showsExactly("spoken but not translated"))
        }
    }

    @Test
    fun `an in-progress translation is shown or hidden by its own setting`() {
        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                displayMode = "translate",
                showTranslationInProgress = true,
            ),
            seed = { applyConnected(); translateInProgress("halfway trans") },
        ) { _, _, _ ->
            assertTrue(showsExactly("halfway trans"), renderedText().toString())
        }

        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                displayMode = "translate",
                showTranslationInProgress = false,
            ),
            seed = { applyConnected(); translate("done"); translateInProgress("halfway trans") },
        ) { _, _, _ ->
            assertTrue(showsExactly("done"))
            assertFalse(showsExactly("halfway trans"), renderedText().toString())
        }
    }

    // ── Segment cap ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `only the last maxSegments captions are previewed`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, maxSegments = 2),
            seed = { live("oldest", "middle", "newest") },
        ) { _, _, _ ->
            assertFalse(showsExactly("oldest"), renderedText().toString())
            assertTrue(showsExactly("middle"))
            assertTrue(showsExactly("newest"))
        }
    }

    @Test
    fun `a zero cap means show everything`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, maxSegments = 0),
            seed = { live("oldest", "middle", "newest") },
        ) { _, _, _ ->
            assertTrue(showsExactly("oldest"), renderedText().toString())
            assertTrue(showsExactly("middle"))
            assertTrue(showsExactly("newest"))
        }
    }

    @Test
    fun `the cap applies to the translation column too`() {
        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                displayMode = "translate",
                maxSegments = 1,
            ),
            seed = { applyConnected(); translate("older line", "newest line") },
        ) { _, _, _ ->
            assertFalse(showsExactly("older line"), renderedText().toString())
            assertTrue(showsExactly("newest line"))
        }
    }

    // ── Highlighting, as the tab wires it up ────────────────────────────────────────────────────

    @Test
    fun `highlighted captions are still rendered as their own text`() {
        // The colouring itself is asserted in STTHighlightingTest; what matters here is that turning
        // it on doesn't change, split or drop the caption the tab draws.
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, showWordHighlighting = true),
            seed = { live("praise the Lord"); highlight("Lord" to "#ff0000") },
        ) { _, _, _ ->
            assertTrue(showsExactly("praise the Lord"), renderedText().toString())
        }
    }

    @Test
    fun `a caption with a highlight and the label both render`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, showWordHighlighting = true),
            seed = { live("grace"); highlight("grace" to "#00ff00") },
        ) { _, _, _ ->
            // The column header and the caption are separate nodes, not one merged string.
            onNode(hasText(STTLabel.TRANSCRIPTION)).assertExists()
            onNode(hasText("grace")).assertExists()
        }
    }
}
