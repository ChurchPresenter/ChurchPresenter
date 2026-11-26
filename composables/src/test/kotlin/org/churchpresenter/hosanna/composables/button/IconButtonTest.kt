@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.hosanna.composables.defaults.IconButtonDefaults
import org.churchpresenter.hosanna.composables.defaults.IconButtonMetrics
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.settle
import org.churchpresenter.hosanna.composables.testing.testIcon

class IconButtonTest {

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun `the three sizes match the spec`() {
        assertEquals(
            IconButtonMetrics(40.dp, 20.dp, 18.dp, AppRadius.M),
            IconButtonDefaults.metrics(IconButtonSize.Large),
        )
        assertEquals(
            IconButtonMetrics(34.dp, 16.dp, 16.dp, AppRadius.M),
            IconButtonDefaults.metrics(IconButtonSize.Medium),
        )
        assertEquals(
            IconButtonMetrics(28.dp, 14.dp, 14.dp, AppRadius.S),
            IconButtonDefaults.metrics(IconButtonSize.Small),
        )
    }

    @Test
    fun `a selected toggle takes the primary look, an unselected one keeps its own`() {
        ButtonHierarchy.entries.forEach { h ->
            assertEquals(ButtonHierarchy.Primary, toggleHierarchy(h, checked = true))
            assertEquals(h, toggleHierarchy(h, checked = false))
        }
    }

    @Test
    fun `each size draws a square of its side`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    IconButtonSize.entries.forEach { size -> IconButton(testIcon(), size.name, {}, size = size) }
                }
            }
        }
        IconButtonSize.entries.forEach { size ->
            val side = IconButtonDefaults.metrics(size).side
            onNodeWithContentDescription(size.name).assertWidthIsEqualTo(side).assertHeightIsEqualTo(side)
        }
    }

    @Test
    fun `a click reaches onClick and the interaction source`() = runComposeUiTest {
        var clicks = 0
        val source = MutableInteractionSource()
        val seen = mutableListOf<Interaction>()
        CoroutineScope(Dispatchers.Unconfined).launch { source.interactions.collect { seen += it } }
        setContent { HosannaSurface { IconButton(testIcon(), "Edit", { clicks++ }, interactionSource = source) } }
        onNodeWithContentDescription("Edit").assert(button).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(1, clicks)
        assertTrue(seen.any { it is PressInteraction.Press })
    }

    @Test
    fun `a disabled icon button cannot be clicked`() = runComposeUiTest {
        var clicks = 0
        setContent { HosannaSurface { IconButton(testIcon(), "Redo", { clicks++ }, enabled = false) } }
        onNodeWithContentDescription("Redo").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `a loading icon button cannot be clicked and says it is busy`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var clicks = 0
        setContent { HosannaSurface { IconButton(testIcon(), "Upload", { clicks++ }, loading = true) } }
        settle()
        onNodeWithContentDescription("Upload").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `a toggle flips its state and reports it`() = runComposeUiTest {
        var on by mutableStateOf(false)
        setContent {
            HosannaSurface { IconToggleButton(testIcon(), "Show on screen", on, { on = it }) }
        }
        val node = onNodeWithContentDescription("Show on screen").assert(checkbox)
        node.assertIsOff().performClick()
        waitForIdle()
        assertTrue(on)
        node.assertIsOn().performClick()
        waitForIdle()
        assertEquals(false, on)
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    @Test
    fun `a disabled or loading toggle does not flip`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var changes = 0
        setContent {
            HosannaSurface {
                Column {
                    IconToggleButton(testIcon(), "Loop", true, { changes++ }, enabled = false)
                    IconToggleButton(testIcon(), "Remote", true, { changes++ }, loading = true)
                }
            }
        }
        settle()
        onNodeWithContentDescription("Loop").assertIsNotEnabled().assertIsOn().performClick()
        onNodeWithContentDescription("Remote").assertIsNotEnabled().assertIsOn().performClick()
        assertEquals(0, changes)
    }
}
