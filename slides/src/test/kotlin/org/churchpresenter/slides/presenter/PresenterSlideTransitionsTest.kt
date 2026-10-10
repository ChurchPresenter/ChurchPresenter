package org.churchpresenter.slides.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import org.jetbrains.skia.Image
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresenterSlideTransitionsTest {

    private val dir = tempDir("cp-presenter-transitions")
    private val red = solidImage(dir, "red.png", Color.RED)
    private val blue = solidImage(dir, "blue.png", Color.BLUE)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun bitmap(file: File): ImageBitmap = Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()

    private fun pictures(type: AnimationType, offset: Float = 0.5f, alpha: Float = 1f) = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp, 100.dp).testTag("out")) {
                PicturePresenter(
                    imagePath = blue.absolutePath,
                    previousImagePath = red.absolutePath,
                    transitionAlpha = alpha,
                    slideOffset = offset,
                    animationType = type,
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        waitUntil("both pictures drawn", 5_000) {
            onAllNodesWithContentDescription("Presented Image").fetchSemanticsNodes().size == 2
        }
        onNodeWithTag("out").captureToImage()
    }

    private fun slides(type: AnimationType, offset: Float = 0.5f, alpha: Float = 1f) = runComposeUiTest {
        val current = bitmap(blue)
        val previous = bitmap(red)
        setContent {
            Box(Modifier.size(200.dp, 100.dp).testTag("out")) {
                SlidePresenter(
                    slide = current,
                    previousSlide = previous,
                    transitionAlpha = alpha,
                    slideOffset = offset,
                    animationType = type,
                )
            }
        }
        onAllNodesWithContentDescription("Presented Slide").assertCountEquals(2)
        onNodeWithTag("out").captureToImage()
    }

    @Test
    fun `a picture sliding left brings both pictures on screen`() = pictures(AnimationType.SLIDE_LEFT)

    @Test
    fun `a picture sliding right brings both pictures on screen`() = pictures(AnimationType.SLIDE_RIGHT)

    @Test
    fun `a picture crossfading draws both pictures`() = pictures(AnimationType.CROSSFADE, alpha = 0.5f)

    @Test
    fun `a slide sliding left brings both slides on screen`() = slides(AnimationType.SLIDE_LEFT)

    @Test
    fun `a slide sliding right brings both slides on screen`() = slides(AnimationType.SLIDE_RIGHT)

    @Test
    fun `a slide crossfading draws both slides`() = slides(AnimationType.CROSSFADE, alpha = 0.5f)

    @Test
    fun `a finished slide-left shows only the new picture`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp, 100.dp).testTag("out")) {
                PicturePresenter(
                    imagePath = blue.absolutePath,
                    previousImagePath = red.absolutePath,
                    slideOffset = 1f,
                    animationType = AnimationType.SLIDE_LEFT,
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
        waitUntil("the pictures drawn", 5_000) {
            onAllNodesWithContentDescription("Presented Image").fetchSemanticsNodes().isNotEmpty()
        }
        val pixels = onNodeWithTag("out").captureToImage().toPixelMap()
        val middle = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(middle.blue > 0.8f && middle.red < 0.2f, "expected blue, got $middle")
    }

    @Test
    fun `a key output of a slide is drawn without the slide itself`() = runComposeUiTest {
        val current = bitmap(blue)
        setContent {
            Box(Modifier.size(200.dp, 100.dp)) {
                SlidePresenter(slide = current, outputRole = Constants.OUTPUT_ROLE_KEY, transitionAlpha = 0.5f)
            }
        }
        onAllNodesWithContentDescription("Presented Slide").assertCountEquals(0)
    }

    @Test
    fun `an output with no slide yet draws no slide`() = runComposeUiTest {
        setContent { Box(Modifier.size(200.dp, 100.dp)) { SlidePresenter(slide = null) } }
        assertEquals(0, onAllNodesWithContentDescription("Presented Slide").fetchSemanticsNodes().size)
    }

    private fun firstShown(type: AnimationType) = runComposeUiTest {
        val current = bitmap(blue)
        setContent {
            Box(Modifier.size(200.dp, 100.dp)) {
                Box { PicturePresenter(imagePath = blue.absolutePath, animationType = type) }
                Box { SlidePresenter(slide = current, animationType = type) }
            }
        }
        waitUntil("the picture drawn", 5_000) {
            onAllNodesWithContentDescription("Presented Image").fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithContentDescription("Presented Image").assertCountEquals(1)
        onAllNodesWithContentDescription("Presented Slide").assertCountEquals(1)
    }

    @Test
    fun `a first slide-left has nothing to slide out`() = firstShown(AnimationType.SLIDE_LEFT)

    @Test
    fun `a first slide-right has nothing to slide out`() = firstShown(AnimationType.SLIDE_RIGHT)
}
