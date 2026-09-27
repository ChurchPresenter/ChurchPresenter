package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The arithmetic behind the Adjust handles: where a block snaps, how wide it is dragged, its box. */
class PreviewAdjustMathTest {

    @Test
    fun `a release near a guide snaps to it, and one between guides does not`() {
        assertEquals(Constants.TOP, snapFor(0.15f))
        assertEquals(Constants.MIDDLE, snapFor(0.45f))
        assertEquals(Constants.BOTTOM, snapFor(0.95f))
        assertNull(snapFor(0.3f))
        assertNull(snapFor(0.7f))
    }

    @Test
    fun `each alignment rests on its own guide, and an unknown one in the middle`() {
        assertEquals(snapFor(restingFraction(Constants.TOP)), Constants.TOP)
        assertEquals(snapFor(restingFraction(Constants.BOTTOM)), Constants.BOTTOM)
        assertEquals(restingFraction(Constants.MIDDLE), restingFraction("sideways"))
    }

    @Test
    fun `a centred box widens on both sides, an off-centre one on the side dragged`() {
        val centred = ContentRegion(widthPercent = 80)
        assertEquals(90, draggedWidth(centred, 80, 5f, rightSide = true))
        assertEquals(90, draggedWidth(centred, 80, -5f, rightSide = false))
        val offCentre = ContentRegion(xOffsetPercent = 30, widthPercent = 80)
        assertEquals(85, draggedWidth(offCentre, 80, 5f, rightSide = true))
        assertEquals(ContentRegion.WIDTH_RANGE.first, draggedWidth(centred, 80, -90f, rightSide = true))
        assertEquals(ContentRegion.WIDTH_RANGE.last, draggedWidth(centred, 80, 90f, rightSide = true))
    }

    @Test
    fun `the content box sits inside the frame by its width and offset`() {
        val frame = AdjustFrame(0.dp, 0.dp, 200.dp, 100.dp)
        assertEquals(frame, innerBox(frame, null))
        val centred = innerBox(frame, ContentRegion(widthPercent = 50))
        assertEquals(50.dp, centred.left)
        assertEquals(150.dp, centred.right)
        val right = innerBox(frame, ContentRegion(xOffsetPercent = 100, widthPercent = 50))
        assertEquals(200.dp, right.right)
        assertEquals(0.dp, AdjustFrame(10.dp, 10.dp, 5.dp, 5.dp).width)
    }
}
