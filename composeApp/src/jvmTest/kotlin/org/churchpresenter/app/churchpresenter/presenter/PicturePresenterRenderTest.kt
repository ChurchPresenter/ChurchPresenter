package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * What the picture output shows for each state of the current slot.
 *
 * The presenter has three outcomes and the operator only ever sees the projector, so a wrong one is
 * invisible from the desk: a real path renders the image, an empty slot says "No images" rather than
 * freezing on the last picture, and a path that cannot be decoded says "Failed to load image"
 * instead of a blank screen the operator reads as "it's working". Each is asserted on the node that
 * lands on screen — the image's content description, or the fallback text — so nothing races a fade.
 *
 * The valid-image case writes a real PNG to a temp file because the loader decodes actual bytes off
 * disk; the two failure cases need no file.
 */
@OptIn(ExperimentalTestApi::class)
class PicturePresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    /** The English resource strings the three states render (values/strings.xml). */
    private val noImages = "No images"
    private val failedToLoad = "Failed to load image"
    private val presentedImage = "Presented Image"

    @Test
    fun `a decodable image is put on screen`() {
        val png = File.createTempFile("cp-pic", ".png").apply {
            ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", this)
            deleteOnExit()
        }
        runComposeUiTest {
            setContent { Box(screen) { PicturePresenter(imagePath = png.absolutePath) } }
            onNodeWithContentDescription(presentedImage).assertExists("a valid image must actually render")
            onNodeWithText(failedToLoad, substring = true).assertDoesNotExist()
            onNodeWithText(noImages, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `an empty slot says there are no images`() = runComposeUiTest {
        setContent { Box(screen) { PicturePresenter(imagePath = null) } }
        onNodeWithText(noImages, substring = true)
            .assertExists("a null path must announce the empty slot, not freeze on the last picture")
    }

    @Test
    fun `an undecodable path reports the failure instead of a blank screen`() = runComposeUiTest {
        setContent { Box(screen) { PicturePresenter(imagePath = "/no/such/file/does-not-exist.png") } }
        onNodeWithText(failedToLoad, substring = true)
            .assertExists("a missing file must surface an error, not read as a working blank output")
    }
}
