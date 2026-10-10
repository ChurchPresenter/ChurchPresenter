package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleListStyleTest {

    private fun <T> read(mode: ThemeMode, value: @Composable () -> T): T {
        val result = ArrayList<T>(1)
        runComposeUiTest {
            setContent { ChurchPresenterTheme(themeMode = mode) { result += value() } }
            waitForIdle()
        }
        return result.last()
    }

    private fun rowColors(mode: ThemeMode) = read(mode) {
        listOf(
            bibleRowColors(selected = false, hovered = false),
            bibleRowColors(selected = false, hovered = true),
            bibleRowColors(selected = true, hovered = false),
            bibleRowColors(selected = true, hovered = true),
        )
    }

    @Test
    fun `an idle row has no fill`() {
        assertEquals(Color.Transparent, rowColors(ThemeMode.LIGHT)[0].background)
    }

    @Test
    fun `hovering a row tints it faintly and darkens its ink`() {
        val (idle, hovered) = rowColors(ThemeMode.LIGHT)
        assertNotEquals(Color.Transparent, hovered.background)
        assertNotEquals(idle.ink, hovered.ink)
    }

    @Test
    fun `a selected row is tinted more strongly when hovered`() {
        val colors = rowColors(ThemeMode.DARK)
        assertTrue(colors[3].background.alpha > colors[2].background.alpha)
        assertEquals(colors[2].ink, colors[3].ink)
    }

    @Test
    fun `selection reads stronger on a dark theme than on a light one`() {
        assertTrue(rowColors(ThemeMode.DARK)[2].background.alpha > rowColors(ThemeMode.LIGHT)[2].background.alpha)
    }

    @Test
    fun `the card fill differs between light and dark themes`() {
        val light = read(ThemeMode.LIGHT) { bibleListCardFill() to MaterialTheme.colorScheme.surface }
        val dark = read(ThemeMode.DARK) { bibleListCardFill() to MaterialTheme.colorScheme.surfaceContainer }
        assertEquals(light.second, light.first)
        assertEquals(dark.second, dark.first)
    }

    @Test
    fun `the inset fill is a shade darker than the card in both themes`() {
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            val (card, inset) = read(mode) { bibleListCardFill() to bibleInsetFill() }
            assertNotEquals(card, inset, "$mode")
        }
    }

    @Test
    fun `the live tint is a faint wash of the error color`() {
        val (tint, error) = read(ThemeMode.LIGHT) { bibleLiveTint() to MaterialTheme.colorScheme.error }
        assertEquals(error.copy(alpha = tint.alpha), tint)
        assertTrue(tint.alpha < 0.5f)
    }

    @Test
    fun `a list card can be drawn in either theme`() {
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) { Box(Modifier.size(40.dp).testTag("card").bibleListCard()) }
                }
                onNodeWithTag("card").assertExists()
            }
        }
    }

    @Test
    fun `a row knows when the pointer is over it`() = runComposeUiTest {
        var hovered = false
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                val (interaction, isHovered) = rememberRowHover()
                hovered = isHovered
                Box(Modifier.size(40.dp).testTag("row").hoverable(interaction))
            }
        }
        onNodeWithTag("row").performMouseInput { moveTo(center) }
        waitForIdle()
        assertTrue(hovered)
    }

    @Test
    fun `a horizontal handle reports the drag and its end`() = runComposeUiTest {
        val deltas = mutableListOf<Float>()
        var ends = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                DragHandle(onDragEnd = { ends++ }, modifier = Modifier.testTag("handle").size(40.dp)) { deltas += it }
            }
        }
        onNodeWithTag("handle").performMouseInput { moveTo(center) }
        onNodeWithTag("handle").performTouchInput { swipeRight(startX = centerX, endX = centerX + 30f) }
        waitForIdle()
        assertTrue(deltas.sum() > 0f)
        assertEquals(1, ends)
    }

    @Test
    fun `a vertical handle drags along its own axis`() = runComposeUiTest {
        val deltas = mutableListOf<Float>()
        var ends = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                DragHandle(
                    onDragEnd = { ends++ },
                    orientation = Orientation.Vertical,
                    modifier = Modifier.testTag("handle").size(40.dp),
                ) { deltas += it }
            }
        }
        onNodeWithTag("handle").performTouchInput { swipeDown(startY = centerY, endY = centerY + 30f) }
        waitForIdle()
        assertTrue(deltas.sum() > 0f)
        assertEquals(1, ends)
    }
}
