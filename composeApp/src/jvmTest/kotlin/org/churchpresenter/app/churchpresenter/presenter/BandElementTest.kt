@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.ElementOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The band's stack-or-float split, which is what let the lower third have offsets at all.
 *
 * Two properties matter, and the second is the one a careless fix would lose:
 *
 *  * a positioned element leaves the column and is placed by its offset, and
 *  * **with nothing positioned, the column is the exact one this replaced** — no wrapper `Box`, and
 *    a short stack still bottom-aligned. That path is every existing document, so it has to be
 *    untouched rather than merely equivalent-looking.
 *
 * Measured in a 200x100 frame with 40x20 children, so the room is 160x80 and the numbers are exact.
 */
class BandElementTest {

    private class Placed(var x: Int = -1, var y: Int = -1)

    private fun element(offset: ElementOffset?, placed: Placed) = BandElement(offset) { fill ->
        Box(
            modifier = (if (fill) Modifier.fillMaxWidth().height(CHILD_H.dp) else Modifier.size(CHILD_W.dp, CHILD_H.dp))
                .onGloballyPositioned { coords ->
                    placed.x = coords.positionInRoot().x.toInt()
                    placed.y = coords.positionInRoot().y.toInt()
                },
        )
    }

    /**
     * [BibleBandColumn] in the container the presenter gives it: the band's own `BoxWithConstraints`,
     * whose `contentAlignment` is what places a column that wraps its own height. Passing that
     * alignment in is not incidental — without it the column sits at the top of the frame whatever
     * its `verticalArrangement` says, because a wrap-height column has no slack to arrange inside.
     */
    private fun layOutColumn(
        first: ElementOffset?,
        second: ElementOffset?,
        bottom: Boolean = true,
    ): Pair<Placed, Placed> {
        val a = Placed()
        val b = Placed()
        val alignment = if (bottom) Alignment.BottomCenter else Alignment.TopCenter
        runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
            setContent {
                Box(modifier = Modifier.size(FRAME_W.dp, FRAME_H.dp), contentAlignment = alignment) {
                    BibleBandColumn(
                        elements = listOf(element(first, a), element(second, b)),
                        verticalArrangement = if (bottom) Arrangement.Bottom else Arrangement.Top,
                        stackAlignment = alignment,
                    )
                }
            }
            waitForIdle()
        }
        return a to b
    }

    @Test
    fun `with nothing positioned the column stacks at the bottom as it always did`() {
        val (a, b) = layOutColumn(null, null)
        assertEquals(0, a.x, "a stacked element fills the width")
        assertEquals(0, b.x)
        // Two 20-high children bottom-aligned in a 100-high frame.
        assertEquals(FRAME_H - 2 * CHILD_H, a.y)
        assertEquals(FRAME_H - CHILD_H, b.y)
    }

    @Test
    fun `a positioned element is placed by its offset instead`() {
        // 0,0 is flush to the top-left, which no stacked element in a bottom-aligned column reaches.
        val (a, b) = layOutColumn(ElementOffset(xPercent = 0, yPercent = 0), null)
        assertEquals(0, a.x)
        assertEquals(0, a.y, "the positioned element went to the top")
        // And the one left behind takes the whole stack, not the second slot in it.
        assertEquals(FRAME_H - CHILD_H, b.y, "the stack closed up over the element that left")
    }

    @Test
    fun `a positioned element costs the stack no height`() {
        // The trade the whole feature rests on. Measured from the top, where closing up is visible:
        // bottom-aligned, the last element is flush to the bottom either way and says nothing.
        val (_, secondOfTwo) = layOutColumn(null, null, bottom = false)
        val (_, stackedAlone) = layOutColumn(ElementOffset(50, 50), null, bottom = false)
        assertEquals(CHILD_H, secondOfTwo.y, "two stacked: the second sits under the first")
        assertEquals(0, stackedAlone.y, "one stacked: it takes the top, the height the other freed")
        assertTrue(stackedAlone.y < secondOfTwo.y, "the stack should have closed up")
    }

    @Test
    fun `both positioned puts each at its own offset`() {
        val (a, b) = layOutColumn(ElementOffset(0, 0), ElementOffset(100, 100))
        assertEquals(0 to 0, a.x to a.y)
        assertEquals((FRAME_W - CHILD_W) to (FRAME_H - CHILD_H), b.x to b.y)
    }

    private fun layOutHalves(offset: ElementOffset?): Placed {
        val placed = Placed()
        runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
            setContent {
                Row(modifier = Modifier.size(FRAME_W.dp, FRAME_H.dp)) {
                    BibleBandHalf(listOf(element(offset, placed)))
                    BibleBandHalf(listOf(element(null, Placed())))
                }
            }
            waitForIdle()
        }
        return placed
    }

    @Test
    fun `an unpositioned half bottom-aligns in its own cell`() {
        val placed = layOutHalves(null)
        assertEquals(0, placed.x, "the left half starts at the left edge")
        assertEquals(FRAME_H - CHILD_H, placed.y, "a short half sits at the bottom of its cell")
    }

    @Test
    fun `a positioned half moves inside its own cell, not the whole band`() {
        // Flush right *of its half*, which is half the frame less the child -- reaching the frame's
        // own right edge would mean the offset was being applied against the wrong box.
        val placed = layOutHalves(ElementOffset(xPercent = 100, yPercent = 0))
        assertEquals(0, placed.y, "flush to the top of its cell")
        assertEquals(FRAME_W / 2 - CHILD_W, placed.x, "flush to the right of its own half")
    }

    private companion object {
        const val FRAME_W = 200
        const val FRAME_H = 100
        const val CHILD_W = 40
        const val CHILD_H = 20
    }
}
