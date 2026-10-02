package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.utils.FontCatalog
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FontSettingsDropdownTest {

    private val fonts = listOf("Arial", "Georgia", "Courier New", "Zapfino", "Wingdings")

    private class Picker {
        var value by mutableStateOf("Arial")
        val expansions = mutableListOf<Boolean>()
    }

    @BeforeTest
    fun startClean() = RecentFonts.clear()

    @AfterTest
    fun cleanUp() {
        RecentFonts.clear()
        FontCatalog.reset()
    }

    private fun picker(
        label: String = "",
        fillWidth: Boolean = false,
        previewLines: List<String> = emptyList(),
        block: ComposeUiTest.(Picker) -> Unit,
    ) = runComposeUiTest {
        val picker = Picker()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontSettingsDropdown(
                    label = label,
                    value = picker.value,
                    fonts = fonts,
                    fillWidth = fillWidth,
                    previewLines = previewLines,
                    onExpandedChange = { picker.expansions += it },
                    onValueChange = { picker.value = it },
                )
            }
        }
        block(picker)
    }

    private fun ComposeUiTest.open() {
        onNodeWithText("Arial").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.search(): SemanticsNodeInteraction = onNodeWithContentDescription("Search fonts…")

    @Test
    fun `the field shows the family in use and its label`() = picker(label = "Font") {
        onNodeWithText("Arial").assertExists()
        onNodeWithText("FONT").assertExists()
    }

    @Test
    fun `opening the panel groups the projection favourites first and says so`() = picker { picker ->
        open()
        onNodeWithText("GOOD FOR PROJECTION", substring = true).assertExists()
        onNodeWithText("ALL FONTS", substring = true).assertExists()
        assertEquals(listOf(true), picker.expansions)
    }

    @Test
    fun `symbol fonts are left out and the footer says how many`() = picker {
        open()
        onNodeWithText("symbol and system fonts hidden", substring = true).assertExists()
        assertEquals(0, onAllNodesWithText("Wingdings").fetchSemanticsNodes().size)
    }

    @Test
    fun `a search narrows the list to matches`() = picker {
        open()
        search().performTextInput("cour")
        waitForIdle()
        onNodeWithText("MATCHES", substring = true).assertExists()
        assertTrue(onAllNodesWithText("Courier New").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, onAllNodesWithText("Georgia").fetchSemanticsNodes().size)
    }

    @Test
    fun `a search with no matches says so and can be cleared`() = picker {
        open()
        search().performTextInput("zzzz")
        waitForIdle()
        onNodeWithText("No results found for \"zzzz\"").assertExists()
        onNodeWithText("Clear search").performClick()
        waitForIdle()
        assertTrue(onAllNodesWithText("Georgia").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the small cross empties the search box`() = picker {
        open()
        search().performTextInput("geo")
        waitForIdle()
        onNodeWithContentDescription("Clear search").performClick()
        waitForIdle()
        onNodeWithText("Courier New").assertExists()
    }

    @Test
    fun `Enter without moving keeps the family in use`() = picker { picker ->
        open()
        search().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Arial", picker.value)
        assertEquals(listOf(true, false), picker.expansions)
    }

    @Test
    fun `the arrow keys move the choice before Enter takes it`() = picker { picker ->
        open()
        search().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertNotEquals("Arial", picker.value)
        assertTrue(picker.value in fonts)
    }

    @Test
    fun `Escape closes the panel without changing the family`() = picker { picker ->
        open()
        search().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("Arial", picker.value)
        assertEquals(listOf(true, false), picker.expansions)
        onNodeWithText("GOOD FOR PROJECTION", substring = true).assertDoesNotExist()
    }

    @Test
    fun `arrows over a search with no matches do nothing`() = picker { picker ->
        open()
        search().performTextInput("zzzz")
        search().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.A)
        }
        waitForIdle()
        assertEquals("Arial", picker.value)
    }

    @Test
    fun `clicking a family picks it and remembers it as recent`() = picker { picker ->
        open()
        onNodeWithText("Georgia").performClick()
        waitForIdle()
        assertEquals("Georgia", picker.value)
        assertEquals(listOf("Georgia"), RecentFonts.names)
    }

    @Test
    fun `a family picked this session heads the list next time`() = picker {
        open()
        onNodeWithText("Courier New").performClick()
        waitForIdle()
        onNodeWithText("Courier New").performClick()
        waitForIdle()
        onNodeWithText("RECENTLY USED", substring = true).assertExists()
    }

    @Test
    fun `the preview quotes the caller's own lines`() = picker(previewLines = listOf("Bereshit bara Elohim")) {
        open()
        onNodeWithText("Bereshit bara Elohim").assertExists()
    }

    @Test
    fun `a field stretched to its parent still opens the panel`() = picker(fillWidth = true) { picker ->
        open()
        assertEquals(listOf(true), picker.expansions)
    }
}
