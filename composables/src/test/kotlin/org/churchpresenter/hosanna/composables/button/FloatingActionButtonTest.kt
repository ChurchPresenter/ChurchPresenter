@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.hosanna.composables.defaults.FabDefaults
import org.churchpresenter.hosanna.composables.defaults.FabMetrics
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.FabSize
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon

class FloatingActionButtonTest {

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `sizes match the spec`() {
        assertEquals(FabMetrics(56.dp, 24.dp), FabDefaults.metrics(FabSize.Regular))
        assertEquals(FabMetrics(40.dp, 20.dp), FabDefaults.metrics(FabSize.Small))
    }

    @Test
    fun `a tonal fab is secondary, the standard one primary`() {
        assertEquals(ButtonHierarchy.Secondary, fabHierarchy(tonal = true))
        assertEquals(ButtonHierarchy.Primary, fabHierarchy(tonal = false))
    }

    @Test
    fun `each size draws a circle of its side`() = runComposeUiTest {
        setContent {
            HosannaSurface {
                Column {
                    FabSize.entries.forEach { size -> FloatingActionButton(testIcon(), size.name, {}, size = size) }
                }
            }
        }
        FabSize.entries.forEach { size ->
            val side = FabDefaults.metrics(size).side
            onNodeWithContentDescription(size.name).assertWidthIsEqualTo(side).assertHeightIsEqualTo(side)
        }
    }

    @Test
    fun `a click reaches onClick, a disabled fab ignores it`() = runComposeUiTest {
        var clicks = 0
        setContent {
            HosannaSurface {
                Column {
                    FloatingActionButton(testIcon(), "Add", { clicks++ })
                    FloatingActionButton(testIcon(), "Add later", { clicks++ }, enabled = false)
                }
            }
        }
        onNodeWithContentDescription("Add").assert(button).performClick()
        onNodeWithContentDescription("Add later").assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `an extended fab shows its label at 48dp tall`() = runComposeUiTest {
        var clicks = 0
        setContent { HosannaSurface { ExtendedFloatingActionButton("Add layer", testIcon(), { clicks++ }) } }
        onNodeWithText("Add layer").assert(button).assertHeightIsEqualTo(FabDefaults.ExtendedHeight).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a hidden fab is not there, and appears when shown`() = runComposeUiTest {
        var visible by mutableStateOf(false)
        setContent {
            HosannaSurface {
                Column {
                    FloatingActionButton(testIcon(), "Add", {}, visible = visible)
                    ExtendedFloatingActionButton("Add layer", testIcon(), {}, visible = visible)
                }
            }
        }
        onNodeWithContentDescription("Add").assertDoesNotExist()
        onNodeWithText("Add layer").assertDoesNotExist()
        visible = true
        waitForIdle()
        onNodeWithContentDescription("Add").assertExists()
        onNodeWithText("Add layer").assertExists()
    }
}
