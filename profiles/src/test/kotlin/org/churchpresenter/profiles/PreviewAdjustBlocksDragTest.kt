@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.presenter.PresentedBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewAdjustBlocksDragTest {

    private class Log {
        val selected = mutableListOf<Int>()
        var shift by mutableStateOf(0 to 0)
        var refShift by mutableStateOf(10 to 10)
        var picks = 0
    }

    private fun frame(left: Int, top: Int, right: Int, bottom: Int) =
        AdjustFrame(left.dp, top.dp, right.dp, bottom.dp)

    private fun blocks(
        frames: List<AdjustFrame?>,
        selected: Int?,
        withShift: Boolean,
        reference: Boolean = false,
        referencePicked: Boolean = false,
        body: SkikoComposeUiTest.(Log) -> Unit,
    ) = runSkikoComposeUiTest(size = Size(600f, 400f), density = Density(1f)) {
        val log = Log()
        var picked by mutableStateOf(referencePicked)
        setContent {
            val targets = BlockTargets(
                kind = PresentedBlock.Kind.TRANSLATION,
                keys = frames.indices.map { "k$it" },
                selected = selected,
                onSelect = { log.selected += it },
                shift = if (withShift) Adjustable(log.shift) { log.shift = it } else null,
                reference = if (reference) {
                    ReferenceTarget(Adjustable(log.refShift) { log.refShift = it }, picked) {
                        log.picks++
                        picked = true
                    }
                } else {
                    null
                },
            )
            MaterialTheme {
                Box(Modifier.size(600.dp, 400.dp)) {
                    BlockOutlines(targets, frames, scale = 1f)
                    BlockGrips(targets, frame(200, 200, 260, 230), frame(180, 180, 280, 250), scale = 1f)
                }
            }
        }
        waitForIdle()
        body(log)
    }

    private fun SkikoComposeUiTest.drag(tag: String, by: Offset) {
        onNodeWithTag(tag).performTouchInput { swipe(center, center + by, durationMillis = 200) }
        waitForIdle()
    }

    @Test
    fun `a lone block that cannot move draws no handle`() =
        blocks(listOf(frame(0, 0, 300, 100)), selected = 0, withShift = false) {
            onNodeWithTag(adjustBlockTag(0)).assertDoesNotExist()
            onNodeWithTag(ADJUST_REFERENCE_TAG).assertDoesNotExist()
        }

    @Test
    fun `a lone block that can move is its own handle, and dragging it picks and moves it`() =
        blocks(listOf(frame(0, 0, 300, 100)), selected = null, withShift = true) { log ->
            drag(adjustBlockTag(0), Offset(80f, 0f))
            assertEquals(listOf(0), log.selected)
            assertTrue(log.shift.first > 0, "moved right: ${log.shift}")
        }

    @Test
    fun `clicking an unpicked block picks it, and a missing frame is skipped`() =
        blocks(listOf(frame(0, 0, 200, 100), null, frame(300, 0, 500, 100)), selected = 2, withShift = false) { log ->
            onNodeWithTag(adjustBlockTag(1)).assertDoesNotExist()
            onNodeWithTag(adjustBlockTag(0)).performClick()
            waitForIdle()
            assertEquals(listOf(0), log.selected)
            drag(adjustBlockTag(2), Offset(60f, 0f))
            assertEquals(listOf(0), log.selected, "the picked block is not picked again")
            assertEquals(0 to 0, log.shift, "with no move to make, a drag moves nothing")
        }

    @Test
    fun `the reference is picked by a click and kept inside its cell when dragged`() =
        blocks(listOf(frame(0, 0, 300, 100)), selected = 0, withShift = false, reference = true) { log ->
            onNodeWithTag(ADJUST_REFERENCE_TAG).performClick()
            waitForIdle()
            assertEquals(1, log.picks)
            drag(ADJUST_REFERENCE_TAG, Offset(150f, 150f))
            assertEquals(10 + 20 to 10 + 20, log.refShift, "clamped to the 20dp of room right and below")
            assertEquals(1, log.picks, "an already picked reference is not picked again")
            drag(ADJUST_REFERENCE_TAG, Offset(-150f, -150f))
            assertEquals(30 - 20 to 30 - 20, log.refShift, "and the 20dp of room left and above")
        }

    @Test
    fun `dragging an unpicked reference picks it, and with the reference picked a lone block is offered`() =
        blocks(listOf(frame(0, 0, 150, 100)), selected = 0, withShift = false, reference = true) { log ->
            onNodeWithTag(adjustBlockTag(0)).assertDoesNotExist()
            drag(ADJUST_REFERENCE_TAG, Offset(30f, 0f))
            assertEquals(1, log.picks)
            onNodeWithTag(adjustBlockTag(0)).performClick()
            waitForIdle()
            assertEquals(listOf(0), log.selected, "the verse's block is a click away again")
        }
}
