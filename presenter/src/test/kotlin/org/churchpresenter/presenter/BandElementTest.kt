@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.presenter

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
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The band's stack-or-box split: a boxed element leaves the column -- the box layer draws it -- and
 * with nothing boxed the column is the one it always was, a short stack still bottom-aligned.
 *
 * Measured in a 200x100 frame with 40x20 children, so the numbers are exact.
 */
class BandElementTest {

    private class Placed(var x: Int = -1, var y: Int = -1)

    private fun element(boxed: Boolean, placed: Placed) = BandElement(boxed) { fill ->
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
        first: Boolean,
        second: Boolean,
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
                    )
                }
            }
            waitForIdle()
        }
        return a to b
    }

    @Test
    fun `with nothing boxed the column stacks at the bottom as it always did`() {
        val (a, b) = layOutColumn(first = false, second = false)
        assertEquals(0, a.x, "a stacked element fills the width")
        assertEquals(0, b.x)
        // Two 20-high children bottom-aligned in a 100-high frame.
        assertEquals(FRAME_H - 2 * CHILD_H, a.y)
        assertEquals(FRAME_H - CHILD_H, b.y)
    }

    @Test
    fun `a boxed element leaves the column and costs the stack no height`() {
        // Measured from the top, where closing up is visible: bottom-aligned, the last element is
        // flush to the bottom either way and says nothing.
        val (_, secondOfTwo) = layOutColumn(first = false, second = false, bottom = false)
        val (boxed, stackedAlone) = layOutColumn(first = true, second = false, bottom = false)
        assertEquals(-1, boxed.y, "the boxed element is not drawn in the column")
        assertEquals(CHILD_H, secondOfTwo.y, "two stacked: the second sits under the first")
        assertEquals(0, stackedAlone.y, "one stacked: it takes the top, the height the other freed")
        assertTrue(stackedAlone.y < secondOfTwo.y, "the stack should have closed up")
    }

    private fun layOutHalves(): Placed {
        val placed = Placed()
        runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
            setContent {
                Row(modifier = Modifier.size(FRAME_W.dp, FRAME_H.dp)) {
                    BibleBandHalf(listOf(element(false, placed)))
                    BibleBandHalf(listOf(element(false, Placed())))
                }
            }
            waitForIdle()
        }
        return placed
    }

    @Test
    fun `an unboxed half bottom-aligns in its own cell`() {
        val placed = layOutHalves()
        assertEquals(0, placed.x, "the left half starts at the left edge")
        assertEquals(FRAME_H - CHILD_H, placed.y, "a short half sits at the bottom of its cell")
    }

    private companion object {
        const val FRAME_W = 200
        const val FRAME_H = 100
        const val CHILD_W = 40
        const val CHILD_H = 20
    }
}
