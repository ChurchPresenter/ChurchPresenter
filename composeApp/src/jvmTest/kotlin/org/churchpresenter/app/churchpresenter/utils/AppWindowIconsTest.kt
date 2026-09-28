package org.churchpresenter.app.churchpresenter.utils

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app icon's window frames: every size is shipped, and each is the size it is named for.
 *
 * A missing or misnamed frame would not fail anywhere else -- the window would quietly fall back to
 * scaling another -- and the blurry small icon this replaced is exactly what that looks like.
 */
class AppWindowIconsTest {

    @Test
    fun `every size is shipped`() {
        assertEquals(AppWindowIcons.SIZES.size, AppWindowIcons.frames.size)
    }

    @Test
    fun `each frame is the size it is named for, and has transparency`() {
        AppWindowIcons.frames.zip(AppWindowIcons.SIZES).forEach { (image, size) ->
            val frame = image as BufferedImage
            assertEquals(size, frame.width, "icon-$size.png width")
            assertEquals(size, frame.height, "icon-$size.png height")
            assertEquals(true, frame.colorModel.hasAlpha(), "icon-$size.png keeps its rounded corners transparent")
        }
    }
}
