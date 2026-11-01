package org.churchpresenter.sharedui.composables

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SectionOrderTokensTest {

    @Test
    fun `no order says the song is sung as written`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { SectionOrderTokens(emptyList(), "As written") }
        }

        onNodeWithText("As written").assertExists()
    }

    @Test
    fun `a short order shows every section and no count`() = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                SectionOrderTokens(listOf("[Verse 1]", "{Chorus}", "[1 куплет]"), "As written")
            }
        }

        onNodeWithText("V1").assertExists()
        onNodeWithText("C").assertExists()
        onNodeWithText("К1").assertExists()
        onAllNodesWithText("+", substring = true).assertCountEquals(0)
        onAllNodesWithText("As written").assertCountEquals(0)
    }

    @Test
    fun `a long order stops at five sections and says how many more`() = runComposeUiTest {
        val order = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}#2", "[Bridge]", "{Chorus}#3", "[Coda]")
        setContent { ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) { SectionOrderTokens(order, "As written") } }

        onNodeWithText("B").assertExists()
        onAllNodesWithText("C").assertCountEquals(2)
        onNodeWithText("+2").assertExists()
    }
}
