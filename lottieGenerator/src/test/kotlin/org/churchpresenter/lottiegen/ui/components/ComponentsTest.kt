@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.lottiegen.ui.Hosted
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.clickDescription
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.node
import org.churchpresenter.lottiegen.ui.settle
import org.churchpresenter.lottiegen.ui.showDark
import org.churchpresenter.lottiegen.ui.tapBelow
import org.churchpresenter.lottiegen.ui.typeLast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComponentsTest {

    @Test
    fun `the colour picker converts every hue sextant and confirms what was typed`() =
        runDesktopComposeUiTest(800, 900) {
        var picked = ""
        var open by mutableStateOf(true)
        showDark(800.dp, 900.dp) {
            if (open) {
                Hosted(picked, { picked = it; open = false }) { _, set ->
                    ColorPickerDialog("#00FF00", { open = false }, set)
                }
            }
        }
        var current = "#00FF00"
        for (hex in listOf("#FFFF00", "#00FFFF", "#0000FF", "#FF00FF", "#FF0000", "#808080", "#000000")) {
            typeLast(current, hex)
            current = hex
        }
        typeLast(current, "zz")
        typeLast("zz", "#")
        typeLast("#", "#123456")
        click(Strings.ok)
        assertEquals("#123456", picked)
        assertFalse(open)
    }

    @Test
    fun `the picker's panel and hue bar pick by pointer, and a recent colour is offered back`() =
        runDesktopComposeUiTest(800, 900) {
            var picked = ""
            var open by mutableStateOf(true)
            showDark(800.dp, 900.dp) {
                if (open) ColorPickerDialog("#FF0000", { open = false }, { picked = it })
            }
            tapBelow(Strings.chooseColor, 120.dp, 0.5f)
            node(Strings.chooseColor).performTouchInput {
                val touch = this
                touch.swipe(Offset(width * 0.2f, height + 40.dp.toPx()), Offset(width * 0.8f, height + 200.dp.toPx()))
            }
            settle()
            node(Strings.chooseColor).performTouchInput {
                val touch = this
                val y = height + (14 + 260 + 14 + 12).dp.toPx()
                touch.click(Offset(width * 0.6f, y))
                touch.swipe(Offset(width * 0.1f, y), Offset(width * 0.9f, y))
            }
            settle()
            val typed = onAllNodes(hasSetTextAction()).onFirst().fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            assertNotEquals("#FF0000", typed)
            click(Strings.ok)
            assertEquals(typed, picked)

            open = true
            settle()
            assertTrue(hasNode(Strings.recent))
            tapBelow(Strings.recent, 14.dp, 0.02f)
            click(Strings.cancelBtn)
            assertFalse(open)
        }

    @Test
    fun `a stepped slider snaps, a disabled one ignores the pointer`() = runDesktopComposeUiTest(600, 300) {
        var stepped by mutableStateOf(0f)
        var locked by mutableStateOf(0.5f)
        var flat by mutableStateOf(1f)
        showDark(600.dp, 300.dp) {
            Column {
                Text("stepped")
                Hosted(stepped, { stepped = it }) { v, set ->
                    LottieSlider(v, set, 0f..1f, Modifier.width(400.dp), steps = 3, trackColor = Tokens.CanvasBg)
                }
                Text("locked")
                LottieSlider(locked, { locked = it }, 0f..1f, Modifier.width(400.dp), enabled = false)
                Text("flat")
                LottieSlider(flat, { flat = it }, 1f..1f, Modifier.width(400.dp))
            }
        }
        tapBelow("stepped", 10.dp, 0.0f)
        node("stepped").performTouchInput {
            val touch = this
            touch.swipe(Offset(10f, height + 10.dp.toPx()), Offset(300.dp.toPx(), height + 10.dp.toPx()))
        }
        settle()
        assertTrue(stepped in listOf(0.25f, 0.5f, 0.75f, 1f), "snapped to $stepped")
        tapBelow("locked", 10.dp, 0.0f)
        assertEquals(0.5f, locked)
        tapBelow("flat", 10.dp, 0.5f)
        assertEquals(1f, flat)
    }

    @Test
    fun `text fields show their states, and widgets answer clicks`() = runDesktopComposeUiTest(600, 600) {
        var edits = 0
        var text by mutableStateOf("")
        showDark(600.dp, 600.dp) {
            Column {
                EditIconButton({ edits += 10 })
                SegmentedButtons(listOf("One", "Two"), -1, { edits += 100 })
                LottieTextField("off", {}, label = "Disabled", enabled = false, readOnly = true)
                LottieTextField("bad", {}, label = "Broken", isError = true, singleLine = false)
                LottieTextField(
                    text, { text = it; edits++ }, singleLine = false,
                    placeholder = { Text("type here") }, trailingIcon = { Text("end") },
                )
            }
        }
        assertTrue(hasNode("DISABLED") && hasNode("BROKEN") && hasNode("type here") && hasNode("end"))
        typeLast("", "typed")
        assertEquals("typed", text)
        assertFalse(hasNode("type here"))
        clickDescription("Edit")
        click("Two")
        assertEquals(111, edits)
    }
}
