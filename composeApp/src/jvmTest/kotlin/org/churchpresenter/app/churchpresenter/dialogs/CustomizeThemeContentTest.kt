@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.cpColorToHex
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.customColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Customize Theme window: every control, and what Apply, OK, Cancel and Reset each hand back.
 *
 * `CustomizeThemeDialog` opens a `DialogWindow`, which cannot be composed headless, so these drive
 * `CustomizeThemeContent` — the window's whole body. The window call, its size and the density it
 * reads are what remain uncovered; its size is held by `DialogViewportTest`.
 *
 * The colour picker's own OK sits after this window's OK in the tree, being the most recently opened
 * popup, so [confirmColorPickerWith] takes the last "OK" — the same collision `AddLabelContentTest`
 * records.
 */
class CustomizeThemeContentTest {

    private class Result {
        val applied = mutableListOf<ThemeCustomizationChoice>()
        var dismissed = 0
    }

    private val initial = ThemeCustomizationChoice(
        useCustomColors = true,
        accentHex = "#3F7FBF",
        dark = true,
        fontFamily = "",
        fontScale = 1f,
    )

    private fun content(
        start: ThemeCustomizationChoice = initial,
        block: ComposeUiTest.(Result) -> Unit,
    ) = runComposeUiTest {
        val result = Result()
        setContent {
            ChurchPresenterTheme(ThemeMode.DARK) {
                Box(Modifier.size(CUSTOMIZE_THEME_DIALOG_WIDTH, CUSTOMIZE_THEME_DIALOG_HEIGHT)) {
                    CustomizeThemeContent(
                        currentTheme = ThemeMode.OCEAN,
                        initial = start,
                        previewDensity = LocalDensity.current,
                        onApply = { result.applied += it },
                        onDismiss = { result.dismissed++ },
                    )
                }
            }
        }
        waitForIdle()
        block(result)
    }

    private fun ComposeUiTest.click(text: String) {
        onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openColorField(showingHex: String) {
        onAllNodes(hasClickAction() and hasText(showingHex)).onFirst().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.confirmColorPickerWith(hex: String) {
        onAllNodes(hasSetTextAction() and hasText("#", substring = true)).onLast().performTextReplacement(hex)
        waitForIdle()
        onAllNodes(hasText("OK") and hasClickAction()).onLast().performClick()
        waitForIdle()
    }

    // ── The buttons ─────────────────────────────────────────────────────────────

    @Test
    fun `apply hands back the draft and leaves the window open`() = content { result ->
        click("Light")
        click("Apply")

        assertEquals(listOf(initial.copy(dark = false)), result.applied)
        assertEquals(0, result.dismissed)
    }

    @Test
    fun `ok applies and closes`() = content { result ->
        click("OK")

        assertEquals(listOf(initial), result.applied)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `cancel closes without applying anything`() = content { result ->
        click("Large")
        click("Cancel")

        assertEquals(emptyList(), result.applied)
        assertEquals(1, result.dismissed)
    }

    @Test
    fun `reset puts every choice back to its default, keeping the switch`() = content(
        initial.copy(
            accentHex = "#AA3355",
            dark = false,
            colors = CustomThemeColors(warning = "#E0A020"),
            fontFamily = "Serif",
            fontScale = 1.3f,
        ),
    ) { result ->
        click("Reset to defaults")
        click("Apply")

        assertEquals(listOf(defaultChoice(useCustomColors = true)), result.applied)
    }

    // ── Colours ─────────────────────────────────────────────────────────────────

    @Test
    fun `turning custom colours off is handed back, and leaves the colours as they were`() = content { result ->
        onAllNodes(isToggleable()).onFirst().performClick()
        waitForIdle()
        // With the switch off the colour controls are inert: a pick does not reach the draft.
        openColorField("#3F7FBF")
        confirmColorPickerWith("#AA3355")
        click("Apply")

        assertEquals(listOf(initial.copy(useCustomColors = false)), result.applied)
    }

    @Test
    fun `a picked accent is handed back`() = content { result ->
        openColorField("#3F7FBF")
        confirmColorPickerWith("#AA3355")
        click("Apply")

        assertEquals("#AA3355", result.applied.single().accentHex)
    }

    @Test
    fun `every optional colour starts on auto, showing the colour it will paint with`() = content { _ ->
        assertEquals(7, onAllNodes(hasText("Auto")).fetchSemanticsNodes().size)
        // The error box shows the derived error colour, not a blank.
        val derived = cpColorToHex(customColorScheme(initial.toCustomization()).error)
        onAllNodes(hasText(derived)).onFirst().assertExists()
    }

    @Test
    fun `a colour picked in the grid is handed back on its own role`() = content { result ->
        openColorField(cpColorToHex(customColorScheme(initial.toCustomization()).error))
        confirmColorPickerWith("#D02040")
        click("Apply")

        assertEquals(CustomThemeColors(error = "#D02040"), result.applied.single().colors)
    }

    @Test
    fun `a set colour can go back to auto`() = content(initial.copy(colors = CustomThemeColors(warning = "#E0A020"))) {
            result ->
        assertEquals(6, onAllNodes(hasText("Auto")).fetchSemanticsNodes().size)
        onNodeWithText("Reset to auto").performClick()
        waitForIdle()
        click("Apply")

        assertEquals(CustomThemeColors(), result.applied.single().colors)
    }

    // ── Font and size ───────────────────────────────────────────────────────────

    @Test
    fun `every text size is offered and the chosen one is handed back`() = content { result ->
        listOf("Small" to 0.9f, "Default" to 1f, "Large" to 1.15f, "Extra large" to 1.3f).forEach { (label, scale) ->
            click(label)
            click("Apply")
            assertEquals(scale, result.applied.last().fontScale, label)
        }
    }

    @Test
    fun `use default clears a chosen font`() = content(initial.copy(fontFamily = "Serif")) { result ->
        click("Use default")
        click("Apply")

        assertEquals("", result.applied.single().fontFamily)
    }

    @Test
    fun `the platform font is shown by name when none is chosen`() = content { _ ->
        onAllNodes(hasText("System default")).onFirst().assertExists()
    }

    // ── Preview ─────────────────────────────────────────────────────────────────

    @Test
    fun `the preview draws a selected row, the buttons and the three statuses`() = content { _ ->
        listOf("Amazing Grace", "How Great Thou Art", "Go Live", "Connected", "Connecting", "Failed").forEach {
            onAllNodes(hasText(it)).onFirst().assertExists()
        }
    }
}
