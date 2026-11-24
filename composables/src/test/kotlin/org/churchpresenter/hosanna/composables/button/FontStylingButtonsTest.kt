@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.composables.foundation.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.defaults.IconSegmentDefaults
import org.churchpresenter.hosanna.composables.defaults.SplitButtonDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonColors
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.SegmentRole
import org.churchpresenter.hosanna.composables.foundation.iconSegmentColors
import org.churchpresenter.hosanna.composables.foundation.startSegment
import org.churchpresenter.hosanna.composables.model.TextStyleToggle
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class FontStylingButtonsTest {

    private val c = LightThemeColors
    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private fun toggleState(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Test
    fun `every style has its own icon`() {
        assertEquals(TextStyleToggle.entries.size, TextStyleToggle.entries.map { it.icon() }.toSet().size)
    }

    @Test
    fun `on and off segments use the segment colors`() {
        ButtonState.entries.forEach { state ->
            assertEquals(c.segmentColors(true, state), c.iconSegmentColors(ToggleableState.On, state))
            assertEquals(c.segmentColors(false, state), c.iconSegmentColors(ToggleableState.Off, state))
        }
    }

    @Test
    fun `a mixed segment rests on the primary container`() {
        fun layer(alpha: Float) = c.onPrimaryContainer.copy(alpha = alpha).compositeOver(c.primaryContainer)
        val mixed = ToggleableState.Indeterminate
        assertEquals(
            ButtonColors(c.primaryContainer, c.onPrimaryContainer, null),
            c.iconSegmentColors(mixed, ButtonState.Rest),
        )
        assertEquals(
            ButtonColors(layer(HosannaStateAlpha.HOVER), c.onPrimaryContainer, null),
            c.iconSegmentColors(mixed, ButtonState.Hovered),
        )
        assertEquals(
            ButtonColors(layer(HosannaStateAlpha.PRESSED), c.onPrimaryContainer, null),
            c.iconSegmentColors(mixed, ButtonState.Pressed),
        )
        assertEquals(
            ButtonColors(
                c.primaryContainer,
                c.onPrimaryContainer,
                c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
            ),
            c.iconSegmentColors(mixed, ButtonState.Focused),
        )
        assertEquals(c.segmentColors(false, ButtonState.Disabled), c.iconSegmentColors(mixed, ButtonState.Disabled))
    }

    @Test
    fun `tab lands on the chosen radio segment, or the first enabled one`() {
        val on = ToggleableState.On
        val off = ToggleableState.Off
        assertEquals(2, startSegment(SegmentRole.Radio, listOf(off, off, on), listOf(true, true, true)))
        assertEquals(0, startSegment(SegmentRole.Toggle, listOf(off, off, on), listOf(true, true, true)))
        assertEquals(1, startSegment(SegmentRole.Action, listOf(off, off), listOf(false, true)))
    }

    @Test
    fun `a style toggle reports on, off or mixed and toggles on click`() = runComposeUiTest {
        val toggled = mutableListOf<TextStyleToggle>()
        setContent {
            HosannaSurface {
                TextStyleToggles(
                    mapOf(
                        TextStyleToggle.Bold to ToggleableState.On,
                        TextStyleToggle.Italic to ToggleableState.Indeterminate,
                    ),
                    { toggled += it },
                    { it.name },
                )
            }
        }
        onNodeWithContentDescription("Bold").assert(checkbox).assert(toggleState(ToggleableState.On))
        onNodeWithContentDescription("Italic").assert(toggleState(ToggleableState.Indeterminate))
        onNodeWithContentDescription("Underline").assert(toggleState(ToggleableState.Off)).performClick()
        onNodeWithContentDescription("Italic").performClick()
        waitForIdle()
        assertEquals(listOf(TextStyleToggle.Underline, TextStyleToggle.Italic), toggled)
    }

    @Test
    fun `a disabled style and a disabled group ignore clicks`() = runComposeUiTest {
        val toggled = mutableListOf<TextStyleToggle>()
        var enabled by mutableStateOf(true)
        setContent {
            HosannaSurface {
                TextStyleToggles(
                    emptyMap(), { toggled += it }, { it.name },
                    enabled = enabled, disabled = setOf(TextStyleToggle.Italic),
                )
            }
        }
        onNodeWithContentDescription("Italic").assertIsNotEnabled().performClick()
        enabled = false
        waitForIdle()
        onNodeWithContentDescription("Bold").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), toggled)
    }

    @Test
    fun `arrow keys move between styles without toggling, Space toggles`() = runComposeUiTest {
        val toggled = mutableListOf<TextStyleToggle>()
        setContent {
            HosannaSurface {
                TextStyleToggles(emptyMap(), { toggled += it }, { it.name }, disabled = setOf(TextStyleToggle.Italic))
            }
        }
        onNodeWithContentDescription("Bold").requestFocus()
        onNodeWithContentDescription("Bold").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(emptyList(), toggled)
        onNodeWithContentDescription("Underline").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(listOf(TextStyleToggle.Underline), toggled)
    }

    @Test
    fun `a split button toggles its effect and opens its options separately`() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var opened = 0
        setContent {
            HosannaSurface {
                SplitToggleButton(testIcon(), "Outline", checked, { checked = it }, { opened++ }, "Outline options")
            }
        }
        val main = onNodeWithContentDescription("Outline")
        main.assert(checkbox).assert(toggleState(ToggleableState.Off))
            .assertWidthIsEqualTo(IconSegmentDefaults.Regular.width)
            .assertHeightIsEqualTo(IconSegmentDefaults.Regular.height)
            .performClick()
        waitForIdle()
        assertTrue(checked)
        main.assert(toggleState(ToggleableState.On))
        onNodeWithContentDescription("Outline options").assert(button)
            .assertWidthIsEqualTo(SplitButtonDefaults.ChevronWidth)
            .performClick()
        waitForIdle()
        assertEquals(1, opened)
        assertTrue(checked)
    }

    @Test
    fun `a disabled split button ignores both parts`() = runComposeUiTest {
        var changes = 0
        setContent {
            HosannaSurface {
                SplitToggleButton(
                    testIcon(), "Backing", true, { changes++ }, { changes++ }, "Backing options",
                    indicatorColor = c.favorite, enabled = false,
                )
            }
        }
        onNodeWithContentDescription("Backing").assertIsNotEnabled().performClick()
        onNodeWithContentDescription("Backing options").assertIsNotEnabled().performClick()
        assertEquals(0, changes)
    }
}
