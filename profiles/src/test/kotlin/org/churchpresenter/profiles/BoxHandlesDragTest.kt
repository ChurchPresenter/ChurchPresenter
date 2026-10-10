@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoxHandlesDragTest {

    private val start = mapOf(
        "a" to TextBox(enabled = true, xPercent = 10f, yPercent = 10f, widthPercent = 30f, heightPercent = 30f),
        "b" to TextBox(enabled = true, xPercent = 30f, yPercent = 30f, widthPercent = 30f, heightPercent = 30f),
        "c" to TextBox(enabled = true, xPercent = 70f, yPercent = 70f, widthPercent = 20f, heightPercent = 20f),
    )

    private fun handles(
        snap: Boolean,
        selected: String? = null,
        body: SkikoComposeUiTest.(Map<String, TextBox>, List<String>) -> Unit,
    ) = runSkikoComposeUiTest(size = Size(1000f, 800f), density = Density(1f)) {
        val boxes = mutableStateMapOf<String, TextBox>().apply { putAll(start) }
        val picks = mutableListOf<String>()
        setContent {
            val targets = BoxTargets(
                handles = boxes.keys.sorted().map { key ->
                    BoxHandle(key, boxes.getValue(key), onChange = { boxes[key] = it }, onPick = { picks += key })
                },
                selected = selected,
                options = TextBoxOptions(snap = snap),
            )
            MaterialTheme {
                Box(Modifier.size(1000.dp, 800.dp).padding(200.dp)) {
                    BoxHandles(targets, Rect(0f, 0f, 480f, 270f), scale = 0.25f)
                }
            }
        }
        waitForIdle()
        body(boxes, picks)
    }

    private fun SkikoComposeUiTest.drag(tag: String, by: Offset) {
        onNodeWithTag(tag).performTouchInput { swipe(center, center + by, durationMillis = 200) }
        waitForIdle()
    }

    @Test
    fun `with nothing picked, no box has move handles until one is clicked`() = handles(snap = false) { boxes, picks ->
        onNodeWithTag(ADJUST_BOX_MOVE_TAG).assertDoesNotExist()
        onNodeWithTag(adjustBoxTag("c")).performClick()
        waitForIdle()
        assertEquals(listOf("c"), picks)
        drag(ADJUST_BOX_MOVE_TAG, Offset(-40f, -30f))
        val moved = boxes.getValue("c")
        assertTrue(moved.xPercent < 70f && moved.yPercent < 70f, "moved up and left: $moved")
        assertEquals(20f, moved.widthPercent)
    }

    @Test
    fun `every grip of the picked box resizes it on its own edges`() =
        handles(snap = false, selected = "c") { boxes, _ ->
        BoxGrip.entries.forEach { grip ->
            val before = boxes.getValue("c")
            drag(adjustBoxGripTag(grip), Offset(grip.dx * 20f, grip.dy * 20f))
            val after = boxes.getValue("c")
            if (grip.dx != 0) assertTrue(after.widthPercent > before.widthPercent, "$grip widens: $after")
            if (grip.dx == 0) assertEquals(before.widthPercent, after.widthPercent, "$grip keeps the width")
            if (grip.dy != 0) assertTrue(after.heightPercent > before.heightPercent, "$grip heightens: $after")
            if (grip.dy == 0) assertEquals(before.heightPercent, after.heightPercent, "$grip keeps the height")
        }
    }

    @Test
    fun `a snapping box lands where the same drag unsnapped would, snapped to the other boxes`() {
        var free: TextBox? = null
        handles(snap = false, selected = "c") { boxes, _ ->
            drag(ADJUST_BOX_MOVE_TAG, Offset(-30f, 0f))
            free = boxes.getValue("c")
        }
        handles(snap = true, selected = "c") { boxes, _ ->
            drag(ADJUST_BOX_MOVE_TAG, Offset(-30f, 0f))
            val (across, down) = snapLines(listOf(start.getValue("a"), start.getValue("b")))
            assertEquals(free!!.snappedTo(across, down), boxes.getValue("c"))
        }
    }

    @Test
    fun `a box picked by the rows has no click of its own, and the others still do`() =
        handles(snap = false, selected = "a") { _, picks ->
            onNodeWithTag(adjustBoxTag("a")).performClick()
            waitForIdle()
            assertEquals(emptyList(), picks)
            onNodeWithTag(adjustBoxTag("c")).performClick()
            waitForIdle()
            assertEquals(listOf("c"), picks)
        }
}
