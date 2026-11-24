@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.chip

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChipsTest {

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun `an action chip runs on click and is 28dp or 24dp tall`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                Column {
                    ActionChip("Verse 1", { clicks++ }, icon = testIcon(), color = ChipColor.Verse)
                    ActionChip("Small", {}, size = ChipSize.Small)
                }
            }
        }
        onNodeWithText("Verse 1").assert(button).assertHeightIsEqualTo(ChipDefaults.Default.height).performClick()
        onNodeWithText("Small").assertHeightIsEqualTo(ChipDefaults.Small.height)
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled action chip ignores clicks`() = runComposeUiTest {
        var clicks = 0
        setContent { HosannaSurface { ActionChip("Insert", { clicks++ }, enabled = false) } }
        onNodeWithText("Insert").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `a filter chip toggles and reports on and off`() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent { HosannaSurface { FilterChip("Hymns", on, { on = it }, icon = testIcon()) } }
        onNodeWithText("Hymns").assert(checkbox).assertIsOff().performClick()
        waitForIdle()
        assertTrue(on)
        onNodeWithText("Hymns").assertIsOn().performClick()
        waitForIdle()
        assertFalse(on)
    }

    @Test
    fun `an input chip is removed by its x, Delete or Backspace`() = runComposeUiTest {
        var removed = 0
        setContent {
            HosannaSurface {
                Column {
                    InputChip("Chorus", { removed++ }, "Remove Chorus", dragHandle = true)
                    InputChip("Bridge", { removed++ }, "Remove Bridge", color = ChipColor.Bridge)
                }
            }
        }
        onNodeWithContentDescription("Remove Chorus").assert(button).performClick()
        waitForIdle()
        assertEquals(1, removed)
        onNodeWithText("Bridge").requestFocus()
        onNodeWithText("Bridge").performKeyInput { pressKey(Key.Delete) }
        onNodeWithText("Bridge").performKeyInput { pressKey(Key.Backspace) }
        onNodeWithText("Bridge").performKeyInput { pressKey(Key.A) }
        waitForIdle()
        assertEquals(3, removed)
    }

    @Test
    fun `a disabled input chip cannot be removed`() = runComposeUiTest {
        var removed = 0
        setContent { HosannaSurface { InputChip("Tag", { removed++ }, "Remove Tag", enabled = false) } }
        onNodeWithContentDescription("Remove Tag").assertIsNotEnabled().performClick()
        assertEquals(0, removed)
    }

    @Test
    fun `an input chip with onClick is a button of its own`() = runComposeUiTest {
        var clicks = 0
        setContent { HosannaSurface { InputChip("Hymnal", {}, "Remove Hymnal", onClick = { clicks++ }) } }
        onNodeWithText("Hymnal").assert(button).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a tag is uppercase and not clickable`() = runComposeUiTest {
        setContent { HosannaSurface { Tag("Songs", color = ChipColor.Song) } }
        onNodeWithText("SONGS").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `a favorite file chip opens on click and its star toggles without opening`() = runComposeUiTest {
        var opened = 0
        var favorite by mutableStateOf(false)
        setContent {
            HosannaSurface {
                FavoriteFileChip("Easter.pptx", { opened++ }, favorite, { favorite = it }, "Add to favorites")
            }
        }
        onNodeWithContentDescription("Add to favorites").assert(button).performClick()
        waitForIdle()
        assertTrue(favorite)
        assertEquals(0, opened)
        onNodeWithText("Easter.pptx").performClick()
        waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun `a disabled favorite file chip neither opens nor stars`() = runComposeUiTest {
        var changes = 0
        setContent {
            HosannaSurface {
                FavoriteFileChip(
                    "Old.pptx", { changes++ }, true, { changes++ }, "Remove from favorites", enabled = false,
                )
            }
        }
        onNodeWithText("Old.pptx").assertIsNotEnabled().performClick()
        onNodeWithContentDescription("Remove from favorites").assertIsNotEnabled().performClick()
        assertEquals(0, changes)
    }
}
