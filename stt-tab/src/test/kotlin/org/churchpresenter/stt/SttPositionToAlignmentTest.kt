package org.churchpresenter.stt

import androidx.compose.ui.Alignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where on the screen the captions sit, for each position the settings can hold.
 *
 * A thirteen-arm `when` reached only through a full presenter render, which is why it went untested
 * — every arm needs its own composition to distinguish it, and the resulting picture differs only in
 * where the text landed. Called directly, each arm is one line.
 *
 * The three aliases and the fallback are the point: `BOTTOM`, `TOP` and `MIDDLE` are older settings
 * values that must keep mapping onto the same places as their `*_CENTER` equivalents, and anything
 * unrecognised has to land somewhere sensible rather than throw in front of a congregation.
 */
class SttPositionToAlignmentTest {

    @Test
    fun `the nine-box grid maps to its own corner`() {
        assertEquals(Alignment.TopStart, sttPositionToAlignment(Constants.TOP_LEFT))
        assertEquals(Alignment.TopCenter, sttPositionToAlignment(Constants.TOP_CENTER))
        assertEquals(Alignment.TopEnd, sttPositionToAlignment(Constants.TOP_RIGHT))
        assertEquals(Alignment.CenterStart, sttPositionToAlignment(Constants.CENTER_LEFT))
        assertEquals(Alignment.Center, sttPositionToAlignment(Constants.CENTER))
        assertEquals(Alignment.CenterEnd, sttPositionToAlignment(Constants.CENTER_RIGHT))
        assertEquals(Alignment.BottomStart, sttPositionToAlignment(Constants.BOTTOM_LEFT))
        assertEquals(Alignment.BottomCenter, sttPositionToAlignment(Constants.BOTTOM_CENTER))
        assertEquals(Alignment.BottomEnd, sttPositionToAlignment(Constants.BOTTOM_RIGHT))
    }

    @Test
    fun `the older bare names still mean what they used to`() {
        assertEquals(Alignment.BottomCenter, sttPositionToAlignment(Constants.BOTTOM))
        assertEquals(Alignment.TopCenter, sttPositionToAlignment(Constants.TOP))
        assertEquals(Alignment.Center, sttPositionToAlignment(Constants.MIDDLE))
    }

    @Test
    fun `an unrecognised position falls back to the bottom rather than throwing`() {
        assertEquals(Alignment.BottomCenter, sttPositionToAlignment("nowhere in particular"))
        assertEquals(Alignment.BottomCenter, sttPositionToAlignment(""))
    }
}
