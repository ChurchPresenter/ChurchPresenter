@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.defaults.SegmentedButtonDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonColors
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.SegmentedOption
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class SegmentedButtonTest {

    private val c = LightThemeColors
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val three = listOf(SegmentedOption("Timer"), SegmentedOption("Duration"), SegmentedOption("Clock"))

    @Test
    fun `left and up step back, right and down step on, other keys do nothing`() {
        assertEquals(-1, arrowStep(Key.DirectionLeft))
        assertEquals(-1, arrowStep(Key.DirectionUp))
        assertEquals(1, arrowStep(Key.DirectionRight))
        assertEquals(1, arrowStep(Key.DirectionDown))
        assertEquals(0, arrowStep(Key.Tab))
    }

    @Test
    fun `the next segment skips disabled ones and stops at the ends`() {
        val enabled = listOf(true, false, true, true)
        assertEquals(2, nextEnabledSegment(enabled, 0, 1))
        assertEquals(0, nextEnabledSegment(enabled, 2, -1))
        assertNull(nextEnabledSegment(enabled, 3, 1))
        assertNull(nextEnabledSegment(enabled, 0, -1))
    }

    @Test
    fun `tab lands on the selected segment, or the first enabled one`() {
        assertEquals(1, focusableSegment(listOf(true, true, true), 1))
        assertEquals(0, focusableSegment(listOf(true, false, true), 1))
        assertEquals(2, focusableSegment(listOf(false, false, true), 7))
        assertEquals(-1, focusableSegment(listOf(false, false), 0))
    }

    @Test
    fun `segments share the width only when every label fits`() {
        assertEquals(98, segmentShare(available = 300, gap = 3, widths = listOf(60, 90, 98)))
        assertNull(segmentShare(available = 300, gap = 3, widths = listOf(60, 90, 99)))
        assertNull(segmentShare(available = 300, gap = 3, widths = emptyList()))
    }

    @Test
    fun `segment colors come from the theme roles and state layers`() {
        fun on(content: Color, container: Color, alpha: Float) = content.copy(alpha = alpha).compositeOver(container)
        assertEquals(ButtonColors(c.primary, c.onPrimary, null), c.segmentColors(true, ButtonState.Rest))
        assertEquals(
            ButtonColors(Color.Transparent, c.onSurfaceVariant, null),
            c.segmentColors(false, ButtonState.Rest),
        )
        assertEquals(
            ButtonColors(on(c.onPrimary, c.primary, HosannaStateAlpha.HOVER), c.onPrimary, null),
            c.segmentColors(true, ButtonState.Hovered),
        )
        assertEquals(
            ButtonColors(on(c.onSurface, Color.Transparent, HosannaStateAlpha.PRESSED), c.onSurface, null),
            c.segmentColors(false, ButtonState.Pressed),
        )
        assertEquals(
            ButtonColors(
                Color.Transparent,
                c.onSurfaceVariant,
                c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
            ),
            c.segmentColors(false, ButtonState.Focused),
        )
        val disabledInk = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        assertEquals(
            ButtonColors(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), disabledInk, null),
            c.segmentColors(true, ButtonState.Disabled),
        )
        assertEquals(ButtonColors(Color.Transparent, disabledInk, null), c.segmentColors(false, ButtonState.Disabled))
        assertEquals(c.segmentColors(true, ButtonState.Rest), c.segmentColors(true, ButtonState.Loading))
    }

    @Test
    fun `the track is the secondary container, faded when disabled`() {
        assertEquals(c.secondaryContainer, c.trackColor(true))
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), c.trackColor(false))
    }

    @Test
    fun `only an enabled selected segment is raised`() {
        assertEquals(HosannaLevel.Level1, segmentLevel(true, ButtonState.Rest))
        assertEquals(HosannaLevel.Level1, segmentLevel(true, ButtonState.Pressed))
        assertEquals(HosannaLevel.Level0, segmentLevel(true, ButtonState.Disabled))
        assertEquals(HosannaLevel.Level0, segmentLevel(false, ButtonState.Rest))
    }

    @Test
    fun `clicking a segment selects it and reports it as a radio button`() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        setContent { HosannaSurface { SegmentedButton(three, selected, { selected = it }) } }
        onNodeWithText("Timer").assert(radio).assertIsSelected()
        onNodeWithText("Clock").assertIsNotSelected().performClick()
        waitForIdle()
        assertEquals(2, selected)
        onNodeWithText("Clock").assertIsSelected()
    }

    @Test
    fun `a disabled option and a disabled control ignore clicks`() = runComposeUiTest {
        val picked = mutableListOf<Int>()
        setContent {
            HosannaSurface {
                Column {
                    SegmentedButton(
                        listOf(SegmentedOption("A"), SegmentedOption("B", enabled = false)),
                        0,
                        { picked += it },
                    )
                    SegmentedButton(
                        listOf(SegmentedOption("C"), SegmentedOption("D")),
                        0,
                        { picked += it },
                        enabled = false,
                    )
                }
            }
        }
        onNodeWithText("B").assertIsNotEnabled().performClick()
        onNodeWithText("D").assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(emptyList(), picked)
    }

    @Test
    fun `arrow keys move the selection and skip disabled options`() = runComposeUiTest {
        var selected by mutableIntStateOf(0)
        val options = listOf(
            SegmentedOption("Timer"),
            SegmentedOption("Duration", enabled = false),
            SegmentedOption("Clock"),
        )
        setContent { HosannaSurface { SegmentedButton(options, selected, { selected = it }) } }
        onNodeWithText("Timer").requestFocus()
        waitForIdle()
        onNodeWithText("Timer").assertIsFocused()
        onNodeWithText("Timer").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(2, selected)
        onNodeWithText("Clock").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(2, selected)
        onNodeWithText("Clock").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(0, selected)
    }

    @Test
    fun `an icon-only segment is named and square`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                SegmentedButton(
                    listOf(
                        SegmentedOption("Left", testIcon(), iconOnly = true),
                        SegmentedOption("Right", testIcon(), iconOnly = true),
                    ),
                    0,
                    {},
                )
            }
        }
        val side = SegmentedButtonDefaults.Default.height
        onNodeWithContentDescription("Left").assert(radio).assertWidthIsEqualTo(side).assertHeightIsEqualTo(side)
    }

    @Test
    fun `fill shares the width when labels fit and hugs when they do not`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    Box(Modifier.width(400.dp)) {
                        SegmentedButton(
                            listOf(SegmentedOption("Lyrics"), SegmentedOption("Language 2")),
                            0,
                            {},
                            fillWidth = true,
                        )
                    }
                    Box(Modifier.width(150.dp)) {
                        SegmentedButton(
                            listOf(SegmentedOption("Timer"), SegmentedOption("Specific Time")),
                            0,
                            {},
                            fillWidth = true,
                        )
                    }
                }
            }
        }
        fun span(text: String) = onNodeWithText(text).getUnclippedBoundsInRoot().let { it.right - it.left }
        assertEquals(span("Lyrics"), span("Language 2"))
        assertTrue(span("Specific Time") > span("Timer"))
    }
}
