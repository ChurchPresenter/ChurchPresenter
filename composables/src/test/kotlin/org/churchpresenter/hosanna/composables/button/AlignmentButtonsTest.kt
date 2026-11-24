@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlignmentButtonsTest {

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `every alignment has its own icon and sits in one group`() {
        assertEquals(TextAlignment.entries.size, TextAlignment.entries.map { it.icon() }.toSet().size)
        assertEquals(LayerAlignment.entries.size, LayerAlignment.entries.map { it.icon() }.toSet().size)
        assertEquals(TextAlignment.entries.toSet(), (HorizontalTextAlignments + VerticalTextAlignments).toSet())
        assertEquals(LayerAlignment.entries.toSet(), (HorizontalLayerAlignments + VerticalLayerAlignments).toSet())
    }

    @Test
    fun `segments are 32 by 30, or 28 by 26 when compact`() = runComposeUiTest {
        var compact by mutableStateOf(false)
        setContent { HosannaSurface { TextAlignmentButtons(null, null, {}, { it.name }, compact = compact) } }
        val regular = IconSegmentDefaults.Regular
        onNodeWithContentDescription("Left").assertWidthIsEqualTo(regular.width).assertHeightIsEqualTo(regular.height)
        compact = true
        waitForIdle()
        val small = IconSegmentDefaults.Compact
        onNodeWithContentDescription("Left").assertWidthIsEqualTo(small.width).assertHeightIsEqualTo(small.height)
    }

    @Test
    fun `text alignment is one choice per group`() = runComposeUiTest {
        var horizontal by mutableStateOf<TextAlignment?>(TextAlignment.Center)
        var vertical by mutableStateOf<TextAlignment?>(TextAlignment.Middle)
        setContent {
            HosannaSurface {
                TextAlignmentButtons(horizontal, vertical, {
                    if (it in HorizontalTextAlignments) horizontal = it else vertical = it
                }, { it.name })
            }
        }
        onNodeWithContentDescription("Center").assert(radio).assertIsSelected()
        onNodeWithContentDescription("Right").assertIsNotSelected().performClick()
        onNodeWithContentDescription("Bottom").performClick()
        waitForIdle()
        assertEquals(TextAlignment.Right, horizontal)
        assertEquals(TextAlignment.Bottom, vertical)
        onNodeWithContentDescription("Right").assertIsSelected()
        onNodeWithContentDescription("Center").assertIsNotSelected()
    }

    @Test
    fun `a mixed selection shows nothing chosen`() = runComposeUiTest {
        setContent { HosannaSurface { TextAlignmentButtons(null, null, {}, { it.name }) } }
        TextAlignment.entries.forEach { onNodeWithContentDescription(it.name).assertIsNotSelected() }
    }

    @Test
    fun `a disabled option and a disabled control ignore clicks`() = runComposeUiTest {
        val picked = mutableListOf<TextAlignment>()
        var enabled by mutableStateOf(true)
        setContent {
            HosannaSurface {
                TextAlignmentButtons(
                    TextAlignment.Left, TextAlignment.Top, { picked += it }, { it.name },
                    enabled = enabled, disabled = setOf(TextAlignment.Justify),
                )
            }
        }
        onNodeWithContentDescription("Justify").assertIsNotEnabled().performClick()
        enabled = false
        waitForIdle()
        onNodeWithContentDescription("Right").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), picked)
    }

    @Test
    fun `arrow keys pick the next text alignment and skip a disabled one`() = runComposeUiTest {
        var horizontal by mutableStateOf<TextAlignment?>(TextAlignment.Right)
        setContent {
            HosannaSurface {
                TextAlignmentButtons(
                    horizontal, TextAlignment.Top, { horizontal = it }, { it.name },
                    disabled = setOf(TextAlignment.Justify),
                )
            }
        }
        onNodeWithContentDescription("Right").requestFocus()
        onNodeWithContentDescription("Right").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(TextAlignment.Right, horizontal)
        onNodeWithContentDescription("Right").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(TextAlignment.Center, horizontal)
        onNodeWithContentDescription("Center").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(TextAlignment.Left, horizontal)
    }

    @Test
    fun `layer alignment runs an action and nothing stays selected`() = runComposeUiTest {
        val aligned = mutableListOf<LayerAlignment>()
        setContent { HosannaSurface { LayerAlignmentButtons({ aligned += it }, { it.name }) } }
        onNodeWithContentDescription("Center").assert(button).performClick()
        onNodeWithContentDescription("Bottom").performClick()
        waitForIdle()
        assertEquals(listOf(LayerAlignment.Center, LayerAlignment.Bottom), aligned)
        onNodeWithContentDescription("Center").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun `arrow keys only move focus in a layer group, Enter runs it`() = runComposeUiTest {
        val aligned = mutableListOf<LayerAlignment>()
        setContent { HosannaSurface { LayerAlignmentButtons({ aligned += it }, { it.name }) } }
        onNodeWithContentDescription("Left").requestFocus()
        onNodeWithContentDescription("Left").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(emptyList(), aligned)
        onNodeWithContentDescription("Center").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(LayerAlignment.Center), aligned)
    }

    @Test
    fun `a disabled layer group ignores clicks`() = runComposeUiTest {
        val aligned = mutableListOf<LayerAlignment>()
        setContent { HosannaSurface { LayerAlignmentButtons({ aligned += it }, { it.name }, enabled = false) } }
        onNodeWithContentDescription("Top").assertIsNotEnabled().performClick()
        assertEquals(emptyList(), aligned)
    }

    @Test
    fun `the vertical group stacks under the horizontal one when there is no room`() = runComposeUiTest {
        setContent { HosannaSurface { Box(Modifier.width(200.dp)) { LayerAlignmentButtons({}, { it.name }) } } }
        val left = onNodeWithContentDescription("Left").getUnclippedBoundsInRoot()
        val top = onNodeWithContentDescription("Top").getUnclippedBoundsInRoot()
        assertTrue(top.top > left.bottom)
        assertEquals(left.left, top.left)
    }
}
