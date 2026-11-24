@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.churchpresenter.hosanna.composables.defaults.StandardButtonDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon

class StandardButtonTest {

    private val role = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `a click reaches onClick and the interaction source`() = runComposeUiTest {
        var clicks = 0
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        CoroutineScope(Dispatchers.Unconfined).launch { source.interactions.collect { seen += it } }
        setContent { HosannaSurface { StandardButton("Save", { clicks++ }, interactionSource = source) } }
        onNodeWithText("Save").assert(role).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(1, clicks)
        assertTrue(seen.any { it is PressInteraction.Press })
        assertTrue(seen.any { it is PressInteraction.Release })
    }

    @Test
    fun `a disabled button cannot be clicked`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton("Delete", { clicks++ }, enabled = false)
            }
        }
        onNodeWithText("Delete").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `a loading button cannot be clicked and says it is busy`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton("Upload", { clicks++ }, icon = testIcon(), loading = true)
            }
        }
        settle()
        onNodeWithText("Upload").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `a loading button without an icon keeps its label for screen readers`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { HosannaSurface { StandardButton("Save", {}, loading = true) } }
        settle()
        onNodeWithText("Save").assertExists().assertIsNotEnabled()
    }

    @Test
    fun `a text button is 34dp tall and at least 64dp wide`() = runComposeUiTest {
        setContent { HosannaSurface { StandardButton("OK", {}) } }
        onNodeWithText("OK")
            .assertHeightIsEqualTo(StandardButtonDefaults.Height)
            .assertWidthIsAtLeast(StandardButtonDefaults.MinWidth)
    }

    @Test
    fun `an icon-only button is named and square`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                StandardButton(testIcon(), "Add to schedule", { clicks++ })
            }
        }
        onNodeWithContentDescription("Add to schedule")
            .assert(role)
            .assertWidthIsEqualTo(StandardButtonDefaults.Height)
            .assertHeightIsEqualTo(StandardButtonDefaults.Height)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `hovering reaches the interaction source`() = runComposeUiTest {
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        CoroutineScope(Dispatchers.Unconfined).launch { source.interactions.collect { seen += it } }
        setContent {
            HosannaSurface {
                StandardButton("Save", {}, interactionSource = source, hierarchy = ButtonHierarchy.Tertiary)
            }
        }
        onNodeWithText("Save").performMouseInput { enter(center) }
        waitForIdle()
        assertTrue(seen.any { it is HoverInteraction.Enter })
        assertNull(seen.firstOrNull { it is FocusInteraction.Focus })
    }
}
