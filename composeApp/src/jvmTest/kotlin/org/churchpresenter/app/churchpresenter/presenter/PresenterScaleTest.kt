package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The factor the song and Bible presenters draw every stored size at.
 *
 * It takes Dp rather than pixels, which is the whole point: the same 1920x1080 output scales at 1.0
 * whether its density is 1 or 2, so the `.dp`/`.sp` it feeds are not multiplied by that density
 * twice. [PresenterHiDpiRenderTest] holds the rendered half of that.
 */
class PresenterScaleTest {

    @Test
    fun `the reference output draws at 1 to 1`() {
        assertEquals(1f, presenterScale(1920.dp, 1080.dp))
    }

    @Test
    fun `a bigger output scales up by the whole factor`() {
        assertEquals(2f, presenterScale(3840.dp, 2160.dp))
    }

    @Test
    fun `the smaller of the two axes wins`() {
        assertEquals(1f, presenterScale(3840.dp, 1080.dp), "an ultrawide is limited by its height")
        assertEquals(1f, presenterScale(1920.dp, 2160.dp), "a tall output is limited by its width")
    }

    @Test
    fun `the range is held at both ends`() {
        // 480x270 (the same 16:9 shape at a quarter size, ratio 0.25) used to sit here and get
        // floored up to the old 0.5 -- which was the same bug this file's own presets never showed:
        // MIN_PRESENTER_SCALE overriding a ratio that was already correct. 96x54 (ratio 0.05) is what
        // now actually needs the floor; PresenterBackgroundTest covers the floor not over-firing on a
        // real vertical output, which is the case that regressed.
        assertEquals(MIN_PRESENTER_SCALE, presenterScale(96.dp, 54.dp))
        assertEquals(MAX_PRESENTER_SCALE, presenterScale(7680.dp, 4320.dp))
    }
}
