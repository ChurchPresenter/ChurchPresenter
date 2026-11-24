@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabMenuLabels
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.model.TabStyleLabels
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class TabVisibilityButtonTest {

    private val labels = TabMenuLabels(
        "Tab visibility", TabStyleLabels("Tab style", "Icon + text", "Text", "Icon"),
        "Show tabs", "Reset to default", "Move up", "Move down",
    )

    private var order by mutableStateOf(listOf("Bible", "Songs", "Media"))
    private var hidden by mutableStateOf(setOf<String>())
    private var style by mutableStateOf(TabStyle.IconAndText)
    private var resets = 0

    @Composable
    private fun Menu() {
        HosannaSurface {
            TabVisibilityButton(
                order.map { NavigationTab(it, it, testIcon()) }, hidden,
                { key, visible -> hidden = if (visible) hidden - key else hidden + key },
                { order = it }, { resets++ }, style, { style = it }, labels,
            )
        }
    }

    private fun ComposeUiTest.row(name: String) =
        onNode(hasAnyDescendant(hasText(name)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))

    private fun ComposeUiTest.open() {
        setContent { Menu() }
        onNodeWithContentDescription("Tab visibility").performClick()
        waitForIdle()
    }

    @Test
    fun `the eye button opens the menu`() = runComposeUiTest {
        setContent { Menu() }
        onNodeWithText("Show tabs", ignoreCase = true).assertDoesNotExist()
        onNodeWithContentDescription("Tab visibility").performClick()
        waitForIdle()
        onNodeWithText("Show tabs", ignoreCase = true).assertExists()
        onNodeWithText("Tab style", ignoreCase = true).assertExists()
    }

    @Test
    fun `unchecking hides a tab, and the last one stays`() = runComposeUiTest {
        open()
        onNodeWithText("Songs").assertIsOn().performClick()
        waitForIdle()
        assertEquals(setOf("Songs"), hidden)
        onNodeWithText("Songs").assertIsOff()
        onNodeWithText("Media").performClick()
        waitForIdle()
        onNodeWithText("Bible").assertIsNotEnabled()
        onNodeWithText("Songs").performClick()
        waitForIdle()
        assertEquals(setOf("Media"), hidden)
    }

    @Test
    fun `the style and reset reach the caller`() = runComposeUiTest {
        open()
        onNodeWithText("Icon").performClick()
        waitForIdle()
        assertEquals(TabStyle.Icon, style)
        onNodeWithText("Reset to default").performClick()
        waitForIdle()
        assertEquals(1, resets)
    }

    @Test
    fun `Alt and the arrows move a row, as do screen reader actions`() = runComposeUiTest {
        open()
        onNodeWithText("Bible").requestFocus()
        onNodeWithText("Bible").performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) } }
        waitForIdle()
        assertEquals(listOf("Songs", "Bible", "Media"), order)
        onNodeWithText("Bible").performKeyInput { pressKey(Key.DirectionDown) }
        assertEquals(listOf("Songs", "Bible", "Media"), order)
        val moves = row("Media").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Move up"), moves.map { it.label })
        moves.single().action()
        waitForIdle()
        assertEquals(listOf("Songs", "Media", "Bible"), order)
    }

    @Test
    fun `dragging a handle past the next row moves it there`() = runComposeUiTest {
        open()
        val bible = row("Bible")
        val height = bible.fetchSemanticsNode().boundsInRoot.height
        val inset = with(density) { 18.dp.toPx() }
        bible.performMouseInput {
            val handle = Offset(right - inset, centerY)
            moveTo(handle)
            press()
            moveBy(Offset(0f, height * 0.3f))
            moveBy(Offset(0f, height * 0.4f))
            release()
        }
        waitForIdle()
        assertEquals(listOf("Songs", "Bible", "Media"), order)
    }
}
