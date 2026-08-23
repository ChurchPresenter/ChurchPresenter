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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.ui.SENTINEL_FONT
import org.churchpresenter.ui.Utils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.pickFont
import org.churchpresenter.ui.recolor
import org.churchpresenter.ui.retypeNumberField
import org.churchpresenter.ui.uniquelyNamedFont

class STTSettingsDialogContentTest {

    @OptIn(ExperimentalTestApi::class)
    private fun sttDialog(
        sttSettings: STTSettings = STTSettings(),
        bibleEngineSettings: BibleEngineSettings = BibleEngineSettings(),
        availableFonts: List<String> = Utils.getAvailableSystemFonts(),
        block: ComposeUiTest.(get: () -> AppSettings, dismissCount: () -> Int) -> Unit,
    ) = runComposeUiTest {
        var current = AppSettings(sttSettings = sttSettings, bibleEngineSettings = bibleEngineSettings)
        var dismissCount = 0
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(current) }
                STTSettingsDialogContent(
                    appSettings = state,
                    onSettingsChange = { transform -> state = transform(state); current = state },
                    onDismiss = { dismissCount++ },
                    availableFonts = availableFonts,
                )
            }
        }
        block({ current }, { dismissCount })
    }

    // ── Scripture detection (Bible Lookup Engine) ───────────────────────────────────────────────────

    @Test
    fun `the detect scripture checkbox toggles the engine and reveals or hides the help-dev row`() =
        sttDialog { get, _ ->
            assertEquals(true, get().bibleEngineSettings.enabled, "on by default")
            onNodeWithText("Help Dev", substring = true).assertIsDisplayed()

            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertEquals(false, get().bibleEngineSettings.enabled)
            onNodeWithText("Help Dev", substring = true).assertDoesNotExist()

            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertEquals(true, get().bibleEngineSettings.enabled)
            onNodeWithText("Help Dev", substring = true).assertIsDisplayed()
        }

    @Test
    fun `the help-dev checkbox toggles independently while the engine stays enabled`() = sttDialog { get, _ ->
        onAllNodes(isToggleable())[1].performClick()
        waitForIdle()
        assertEquals(true, get().bibleEngineSettings.helpDevMode)
        assertEquals(true, get().bibleEngineSettings.enabled, "the engine checkbox must be untouched")
    }

    @Test
    @Suppress("MaxLineLength")
    fun `the host and port fields appear only when the engine is enabled and not local, and the port field ignores non-numeric text`() =
        sttDialog(bibleEngineSettings = BibleEngineSettings(enabled = true, runLocal = false)) { get, _ ->
            onNode(hasSetTextAction() and hasText("localhost")).performTextReplacement("192.168.1.5")
            waitForIdle()
            assertEquals("192.168.1.5", get().bibleEngineSettings.host)

            onNode(hasSetTextAction() and hasText("8766")).performTextReplacement("9000")
            waitForIdle()
            assertEquals(9000, get().bibleEngineSettings.port)

            onNode(hasSetTextAction() and hasText("9000")).performTextReplacement("abc")
            waitForIdle()
            assertEquals(9000, get().bibleEngineSettings.port, "non-numeric text must be ignored, not stored as 0")
        }

    @Test
    fun `with the engine disabled, the help-dev row and host or port fields are both hidden`() = sttDialog(
        bibleEngineSettings = BibleEngineSettings(enabled = false, runLocal = false),
    ) { _, _ ->
        onNodeWithText("Help Dev", substring = true).assertDoesNotExist()
        onNodeWithText("localhost").assertDoesNotExist()
    }

    @Test
    fun `with the engine enabled but running locally, the host and port fields stay hidden`() = sttDialog { _, _ ->
        onNodeWithText("localhost").assertDoesNotExist()
    }

    // ── Display mode and layout ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the display mode dropdown stores each option and only reveals Layout for Both`() = sttDialog { get, _ ->
        onNodeWithText("Stacked").assertDoesNotExist()

        chooseFromDropdown(currentValue = "Transcription Only", target = "Translation Only")
        assertEquals("translate", get().sttSettings.displayMode)
        onNodeWithText("Stacked").assertDoesNotExist()

        chooseFromDropdown(currentValue = "Translation Only", target = "Both")
        assertEquals("both", get().sttSettings.displayMode)
        onNodeWithText("Stacked").assertIsDisplayed()
    }

    @Test
    fun `the layout dropdown stores each option`() =
        sttDialog(sttSettings = STTSettings(displayMode = "both")) { get, _ ->
            chooseFromDropdown(currentValue = "Stacked", target = "Stacked (Inverse)")
            assertEquals("stacked_inverse", get().sttSettings.layout)

            chooseFromDropdown(currentValue = "Stacked (Inverse)", target = "Side by Side")
            assertEquals("side_by_side", get().sttSettings.layout)

            chooseFromDropdown(currentValue = "Side by Side", target = "Side by Side (Inverse)")
            assertEquals("side_by_side_inverse", get().sttSettings.layout)
        }

    // ── Numeric fields ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `max segments stores a value within range and rejects one outside it`() = sttDialog { get, _ ->
        retypeNumberField(showing = 5, to = 20)
        assertEquals(20, get().sttSettings.maxSegments)
        retypeNumberField(showing = 20, to = 500)
        assertEquals(20, get().sttSettings.maxSegments, "500 is above the 0..100 range")
        retypeNumberField(showing = 500, to = 100)
        assertEquals(100, get().sttSettings.maxSegments, "100 is the top of the range and is accepted")
    }

    @Test
    fun `max lines stores a value within range and rejects one outside it`() = sttDialog { get, _ ->
        retypeNumberField(showing = 3, to = 10)
        assertEquals(10, get().sttSettings.maxLines)
        retypeNumberField(showing = 10, to = 90)
        assertEquals(10, get().sttSettings.maxLines, "90 is above the 0..50 range")
        retypeNumberField(showing = 90, to = 50)
        assertEquals(50, get().sttSettings.maxLines, "50 is the top of the range and is accepted")
    }

    @Test
    fun `line spacing stores a value within range and rejects one outside it`() = sttDialog { get, _ ->
        retypeNumberField(showing = 130, to = 150)
        assertEquals(150, get().sttSettings.lineSpacing)
        retypeNumberField(showing = 150, to = 50)
        assertEquals(150, get().sttSettings.lineSpacing, "50 is below the 80..300 range")
        retypeNumberField(showing = 50, to = 80)
        assertEquals(80, get().sttSettings.lineSpacing, "80 is the bottom of the range and is accepted")
    }

    // ── Preview-behaviour checkboxes ────────────────────────────────────────────────────────────────

    @Test
    fun `word highlighting in-progress and translation in-progress toggle independently`() = sttDialog { get, _ ->
        onAllNodes(isToggleable())[2].performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.showWordHighlighting)
        assertEquals(false, get().sttSettings.showInProgress)

        onAllNodes(isToggleable())[3].performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.showInProgress)
        assertEquals(true, get().sttSettings.showWordHighlighting, "word highlighting must stay on")

        onAllNodes(isToggleable())[4].performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.showTranslationInProgress)
        assertEquals(true, get().sttSettings.showInProgress, "in-progress must stay on")
    }

    // ── Drip feed ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the drip feed checkbox toggles and the speed field stores a value within its range`() = sttDialog { get, _ ->
        assertEquals(true, get().sttSettings.dripFeedEnabled, "on by default")
        onAllNodes(isToggleable())[5].performClick()
        waitForIdle()
        assertEquals(false, get().sttSettings.dripFeedEnabled)

        retypeNumberField(showing = 25, to = 100)
        assertEquals(100, get().sttSettings.dripFeedSpeed)
        retypeNumberField(showing = 100, to = 2000)
        assertEquals(100, get().sttSettings.dripFeedSpeed, "2000 is above the 1..1000 range")
        retypeNumberField(showing = 2000, to = 1000)
        assertEquals(1000, get().sttSettings.dripFeedSpeed, "1000 is the top of the range and is accepted")
    }

    // ── Colour fields ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the text colour field stores the confirmed hex`() =
        sttDialog(sttSettings = STTSettings(translationTextColor = "#123123")) { get, _ ->
            recolor(fromHex = "#FFFFFF", toHex = "#ABCDEF")
            assertTrue(get().sttSettings.textColor.equals("#ABCDEF", ignoreCase = true))
            assertEquals("#123123", get().sttSettings.translationTextColor, "must be untouched")
        }

    @Test
    fun `the translation colour field stores the confirmed hex`() =
        sttDialog(sttSettings = STTSettings(textColor = "#123123")) { get, _ ->
            recolor(fromHex = "#FFFFFF", toHex = "#654321")
            assertTrue(get().sttSettings.translationTextColor.equals("#654321", ignoreCase = true))
            assertEquals("#123123", get().sttSettings.textColor, "must be untouched")
        }

    @Test
    fun `the background colour field stores the confirmed hex`() = sttDialog { get, _ ->
        recolor(fromHex = "transparent", toHex = "#334455")
        assertTrue(get().sttSettings.backgroundColor.equals("#334455", ignoreCase = true))
    }

    // ── Bold / italic / underline / shadow ──────────────────────────────────────────────────────────

    @Test
    fun `the style buttons toggle bold italic and underline independently`() = sttDialog { get, _ ->
        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.bold)
        assertEquals(false, get().sttSettings.italic)

        onNodeWithText("I").performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.italic)
        assertEquals(true, get().sttSettings.bold, "bold must stay on")

        onNodeWithText("U").performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.underline)
        assertEquals(true, get().sttSettings.bold, "bold must still stay on")

        onNodeWithText("B").performClick()
        waitForIdle()
        assertEquals(false, get().sttSettings.bold, "clicking again must clear it")
        assertEquals(true, get().sttSettings.italic, "italic must be untouched by clearing bold")
    }

    @Test
    fun `the shadow toggle reveals its detail row and clearing it hides the row again`() = sttDialog { get, _ ->
        assertEquals(false, get().sttSettings.shadow, "no shadow out of the box")
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)

        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(true, get().sttSettings.shadow)
        onAllNodesWithText("SIZE (%)").assertCountEquals(1)
        onAllNodesWithText("INTENSITY (%)").assertCountEquals(1)

        onNodeWithText("S").performClick()
        waitForIdle()
        assertEquals(false, get().sttSettings.shadow)
        onAllNodesWithText("SIZE (%)").assertCountEquals(0)
    }

    @Test
    fun `the shadow colour size and opacity fields store independently`() =
        sttDialog(
            sttSettings = STTSettings(shadow = true, shadowColor = "#654321", shadowSize = 120, shadowOpacity = 60),
        ) { get, _ ->
            recolor(fromHex = "#654321", toHex = "#0F0F0F")
            assertTrue(get().sttSettings.shadowColor.equals("#0F0F0F", ignoreCase = true))

            retypeNumberField(showing = 120, to = 200)
            assertEquals(200, get().sttSettings.shadowSize, "the typed size must be stored")
            assertEquals(60, get().sttSettings.shadowOpacity, "the opacity must be untouched")

            retypeNumberField(showing = 60, to = 90)
            assertEquals(90, get().sttSettings.shadowOpacity, "the typed opacity must be stored")
            assertEquals(200, get().sttSettings.shadowSize, "the size must be untouched")
        }

    // ── Font ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the font dropdown stores the picked family`() {
        val target = uniquelyNamedFont()
        sttDialog(sttSettings = STTSettings(fontType = SENTINEL_FONT)) { get, _ ->
            pickFont(showing = SENTINEL_FONT, to = target)
            assertEquals(target, get().sttSettings.fontType)
        }
    }

    @Test
    fun `the font size field stores a value within range and rejects one outside it`() = sttDialog { get, _ ->
        retypeNumberField(showing = 42, to = 72)
        assertEquals(72, get().sttSettings.fontSize)
        retypeNumberField(showing = 72, to = 400)
        assertEquals(72, get().sttSettings.fontSize, "400 is above the 8..200 range")
        retypeNumberField(showing = 400, to = 200)
        assertEquals(200, get().sttSettings.fontSize, "200 is the top of the range and is accepted")
    }

    // ── Position grid ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every position button stores its own constant`() = sttDialog { get, _ ->
        val positions = listOf(
            "TL" to Constants.TOP_LEFT,
            "TC" to Constants.TOP_CENTER,
            "TR" to Constants.TOP_RIGHT,
            "CL" to Constants.CENTER_LEFT,
            "C" to Constants.CENTER,
            "CR" to Constants.CENTER_RIGHT,
            "BL" to Constants.BOTTOM_LEFT,
            "BC" to Constants.BOTTOM_CENTER,
            "BR" to Constants.BOTTOM_RIGHT,
        )
        for ((label, constant) in positions) {
            onNodeWithText(label).performClick()
            waitForIdle()
            assertEquals(constant, get().sttSettings.position, "clicking $label must store $constant")
        }
    }

    // ── Opacity slider ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dragging the opacity slider to the far right stores 100`() = sttDialog { get, _ ->
        val reading = dragOpacitySliderToEnd(toRight = true)
        assertEquals(100, reading)
        assertEquals(100, get().sttSettings.backgroundOpacity)
    }

    @Test
    fun `dragging the opacity slider to the far left stores 0`() =
        sttDialog(sttSettings = STTSettings(backgroundOpacity = 100)) { get, _ ->
            val reading = dragOpacitySliderToEnd(toRight = false)
            assertEquals(0, reading)
            assertEquals(0, get().sttSettings.backgroundOpacity)
        }

    // ── Close ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the close button invokes onDismiss`() = sttDialog { _, dismissCount ->
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertEquals(1, dismissCount())
    }
}

// ── Locators local to this file ────────────────────────────────────────────────────────────────────

private fun ComposeUiTest.chooseFromDropdown(currentValue: String, target: String) {
    onNode(hasClickAction() and hasText(currentValue)).performClick()
    waitForIdle()
    onNode(hasClickAction() and hasText(target)).performClick()
    waitForIdle()
}

private fun ComposeUiTest.opacitySliderGeometry(): Pair<Rect, Rect> {
    val captionBounds = onNodeWithText("Opacity:").fetchSemanticsNode().boundsInRoot
    val readout = onAllNodes(hasText("%", substring = true) and !hasClickAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .map { it.boundsInRoot }
        .filter { it.left >= captionBounds.right && it.top < captionBounds.bottom && it.bottom > captionBounds.top }
        .minByOrNull { it.left }
        ?: error("no percentage readout found beside the opacity caption")
    return captionBounds to readout
}

private fun ComposeUiTest.opacityReading(): Int {
    val (_, readoutBounds) = opacitySliderGeometry()
    val node = onAllNodes(hasText("%", substring = true) and !hasClickAction())
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .first { it.boundsInRoot == readoutBounds }
    val text = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        ?: error("the opacity slider published no readout text")
    return text.removeSuffix("%").toIntOrNull() ?: error("could not parse a percentage out of \"$text\"")
}

private fun ComposeUiTest.dragOpacitySliderToEnd(toRight: Boolean): Int {
    val (captionBounds, readoutBounds) = opacitySliderGeometry()
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
    return opacityReading()
}
