@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.ui.settle
import org.churchpresenter.lottiegen.ui.showDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LottieNumberFieldTest {

    @Test
    fun `a partial number is never committed and a complete one in range is`() {
        assertNull(NumberInput.live("", 100.0, 7680.0, whole = true))
        assertNull(NumberInput.live("1", 100.0, 7680.0, whole = true))
        assertNull(NumberInput.live("19", 100.0, 7680.0, whole = true))
        assertEquals(1920.0, NumberInput.live("1920", 100.0, 7680.0, whole = true))
        assertNull(NumberInput.live("99999", 100.0, 7680.0, whole = true))
        assertNull(NumberInput.live("1920.5", 100.0, 7680.0, whole = true))
    }

    @Test
    fun `a comma is a decimal mark and nothing else is a number`() {
        assertEquals(2.5, NumberInput.parse("2,5"))
        assertEquals(2.5, NumberInput.parse(" 2.5 "))
        assertNull(NumberInput.parse(""))
        assertNull(NumberInput.parse("-"))
        assertNull(NumberInput.parse("NaN"))
        assertNull(NumberInput.parse("Infinity"))
    }

    @Test
    fun `finishing clamps and rounds, and a non-number reverts`() {
        assertEquals(100.0, NumberInput.finish("1", 100.0, 7680.0, whole = true))
        assertEquals(7680.0, NumberInput.finish("99999", 100.0, 7680.0, whole = true))
        assertEquals(1921.0, NumberInput.finish("1920.6", 100.0, 7680.0, whole = true))
        assertEquals(0.5, NumberInput.finish("0,1", 0.5, 20.0, whole = false))
        assertNull(NumberInput.finish("", 100.0, 7680.0, whole = true))
    }

    @Test
    fun `a number shows without a trailing point zero`() {
        assertEquals("2", NumberInput.format(2.0))
        assertEquals("2.5", NumberInput.format(2.5))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.input(): SemanticsNodeInteraction =
        onAllNodes(hasSetTextAction()).onFirst()

    @Test
    fun `the field keeps what is typed, commits a complete number live and clamps on Enter`() =
        runDesktopComposeUiTest(600, 300) {
            var width by mutableStateOf(1280)
            val commits = mutableListOf<Int>()
            showDark(400.dp, 200.dp) {
                LottieIntField("W", width, { width = it; commits += it }, 100..7680)
            }
            input().performTextReplacement("")
            settle()
            input().assertTextEquals("")
            assertEquals(1280, width)

            input().performTextReplacement("1")
            settle()
            input().assertTextEquals("1")
            assertEquals(1280, width)

            input().performTextReplacement("1920")
            settle()
            input().assertTextEquals("1920")
            assertEquals(1920, width)

            input().performTextReplacement("99999")
            settle()
            input().assertTextEquals("99999")
            input().performImeAction()
            settle()
            assertEquals(7680, width)
            input().assertTextEquals("7680")
        }

    @Test
    fun `Enter on text that is not a number puts the last value back`() = runDesktopComposeUiTest(600, 300) {
        var seconds by mutableStateOf(2f)
        showDark(400.dp, 200.dp) { LottieFloatField("S", seconds, { seconds = it }, 0.5f..20f) }
        input().assertTextEquals("2")
        input().performTextReplacement("")
        input().performImeAction()
        settle()
        assertEquals(2f, seconds)
        input().assertTextEquals("2")
    }

    @Test
    fun `a decimal comma is accepted and a trailing point is left alone while typing`() =
        runDesktopComposeUiTest(600, 300) {
            var seconds by mutableStateOf(2f)
            showDark(400.dp, 200.dp) { LottieFloatField("S", seconds, { seconds = it }, 0.5f..20f) }
            input().performTextReplacement("3,")
            settle()
            input().assertTextEquals("3,")
            assertEquals(3f, seconds)
            input().performTextReplacement("3,5")
            settle()
            assertEquals(3.5f, seconds)
            input().assertTextEquals("3,5")
        }

    @Test
    fun `a value changed from outside shows in the field`() = runDesktopComposeUiTest(600, 300) {
        var width by mutableStateOf(1280)
        showDark(400.dp, 200.dp) { LottieIntField("W", width, { width = it }, 100..7680) }
        width = 1920
        settle()
        input().assertTextEquals("1920")
    }

    @Test
    fun `leaving the field clamps what was typed`() = runDesktopComposeUiTest(600, 400) {
        var width by mutableStateOf(1280)
        val other = FocusRequester()
        showDark(400.dp, 300.dp) {
            Column {
                LottieIntField("W", width, { width = it }, 100..7680)
                LottieTextField("", {}, modifier = Modifier.focusRequester(other))
            }
        }
        input().performClick()
        input().performTextReplacement("5")
        settle()
        other.requestFocus()
        settle()
        assertEquals(100, width)
        input().assertTextEquals("100")
    }
}
