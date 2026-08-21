@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class PicturePresenterTransitionsTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)
    private val presentedImage = "Presented Image"
    private val noImages = "No images"
    private val failedToLoad = "Failed to load image"

    private fun png(): File = File.createTempFile("cp-pic-transition", ".png").apply {
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", this)
        deleteOnExit()
    }

    @Test
    fun `pictureKeyAlpha follows the transition alpha except during a slide`() {
        assertEquals(0.4f, pictureKeyAlpha(AnimationType.CROSSFADE, 0.4f))
        assertEquals(0.4f, pictureKeyAlpha(AnimationType.FADE, 0.4f))
        assertEquals(0.4f, pictureKeyAlpha(AnimationType.NONE, 0.4f))
        assertEquals(1f, pictureKeyAlpha(AnimationType.SLIDE_LEFT, 0.4f))
        assertEquals(1f, pictureKeyAlpha(AnimationType.SLIDE_RIGHT, 0.4f))
    }

    @Test
    fun `key mode shows no image or text content`() = runComposeUiTest {
        val path = png().absolutePath
        setContent {
            Box(screen) {
                PicturePresenter(imagePath = path, outputRole = Constants.OUTPUT_ROLE_KEY)
            }
        }
        onNodeWithContentDescription(presentedImage).assertDoesNotExist()
        onNodeWithText(noImages, substring = true).assertDoesNotExist()
        onNodeWithText(failedToLoad, substring = true).assertDoesNotExist()
    }

    @Test
    fun `crossfade with a previous image renders both images at once`() = runComposeUiTest {
        val previous = png().absolutePath
        val current = png().absolutePath
        setContent {
            Box(screen) {
                PicturePresenter(
                    imagePath = current,
                    previousImagePath = previous,
                    animationType = AnimationType.CROSSFADE,
                    transitionAlpha = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedImage).assertCountEquals(2)
    }

    @Test
    fun `crossfade with no previous image falls back to a single image`() = runComposeUiTest {
        val current = png().absolutePath
        setContent {
            Box(screen) {
                PicturePresenter(imagePath = current, animationType = AnimationType.CROSSFADE)
            }
        }
        onAllNodesWithContentDescription(presentedImage).assertCountEquals(1)
    }

    @Test
    fun `slide left with a previous image renders both images at once`() = runComposeUiTest {
        val previous = png().absolutePath
        val current = png().absolutePath
        setContent {
            Box(screen) {
                PicturePresenter(
                    imagePath = current,
                    previousImagePath = previous,
                    animationType = AnimationType.SLIDE_LEFT,
                    slideOffset = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedImage).assertCountEquals(2)
    }

    @Test
    fun `slide right with a previous image renders both images at once`() = runComposeUiTest {
        val previous = png().absolutePath
        val current = png().absolutePath
        setContent {
            Box(screen) {
                PicturePresenter(
                    imagePath = current,
                    previousImagePath = previous,
                    animationType = AnimationType.SLIDE_RIGHT,
                    slideOffset = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedImage).assertCountEquals(2)
    }

    @Test
    fun `with no known window size, slide offsets and image scaling fall back to 1920x1080`() = runComposeUiTest {
        val previous = png().absolutePath
        val current = png().absolutePath
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides zeroSizeWindowInfo) {
                Box(screen) {
                    PicturePresenter(
                        imagePath = current,
                        previousImagePath = previous,
                        animationType = AnimationType.SLIDE_LEFT,
                        slideOffset = 0.5f,
                    )
                }
            }
        }
        onAllNodesWithContentDescription(presentedImage).assertCountEquals(2)
    }
}
