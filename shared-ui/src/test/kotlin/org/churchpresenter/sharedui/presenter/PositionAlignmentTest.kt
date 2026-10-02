package org.churchpresenter.sharedui.presenter

import androidx.compose.ui.Alignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionAlignmentTest {

    @Test
    fun `every position a caption can be given lands where it says`() {
        val expected = mapOf(
            Constants.TOP_LEFT to Alignment.TopStart,
            Constants.TOP_CENTER to Alignment.TopCenter,
            Constants.TOP_RIGHT to Alignment.TopEnd,
            Constants.CENTER_LEFT to Alignment.CenterStart,
            Constants.CENTER to Alignment.Center,
            Constants.CENTER_RIGHT to Alignment.CenterEnd,
            Constants.BOTTOM_LEFT to Alignment.BottomStart,
            Constants.BOTTOM_CENTER to Alignment.BottomCenter,
            Constants.BOTTOM_RIGHT to Alignment.BottomEnd,
            Constants.BOTTOM to Alignment.BottomCenter,
            Constants.TOP to Alignment.TopCenter,
            Constants.MIDDLE to Alignment.Center,
        )
        expected.forEach { (position, alignment) ->
            assertEquals(alignment, sttPositionToAlignment(position), position)
        }
    }

    @Test
    fun `a position this build does not know puts the caption at the bottom`() {
        assertEquals(Alignment.BottomCenter, sttPositionToAlignment("Somewhere Else"))
    }
}
