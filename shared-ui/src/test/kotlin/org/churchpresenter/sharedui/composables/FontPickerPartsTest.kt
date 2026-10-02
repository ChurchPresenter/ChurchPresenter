package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.utils.FontCategory
import org.churchpresenter.sharedui.utils.FontFace
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FontPickerPartsTest {

    private fun face(cyrillic: Boolean = true, hebrew: Boolean = true) =
        FontFace("Plain", FontCategory.SANS, cyrillic = cyrillic, hebrew = hebrew, recommended = false)

    @Test
    fun `every shape has its own short name`() = runComposeUiTest {
        val names = mutableMapOf<FontCategory, String>()
        setContent { MaterialTheme { FontCategory.entries.forEach { names[it] = categoryLabel(it) } } }
        waitForIdle()
        assertEquals(
            mapOf(
                FontCategory.SANS to "Sans",
                FontCategory.SERIF to "Serif",
                FontCategory.MONO to "Mono",
                FontCategory.DISPLAY to "Display",
            ),
            names,
        )
    }

    @Test
    fun `every group has its own heading`() = runComposeUiTest {
        val names = mutableMapOf<FontGroupKind, String>()
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontGroupKind.entries.forEach {
                    names[it] = groupLabel(it)
                    groupColor(it)
                }
            }
        }
        waitForIdle()
        assertEquals(
            mapOf(
                FontGroupKind.RECENT to "Recently used",
                FontGroupKind.RECOMMENDED to "Good for projection",
                FontGroupKind.ALL to "All fonts",
                FontGroupKind.MATCHES to "Matches",
            ),
            names,
        )
    }

    @Test
    fun `a group heading shows its name in capitals and how many it holds`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) { FontGroupHeader(FontRow.Header(FontGroupKind.ALL, 42)) }
        }
        onNodeWithText("ALL FONTS").assertExists()
        onNodeWithText("42").assertExists()
    }

    @Test
    fun `a family without Cyrillic is warned about once measured`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontPreviewPane(face(cyrillic = false), measured = true, lines = listOf("В начале"))
            }
        }
        onNodeWithText("has no Cyrillic glyphs", substring = true).assertExists()
    }

    @Test
    fun `a family without Hebrew is warned about once measured`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontPreviewPane(face(hebrew = false), measured = true, lines = listOf("בְּרֵאשִׁית"))
            }
        }
        onNodeWithText("has no Hebrew glyphs", substring = true).assertExists()
    }

    @Test
    fun `nothing is warned about before the glyph scan has run`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontPreviewPane(face(cyrillic = false, hebrew = false), measured = false, lines = listOf("В начале"))
            }
        }
        onNodeWithText("has no Cyrillic glyphs", substring = true).assertDoesNotExist()
        onNodeWithText("В начале").assertExists()
    }

    @Test
    fun `a family that draws every line shows them with no warning`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                FontPreviewPane(face(), measured = true, lines = listOf("In the beginning", "В начале"))
            }
        }
        onNodeWithText("In the beginning").assertExists()
        onNodeWithText("has no", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a selected and highlighted row both pick on click`() = runComposeUiTest {
        var picks = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    FontFamilyRow(face(), selected = true, highlighted = false, onPick = { picks++ }, onHover = {})
                    FontFamilyRow(
                        face().copy(name = "Other"), selected = false, highlighted = true,
                        onPick = { picks++ }, onHover = {},
                    )
                }
            }
        }
        onNodeWithText("Plain").performClick()
        onNodeWithText("Other").performClick()
        waitForIdle()
        assertEquals(2, picks)
    }
}
