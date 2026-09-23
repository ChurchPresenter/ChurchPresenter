package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.contentScale
import org.churchpresenter.settings.OutputScaleMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The Pictures tab's scale button, as the output draws it: a 4:3 picture on a 16:9 screen, once per
 * [OutputScaleMode]. See [OutputScaleFixture] for how two pixels tell the three apart.
 *
 * The picture is written to a real file because the presenter decodes it off disk -- and, outside
 * FIT, decodes it to cover the screen rather than fit inside it, which this also exercises.
 */
@OptIn(ExperimentalTestApi::class)
class PicturePresenterScaleTest {

    private lateinit var picture: File

    @BeforeTest
    fun writePicture() {
        picture = File.createTempFile("cp-scale", ".png")
        ImageIO.write(OutputScaleFixture.source(), "png", picture)
    }

    @AfterTest
    fun deletePicture() {
        picture.delete()
    }

    private fun drawnAs(mode: OutputScaleMode) = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("output").size(160.dp, 90.dp)) {
                PicturePresenter(imagePath = picture.absolutePath, contentScale = mode.contentScale)
            }
        }
        OutputScaleFixture.assertDrawnAs(mode, onNodeWithTag("output").captureToImage().toPixelMap())
    }

    @Test
    fun `fit shows the whole picture with bars at the sides`() = drawnAs(OutputScaleMode.FIT)

    @Test
    fun `fill covers the screen by cropping the top and bottom`() = drawnAs(OutputScaleMode.FILL)

    @Test
    fun `stretch covers the screen by squashing the picture`() = drawnAs(OutputScaleMode.STRETCH)
}
