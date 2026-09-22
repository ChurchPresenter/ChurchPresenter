package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.SharedVideoOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MediaPresenterRenderTest {

    @AfterTest
    fun clearSharedFrame() {
        SharedVideoOutput.frame.value = null
    }

    private fun loadedViewModel() = MediaViewModel().apply {
        loadMedia("file:///tmp/clip.mp4", Constants.MEDIA_TYPE_LOCAL)
        play()
    }

    @Test
    fun `key output role renders solid white regardless of the view model`() = runComposeUiTest {
        setContent {
            MediaPresenter(
                modifier = Modifier.testTag("media").size(40.dp),
                outputRole = Constants.OUTPUT_ROLE_KEY,
            )
        }
        val pixel = onNodeWithTag("media").captureToImage().toPixelMap()[20, 20]
        assertEquals(1f, pixel.red)
        assertEquals(1f, pixel.green)
        assertEquals(1f, pixel.blue)
    }

    @Test
    fun `no media view model renders nothing`() = runComposeUiTest {
        setContent {
            MediaPresenter(modifier = Modifier.testTag("media"), outputRole = Constants.OUTPUT_ROLE_NORMAL)
        }
        onNodeWithTag("media").assertDoesNotExist()
    }

    @Test
    fun `becoming invisible pauses the view model`() = runComposeUiTest {
        val viewModel = loadedViewModel()

        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                MediaPresenter(isVisible = false)
            }
        }

        assertFalse(viewModel.isPlaying)
    }

    @Test
    fun `loaded and visible shows the shared video frame`() = runComposeUiTest {
        val bitmap = ImageBitmap(4, 4)
        Canvas(bitmap).drawRect(Rect(0f, 0f, 4f, 4f), Paint().apply { color = Color.Green })
        SharedVideoOutput.frame.value = bitmap
        val viewModel = loadedViewModel()

        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                MediaPresenter(modifier = Modifier.testTag("media").size(40.dp), isVisible = true)
            }
        }

        val pixel = onNodeWithTag("media").captureToImage().toPixelMap()[20, 20]
        assertEquals(0f, pixel.red)
        assertEquals(1f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    @Test
    fun `not loaded shows the black backdrop instead of the shared frame`() = runComposeUiTest {
        val bitmap = ImageBitmap(4, 4)
        Canvas(bitmap).drawRect(Rect(0f, 0f, 4f, 4f), Paint().apply { color = Color.Green })
        SharedVideoOutput.frame.value = bitmap
        val viewModel = MediaViewModel()

        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                MediaPresenter(modifier = Modifier.testTag("media").size(40.dp), isVisible = true)
            }
        }

        val pixel = onNodeWithTag("media").captureToImage().toPixelMap()[20, 20]
        assertEquals(0f, pixel.red)
        assertEquals(0f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    @Test
    fun `an active subtitle cue draws the overlay when showSubtitles is on`() = runComposeUiTest {
        val temp = kotlin.io.path.createTempFile(suffix = ".srt").toFile()
        temp.writeText("1\n00:00:00,000 --> 00:00:10,000\nHello\n")
        val viewModel = loadedViewModel().apply { setSubtitleFile(temp.absolutePath) }

        try {
            setContent {
                CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                    MediaPresenter(
                        modifier = Modifier.testTag("media").size(200.dp),
                        isVisible = true,
                        showSubtitles = true,
                    )
                }
            }

            val colors = onNodeWithTag("media").captureToImage().toPixelMap()
            // The default MediaSettings card background is opaque, so some pixel in the frame
            // must differ from the plain black backdrop once the overlay draws.
            assertFalse(
                (0 until colors.width step 4).flatMap { x -> (0 until colors.height step 4).map { y -> colors[x, y] } }
                    .all { it == Color.Black },
                "the subtitle card should have drawn something other than the plain black backdrop",
            )
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `an active subtitle cue draws nothing when showSubtitles is off`() = runComposeUiTest {
        val temp = kotlin.io.path.createTempFile(suffix = ".srt").toFile()
        temp.writeText("1\n00:00:00,000 --> 00:00:10,000\nHello\n")
        val viewModel = loadedViewModel().apply { setSubtitleFile(temp.absolutePath) }

        try {
            setContent {
                CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                    MediaPresenter(
                        modifier = Modifier.testTag("media").size(200.dp),
                        isVisible = true,
                        showSubtitles = false,
                    )
                }
            }

            val colors = onNodeWithTag("media").captureToImage().toPixelMap()
            assertTrue(
                (0 until colors.width step 4).flatMap { x -> (0 until colors.height step 4).map { y -> colors[x, y] } }
                    .all { it == Color.Black },
                "showSubtitles = false must draw nothing but the plain black backdrop",
            )
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `not visible shows the black backdrop instead of the shared frame`() = runComposeUiTest {
        val bitmap = ImageBitmap(4, 4)
        Canvas(bitmap).drawRect(Rect(0f, 0f, 4f, 4f), Paint().apply { color = Color.Green })
        SharedVideoOutput.frame.value = bitmap
        val viewModel = loadedViewModel()

        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides viewModel) {
                MediaPresenter(modifier = Modifier.testTag("media").size(40.dp), isVisible = false)
            }
        }

        val pixel = onNodeWithTag("media").captureToImage().toPixelMap()[20, 20]
        assertEquals(0f, pixel.red)
        assertEquals(0f, pixel.green)
        assertEquals(0f, pixel.blue)
    }
}
