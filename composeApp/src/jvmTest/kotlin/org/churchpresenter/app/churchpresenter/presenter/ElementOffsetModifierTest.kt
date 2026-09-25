@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
 * `Modifier.elementOffset`, which is what places a positioned element in #613.
 *
 * The contract is the whole reason the range is unsigned: both axes are a percentage of the room the
 * element has to move through -- the frame less its own size -- so **0 is flush to the start, 50 is
 * dead centre and 100 is flush to the end**, and 100 lands flush whatever size the element turns out
 * to be. The request was for negative values to reach a position the old range could not; widening
 * the range reaches all of them without anyone having to work out which way -3 moves a label.
 *
 * The containment is arithmetic rather than a check somewhere, and that is what makes the "warn the
 * operator when it goes off screen" question moot: no configured value can put it off screen.
 *
 * Measured in a 200x100 frame holding a 40x20 child, so the room is 160x80 and the numbers below are
 * exact rather than approximate.
 */
class ElementOffsetModifierTest {

    private fun placedAt(offset: ElementOffset?): Pair<Int, Int> {
        var x = -1
        var y = -1
        runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
            setContent {
                Box(modifier = Modifier.size(FRAME_W.dp, FRAME_H.dp)) {
                    Box(
                        modifier = Modifier
                            .elementOffset(offset)
                            .size(CHILD_W.dp, CHILD_H.dp)
                            .onGloballyPositioned { coords ->
                                val root = coords.positionInRoot()
                                x = root.x.toInt()
                                y = root.y.toInt()
                            },
                    )
                }
            }
            waitForIdle()
        }
        return x to y
    }

    @Test
    fun `zero puts the element flush against the left and top`() {
        assertEquals(0 to 0, placedAt(ElementOffset(xPercent = 0, yPercent = 0)))
    }

    @Test
    fun `fifty centres it on both axes`() {
        // Half of the 160x80 room, which is the centre of the frame for a child of this size.
        assertEquals(ROOM_W / 2 to ROOM_H / 2, placedAt(ElementOffset(xPercent = 50, yPercent = 50)))
    }

    @Test
    fun `a hundred puts it flush against the right and bottom`() {
        assertEquals(ROOM_W to ROOM_H, placedAt(ElementOffset(xPercent = 100, yPercent = 100)))
    }

    @Test
    fun `the two axes are independent`() {
        assertEquals(0 to ROOM_H, placedAt(ElementOffset(xPercent = 0, yPercent = 100)))
    }

    @Test
    fun `a value past the range is clamped rather than trusted`() {
        // Not reachable through the UI; reachable by hand-editing settings.json, which is the case
        // this guards. It must land flush, not somewhere outside the frame.
        assertEquals(ROOM_W to ROOM_H, placedAt(ElementOffset(xPercent = 400, yPercent = 999)))
    }

    @Test
    fun `null leaves the element exactly where the layout put it`() {
        // The default at every use site, and the reason nothing moves on upgrade: the modifier is a
        // no-op, so the element keeps whatever placement its parent gave it.
        assertEquals(0 to 0, placedAt(null))
    }

    @Test
    fun `no value can place the element outside the frame`() {
        // The containment claim, swept rather than asserted at three points.
        for (percent in 0..100 step 5) {
            val (x, y) = placedAt(ElementOffset(xPercent = percent, yPercent = percent))
            assertTrue(x in 0..ROOM_W, "x $x escaped at $percent%")
            assertTrue(y in 0..ROOM_H, "y $y escaped at $percent%")
        }
    }

    private companion object {
        const val FRAME_W = 200
        const val FRAME_H = 100
        const val CHILD_W = 40
        const val CHILD_H = 20

        /** The room the child has to move through, which every percentage is a fraction of. */
        const val ROOM_W = FRAME_W - CHILD_W
        const val ROOM_H = FRAME_H - CHILD_H
    }
}
