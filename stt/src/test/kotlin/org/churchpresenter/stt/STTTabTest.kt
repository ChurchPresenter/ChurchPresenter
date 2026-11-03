@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.stt

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.STTTabView
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly

/**
 * What the STT tab shows and what its controls do.
 *
 * The tab is a connection row over a live-caption preview, and almost everything it draws is decided
 * by the four connection flags plus the tab's own view, `STTTabView`. Those states are reached through [STTManager]'s
 * own socket transitions (`applyConnected` and friends) rather than through a socket — see
 * `STTTabTestSupport.kt`.
 *
 * The settings dialog the Tune button opens is the app's, drawn into the tab's `settingsDialog`
 * slot; its body is covered in the app by `STTSettingsContentTest` / `STTSettingsDialogContentTest`.
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

    /**
     * Seeded through `applyConnecting` rather than by clicking Connect and hoping to look before the
     * attempt lands. Clicking raced the connection: on an idle machine the attempt failed first and
     * the tab already read "Can't reach STT server", so this failed intermittently and in isolation.
     * The click → CONNECTING transition is `STTManager.connect`'s own first act; what this test is
     * for is what the *tab* renders while that state is up.
     */
    @Test
    fun `while connecting the tab says so and the field is locked`() {
        sttTab(seed = { applyConnecting() }) { _, _, _ ->
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
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(showInProgress = true)),
            seed = { applyConnected(); transcribeInProgress("half a sen") },
        ) { _, _, _ ->
            assertFalse(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertTrue(showsExactly("half a sen"))
        }
    }

    @Test
    fun `the in-progress phrase is hidden when the setting is off`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(showInProgress = false)),
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
        sttTab(seed = { live("a caption") }) { _, captionsLive, reports ->
            sttButton(STTLabel.GO_LIVE).assertIsEnabled()

            sttButton(STTLabel.GO_LIVE).performClick()

            assertEquals(listOf(Presenting.STT), reports.presenting)

            // The tab dims Go Live off what is on air, not off its own click.
            captionsLive.value = true
            waitForIdle()
            sttButton(STTLabel.GO_LIVE).assertIsNotEnabled()
        }
    }

    // ── Display modes ───────────────────────────────────────────────────────────────────────────

    // Each output picks transcription, translation or both on its own, so the tab always shows both.

    @Test
    fun `every display mode draws both columns side by side`() {
        listOf("transcribe", "translate", "both").forEach { mode ->
            sttTab(
                settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = mode),
                seed = { live("spoken"); translate("translated") },
            ) { _, _, _ ->
                assertTrue(showsExactly(STTLabel.TRANSCRIPTION), "$mode: ${renderedText()}")
                assertTrue(showsExactly(STTLabel.TRANSLATION), mode)
                assertTrue(showsExactly("spoken"), mode)
                assertTrue(showsExactly("translated"), mode)
            }
        }
    }

    @Test
    fun `a transcription alone is shown before any translation arrives`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, displayMode = "translate"),
            seed = { live("spoken but not translated") },
        ) { _, _, _ ->
            assertFalse(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertTrue(showsExactly("spoken but not translated"))
            assertTrue(showsExactly(STTLabel.TRANSLATION))
        }
    }

    @Test
    fun `an in-progress translation is shown or hidden by its own setting`() {
        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                tab = STTTabView(shows = "translate", showTranslationInProgress = true),
            ),
            seed = { applyConnected(); translateInProgress("halfway trans") },
        ) { _, _, _ ->
            assertTrue(showsExactly("halfway trans"), renderedText().toString())
        }

        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                tab = STTTabView(shows = "translate", showTranslationInProgress = false),
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
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(maxSegments = 2)),
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
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(maxSegments = 0)),
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
                tab = STTTabView(shows = "translate", maxSegments = 1),
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
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(wordHighlighting = true)),
            seed = { live("praise the Lord"); highlight("Lord" to "#ff0000") },
        ) { _, _, _ ->
            assertTrue(showsExactly("praise the Lord"), renderedText().toString())
        }
    }

    @Test
    fun `a caption with a highlight and the label both render`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(wordHighlighting = true)),
            seed = { live("grace"); highlight("grace" to "#00ff00") },
        ) { _, _, _ ->
            // The column header and the caption are separate nodes, not one merged string.
            onNode(hasText(STTLabel.TRANSCRIPTION)).assertExists()
            onNode(hasText("grace")).assertExists()
        }
    }

    // ── The tab's own view ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab can show only the transcription`() {
        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                tab = STTTabView(shows = STTTabView.SHOWS_TRANSCRIPTION),
            ),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly("spoken"), renderedText().toString())
            assertFalse(showsExactly(STTLabel.TRANSLATION))
            assertFalse(showsExactly("translated"))
        }
    }

    @Test
    fun `a tab showing only the translation waits while only a transcription has arrived`() {
        sttTab(
            settings = STTSettings(
                serverUrl = SILENT_STT_URL,
                tab = STTTabView(shows = STTTabView.SHOWS_TRANSLATION),
            ),
            seed = { live("spoken but not translated") },
        ) { _, _, _ ->
            assertTrue(showsExactly(STTLabel.WAITING), renderedText().toString())
            assertFalse(showsExactly("spoken but not translated"))
        }
    }

    @Test
    fun `one under the other puts the translation below the transcription`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(layout = STTTabView.LAYOUT_STACKED)),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            val spoken = onNode(hasText("spoken")).fetchSemanticsNode().boundsInRoot
            val translated = onNode(hasText("translated")).fetchSemanticsNode().boundsInRoot
            assertTrue(translated.top >= spoken.bottom, "translation below: $spoken / $translated")
            assertEquals(spoken.left, translated.left, "and in the same column")
        }
    }

    @Test
    fun `side by side puts the translation beside the transcription`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL),
            seed = { live("spoken"); translate("translated") },
        ) { _, _, _ ->
            val spoken = onNode(hasText("spoken")).fetchSemanticsNode().boundsInRoot
            val translated = onNode(hasText("translated")).fetchSemanticsNode().boundsInRoot
            assertTrue(translated.left >= spoken.right, "translation to the right: $spoken / $translated")
        }
    }

    @Test
    fun `a larger text size draws taller lines`() {
        fun lineHeight(size: Int): Float {
            var height = 0f
            sttTab(
                settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(fontSize = size)),
                seed = { live("spoken") },
            ) { _, _, _ ->
                height = onNode(hasText("spoken")).fetchSemanticsNode().boundsInRoot.height
            }
            return height
        }
        assertTrue(lineHeight(30) > lineHeight(12))
    }

    @Test
    fun `with highlighting off a highlighted caption still renders`() {
        sttTab(
            settings = STTSettings(serverUrl = SILENT_STT_URL, tab = STTTabView(wordHighlighting = false)),
            seed = { live("praise the Lord"); highlight("Lord" to "#ff0000") },
        ) { _, _, _ ->
            assertTrue(showsExactly("praise the Lord"), renderedText().toString())
        }
    }
}
