@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.app.churchpresenter.dialogs.tabs.recolor
import org.churchpresenter.app.churchpresenter.dialogs.tabs.retypeNumberField
import org.churchpresenter.companionserver.TunnelStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QARemoteContentControlsTest {

    private companion object {
        const val SERVER = "http://192.168.1.50:8080"
        const val TUNNEL = "https://abc-def.trycloudflare.com"
    }

    @OptIn(ExperimentalTestApi::class)
    private fun qaRemoteTab(
        qaSettings: QASettings = QASettings(),
        serverUrl: String = SERVER,
        qaDisplayUrl: String = "",
        tunnelStatus: TunnelStatus = TunnelStatus.Idle,
        tunnelUrl: String = "",
        block: ComposeUiTest.(get: () -> QASettings, qaDisplayUrlChanges: () -> List<String>) -> Unit,
    ) = runComposeUiTest {
        var current = qaSettings
        val qaDisplayUrlChanges = mutableListOf<String>()
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(AppSettings(qaSettings = current)) }
                QARemoteContent(
                    serverUrl = serverUrl,
                    qaDisplayUrl = qaDisplayUrl,
                    onQaDisplayUrlChanged = { qaDisplayUrlChanges += it },
                    apiKeyEnabled = false,
                    apiKey = "",
                    tunnelStatus = tunnelStatus,
                    tunnelUrl = tunnelUrl,
                    onStartTunnel = {},
                    onStopTunnel = {},
                    qaSettings = state.qaSettings,
                    onSettingsChange = { transform -> state = transform(state); current = state.qaSettings },
                    availableFonts = emptyList(),
                    onDismiss = {},
                )
            }
        }
        block({ current }, { qaDisplayUrlChanges })
    }

    // ── Branches the other test's fixtures never hit ────────────────────────────────────────────────

    @Test
    fun `a display address that exactly equals the server also counts as local`() =
        qaRemoteTab(
            qaDisplayUrl = SERVER,
            tunnelUrl = TUNNEL,
            tunnelStatus = TunnelStatus.Connected(TUNNEL),
        ) { _, changes ->
            onNodeWithText("Public").performClick()
            waitForIdle()
            assertEquals(listOf(TUNNEL), changes(), "the Public button must still switch to the tunnel")
        }

    @Test
    fun `an admin address that cannot be built shows no QR image`() =
        qaRemoteTab(serverUrl = "", qaDisplayUrl = "https://mydisplay.example") { _, _ ->
            onNodeWithText("https://mydisplay.example/qa").assertIsDisplayed()
            onNodeWithContentDescription("Admin Panel").assertDoesNotExist()
        }

    @Test
    fun `with no address at all the server hint replaces every styling control`() =
        qaRemoteTab(serverUrl = "") { _, _ ->
            onNodeWithText("Start the companion server to enable Q&A").assertIsDisplayed()
            onNodeWithText("Display Styling").assertDoesNotExist()
            onNodeWithText("Position").assertDoesNotExist()
        }

    // ── Clamped-range fields ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a cooldown outside 0 to 600 is not stored`() = qaRemoteTab { get, _ ->
        retypeNumberField(showing = 30, to = 900)
        assertEquals(30, get().rateLimitCooldownSeconds, "900 is above the 0..600 range")
        retypeNumberField(showing = 900, to = 600)
        assertEquals(600, get().rateLimitCooldownSeconds, "600 is the top of the range and is accepted")
    }

    @Test
    fun `a font size outside 8 to 200 is not stored`() = qaRemoteTab { get, _ ->
        retypeNumberField(showing = 48, to = 400)
        assertEquals(48, get().fontSize, "400 is above the 8..200 range")
        retypeNumberField(showing = 400, to = 200)
        assertEquals(200, get().fontSize, "200 is the top of the range and is accepted")
    }

    // ── Style toggle and shadow-detail independence ─────────────────────────────────────────────────

    @Test
    fun `the style buttons toggle bold italic and underline independently`() = qaRemoteTab { get, _ ->
        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(true, get().bold)
        assertEquals(false, get().italic)

        onNodeWithText("I").performClick()
        waitForIdle()
        assertEquals(true, get().italic)
        assertEquals(true, get().bold, "bold must stay on")

        onNodeWithText("U").performClick()
        waitForIdle()
        assertEquals(true, get().underline)
        assertEquals(true, get().bold, "bold must still stay on")

        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(false, get().bold, "clicking again must clear it")
        assertEquals(true, get().italic, "italic must be untouched by clearing bold")
    }

    @Test
    fun `the shadow toggle reveals its detail row and clearing it hides the row again`() = qaRemoteTab { get, _ ->
        assertEquals(false, get().shadow, "no shadow out of the box")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)

        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(true, get().shadow, "clicking S must be stored")
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)

        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(false, get().shadow, "clicking S again must clear it")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
    }

    @Test
    fun `the shadow colour size and opacity fields store independently`() =
        qaRemoteTab(
            qaSettings = QASettings(shadow = true, shadowColor = "#654321", shadowSize = 120, shadowOpacity = 60),
        ) { get, _ ->
            recolor(fromHex = "#654321", toHex = "#0F0F0F")
            assertTrue(get().shadowColor.equals("#0F0F0F", ignoreCase = true))

            retypeNumberField(showing = 120, to = 200)
            assertEquals(200, get().shadowSize, "the typed size must be stored")
            assertEquals(60, get().shadowOpacity, "the opacity must be untouched")

            retypeNumberField(showing = 60, to = 90)
            assertEquals(90, get().shadowOpacity, "the typed opacity must be stored")
            assertEquals(200, get().shadowSize, "the size must be untouched")
        }

    // ── Opacity sliders ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dragging the QR opacity slider to zero leaves the background opacity alone`() = qaRemoteTab { get, _ ->
        val reading = dragOpacitySliderToEnd(ordinal = 0, toRight = false)
        assertEquals(0, reading)
        assertEquals(0, get().qrBackgroundOpacity, "dragging to the far left must store 0")
        assertEquals(100, get().backgroundOpacity, "the background opacity must be untouched")
    }

    @Test
    fun `dragging the background opacity slider to zero leaves the QR opacity alone`() = qaRemoteTab { get, _ ->
        val reading = dragOpacitySliderToEnd(ordinal = 1, toRight = false)
        assertEquals(0, reading)
        assertEquals(0, get().backgroundOpacity, "dragging to the far left must store 0")
        assertEquals(100, get().qrBackgroundOpacity, "the QR opacity must be untouched")
    }
}

// ── Locators local to this file ────────────────────────────────────────────────────────────────────

private fun ComposeUiTest.opacitySliderGeometry(ordinal: Int): Pair<Rect, Rect> {
    val captions = onAllNodesWithText("Opacity:").fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
    check(ordinal in captions.indices) {
        "wanted opacity slider #$ordinal but only ${captions.size} caption(s) exist"
    }
    val captionBounds = captions[ordinal]
    val readout = onAllNodes(hasText("%", substring = true) and !hasClickAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .filter { it.left >= captionBounds.right && it.top < captionBounds.bottom && it.bottom > captionBounds.top }
        .minByOrNull { it.left }
        ?: error("no percentage readout found beside opacity caption #$ordinal")
    return captionBounds to readout
}

private fun ComposeUiTest.opacityReading(ordinal: Int): Int {
    val (_, readoutBounds) = opacitySliderGeometry(ordinal)
    val node = onAllNodes(hasText("%", substring = true) and !hasClickAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first { it.boundsInRoot == readoutBounds }
    val text = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: error("opacity slider #$ordinal published no readout text")
    return text.removeSuffix("%").toIntOrNull() ?: error("could not parse a percentage out of \"$text\"")
}

private fun ComposeUiTest.dragOpacitySliderToEnd(ordinal: Int, toRight: Boolean): Int {
    val (captionBounds, readoutBounds) = opacitySliderGeometry(ordinal)
    val trackStart = captionBounds.right + 4f // Spacer(4.dp) between the caption and the slider
    val trackEnd = readoutBounds.left - 10f // SlimSlider's own Row(spacedBy(10.dp))
    val y = readoutBounds.center.y
    val from = (trackStart + trackEnd) / 2f
    val to = if (toRight) trackEnd + 60f else trackStart - 60f
    onRoot().performMouseInput {
        moveTo(Offset(from, y))
        press()
        moveTo(Offset(to, y))
        release()
    }
    waitForIdle()
    return opacityReading(ordinal)
}
