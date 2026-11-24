@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TopNavigationBarTest {

    private val names = listOf("Bible", "Songs", "Pictures", "Presentation", "Media")

    @Composable
    private fun tabs() = names.map {
        NavigationTab(it.lowercase(), it, testIcon(), badge = if (it == "Media") "3" else null)
    }

    @Test
    fun `clicking a tab selects it and tabs read as tabs`() = runComposeUiTest {
        var selected by mutableStateOf("bible")
        setContent { HosannaSurface { TopNavigationBar(tabs(), selected, { selected = it }, Modifier.width(900.dp)) } }
        onNodeWithText("Bible").assertIsSelected()
        onNodeWithText("Songs")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .performClick()
        waitForIdle()
        assertEquals("songs", selected)
        onNodeWithText("3").assertExists()
    }

    @Test
    fun `arrows move focus between tabs and wrap, Enter opens`() = runComposeUiTest {
        var selected by mutableStateOf("bible")
        setContent { HosannaSurface { TopNavigationBar(tabs(), selected, { selected = it }, Modifier.width(900.dp)) } }
        onNodeWithText("Bible").requestFocus()
        onNodeWithText("Bible").performKeyInput { pressKey(Key.DirectionLeft) }
        onNodeWithText("Media").assertIsFocused()
        onNodeWithText("Media").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Bible").assertIsFocused()
        onNodeWithText("Bible").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithText("Songs").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("songs", selected)
    }

    @Test
    fun `the text style drops the icons, the icon style names tabs for screen readers`() = runComposeUiTest {
        var style by mutableStateOf(TabStyle.Text)
        setContent { HosannaSurface { TopNavigationBar(tabs(), "bible", {}, Modifier.width(900.dp), style) } }
        onNodeWithText("Bible").assertExists()
        style = TabStyle.Icon
        waitForIdle()
        onNodeWithText("Bible").assertDoesNotExist()
        onNodeWithContentDescription("Bible").assertIsSelected()
    }

    @Test
    fun `tabs drop to icons when the labels do not fit`() = runComposeUiTest {
        setContent { HosannaSurface { TopNavigationBar(tabs(), "bible", {}, Modifier.width(320.dp)) } }
        onNodeWithText("Presentation").assertDoesNotExist()
        onNodeWithContentDescription("Presentation").assertExists()
    }

    @Test
    fun `a bar too narrow for icons scrolls with the wheel`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                TopNavigationBar(tabs(), "bible", {}, Modifier.width(140.dp)) { }
            }
        }
        val before = onNodeWithContentDescription("Songs").getBoundsInRoot().left
        onNodeWithContentDescription("Bible").performMouseInput {
            moveTo(center)
            scroll(3f, ScrollWheel.Vertical)
        }
        waitForIdle()
        assertNotEquals(before, onNodeWithContentDescription("Songs").getBoundsInRoot().left)
    }

    @Test
    fun `the selected tab scrolls into view`() = runComposeUiTest {
        var selected by mutableStateOf("bible")
        setContent { HosannaSurface { TopNavigationBar(tabs(), selected, { selected = it }, Modifier.width(120.dp)) } }
        val hidden = onNodeWithContentDescription("Media").getBoundsInRoot()
        assertEquals(0.dp, hidden.right - hidden.left)
        selected = "media"
        waitForIdle()
        val shown = onNodeWithContentDescription("Media").getBoundsInRoot()
        assertTrue(shown.right - shown.left >= 41.dp)
    }

    @Test
    fun `tab width adds padding, icon, label and badge by style`() {
        val density = Density(1f)
        assertEquals(28f + 17f + 6f + 40f, tabWidth(TabStyle.IconAndText, 40f, null, density))
        assertEquals(28f + 40f + 6f + 18f, tabWidth(TabStyle.Text, 40f, 4f, density))
        assertEquals(28f + 40f + 6f + 30f, tabWidth(TabStyle.Text, 40f, 20f, density))
        assertEquals(24f + 17f, tabWidth(TabStyle.Icon, 40f, 20f, density))
    }
}
