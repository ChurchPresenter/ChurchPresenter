package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.FontCategory
import org.churchpresenter.sharedui.utils.FontFace
import org.churchpresenter.sharedui.utils.highlightedText
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SharedControlsExtrasTest {

    private fun highlight(text: String, query: String): AnnotatedString {
        var result = AnnotatedString("")
        runComposeUiTest {
            setContent { MaterialTheme { result = highlightedText(text, query) } }
            waitForIdle()
        }
        return result
    }

    @Test
    fun `every match of the search is marked bold`() {
        val result = highlight("Grace upon grace", "grace")
        assertEquals("Grace upon grace", result.text)
        assertEquals(listOf(0 to 5, 11 to 16), result.spanStyles.map { it.start to it.end })
        assertTrue(result.spanStyles.all { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `text after the last match is kept as it was`() {
        val result = highlight("Amazing grace, how sweet", "amazing")
        assertEquals("Amazing grace, how sweet", result.text)
        assertEquals(listOf(0 to 7), result.spanStyles.map { it.start to it.end })
    }

    @Test
    fun `a blank search marks nothing`() {
        val result = highlight("Psalm 23", "  ")
        assertEquals("Psalm 23", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `the vertical alignment buttons report the value they stand for`() = runComposeUiTest {
        var alignment by mutableStateOf("middle")
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                VerticalAlignmentButtons(
                    alignment, { alignment = it }, "top", "middle", "bottom",
                    buttonSize = 32.dp, cornerRadius = 6.dp,
                )
            }
        }
        onNodeWithContentDescription("Align Top").performClick()
        waitForIdle()
        assertEquals("top", alignment)
        onNodeWithContentDescription("Align Bottom").performClick()
        waitForIdle()
        assertEquals("bottom", alignment)
    }

    @Test
    fun `the save preset button reports its click`() = runComposeUiTest {
        var saves = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                SavePresetButton(onClick = { saves++ }, tooltipText = "Save preset")
            }
        }
        onNodeWithContentDescription("Save preset").performClick()
        waitForIdle()
        assertEquals(1, saves)
    }

    @Test
    fun `a disabled save preset button ignores clicks`() = runComposeUiTest {
        var saves = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                SavePresetButton(onClick = { saves++ }, tooltipText = "Save preset", enabled = false)
            }
        }
        onNodeWithContentDescription("Save preset").performClick()
        waitForIdle()
        assertEquals(0, saves)
    }

    @Test
    fun `typing a valid hex into the picker enables OK and hands that color back`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                ColorPickerDialog(initialHex = "#000000", onDismiss = {}, onColorSelected = { picked += it })
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("#80FF00")
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals("#80FF00", picked.single().uppercase())
    }

    @Test
    fun `typing something that is not a hex leaves OK unusable`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                ColorPickerDialog(initialHex = "not a color", onDismiss = {}, onColorSelected = { picked += it })
            }
        }
        onNode(hasSetTextAction()).performTextReplacement("#XYZ")
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertTrue(picked.isEmpty())
        onNode(hasSetTextAction()).performTextReplacement("#")
        waitForIdle()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `an accent segmented row wraps long labels when allowed`() = runComposeUiTest {
        var value by mutableStateOf(1)
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalSegmentedButtonTone provides SegmentedButtonTone.ACCENT) {
                    SegmentedButton(
                        items = listOf(
                            SegmentedButtonItem(1, "First choice", width = 90.dp),
                            SegmentedButtonItem(2, "Second choice"),
                        ),
                        selectedValue = value,
                        onValueChange = { value = it },
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
            }
        }
        onNodeWithText("Second choice").performClick()
        waitForIdle()
        assertEquals(2, value)
    }

    @Test
    fun `a font list with nothing recommended has no recommended group`() {
        val faces = listOf(
            FontFace("Zapfino", FontCategory.DISPLAY, cyrillic = false, hebrew = false, recommended = false),
        )
        val groups = groupFonts(faces, query = "", recents = emptyList())
        assertEquals(listOf(FontGroupKind.ALL), groups.map { it.kind })
    }

    @Test
    fun `a font list that is all recommended has no catch-all group`() {
        val faces = listOf(FontFace("Arial", FontCategory.SANS, cyrillic = true, hebrew = true, recommended = true))
        val groups = groupFonts(faces, query = "", recents = emptyList())
        assertEquals(listOf(FontGroupKind.RECOMMENDED), groups.map { it.kind })
    }
}
