package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ColorPickerFieldTest {

    @Test
    fun `an opaque color shows its hex and no opacity`() = runComposeUiTest {
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ColorPickerField("#FF0000", {}) } }
        onNodeWithText("#FF0000").assertExists()
        assertEquals(0, onAllNodesWithText("%", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a translucent color shows its RGB and its opacity apart`() = runComposeUiTest {
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ColorPickerField("#80FF0000", {}) } }
        onNodeWithText("#FF0000").assertExists()
        onNodeWithText("50%").assertExists()
    }

    @Test
    fun `transparent is named as it is`() = runComposeUiTest {
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.DARK) { ColorPickerField("transparent", {}) } }
        onNodeWithText("transparent").assertExists()
    }

    @Test
    fun `the field's label is shown in capitals`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ColorPickerField("#000000", {}, label = "Border") }
        }
        onNodeWithText("BORDER").assertExists()
    }

    @Test
    fun `clicking the field opens the picker and Cancel leaves the color alone`() = runComposeUiTest {
        var color by mutableStateOf("#00FF00")
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ColorPickerField(color, { color = it }) } }
        onNodeWithText("#00FF00").performClick()
        waitForIdle()
        onNodeWithText("Choose Color").assertExists()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        onNodeWithText("Choose Color").assertDoesNotExist()
        assertEquals("#00FF00", color)
    }

    @Test
    fun `OK in the picker hands the color back and closes it`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { ColorPickerField("#0000FF", { picked += it }) }
        }
        onNodeWithText("#0000FF").performClick()
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(1, picked.size)
        assertEquals("#0000FF", picked.single().uppercase())
        onNodeWithText("Choose Color").assertDoesNotExist()
    }
}
