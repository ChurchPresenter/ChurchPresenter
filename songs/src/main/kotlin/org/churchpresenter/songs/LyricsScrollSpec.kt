package org.churchpresenter.songs

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * How the lyrics box answers a request to scroll something into view. From the press that focuses
 * the field until the next key press it holds still: taking focus scrolls the field's old cursor --
 * line 1, until the press moves it -- into view, which would throw the box back to the top under the
 * click and put the cursor on whatever line the release then lands on. A key press ends the hold.
 */
internal class LyricsScrollSpec : BringIntoViewSpec {
    var holdStill = false

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (holdStill) 0f else super.calculateScrollDistance(offset, size, containerSize)
}
