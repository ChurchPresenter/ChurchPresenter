package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.core.models.scene.SceneSource

/**
 * A scene's picture source: read off the event thread, drawn once read, a placeholder for a file
 * that is missing or not a picture, and nothing at all while the read is under way.
 */
@OptIn(ExperimentalTestApi::class)
class SceneImageSourceTest {

    private companion object {
        /** When a picture read that never lands is reported; the waits end on the read itself. */
        const val READ_TIMEOUT_MS = 5_000L
    }

    // A picture is read off the event thread, so each of these waits for the read to land.
    private fun ComposeUiTest.awaitPicture(name: String) =
        waitUntil("the picture to be drawn", READ_TIMEOUT_MS) {
            onAllNodesWithContentDescription(name).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.awaitNotFound() =
        waitUntil("the not-found placeholder", READ_TIMEOUT_MS) {
            onAllNodesWithText("Image not found").fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun `a picture still being read draws neither the picture nor the not-found placeholder`() = runComposeUiTest {
        val read = CompletableDeferred<ScenePicture>()
        setContent {
            MaterialTheme {
                ImageSourceContent(
                    SceneSource.ImageSource(id = "i5", name = "Photo", filePath = "held.png"),
                    Modifier,
                    load = { read.await() },
                )
            }
        }

        onNodeWithContentDescription("Photo").assertDoesNotExist()
        onNodeWithText("Image not found").assertDoesNotExist()

        read.complete(ScenePicture.Missing)
        awaitNotFound()
    }

    @Test
    fun `an image source renders the file when it exists and is a valid image`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test", ".png")
        try {
            val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 10) for (x in 0 until 10) img.setRGB(x, y, 0xFFFF0000.toInt())
            ImageIO.write(img, "png", file)

            setContent {
                MaterialTheme {
                    SceneSourceRenderer(SceneSource.ImageSource(
                        id = "i1",
                        name = "Photo",
                        filePath = file.absolutePath,
                    ))
                }
            }
            awaitPicture("Photo")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an image source shows a placeholder when the file does not exist`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.ImageSource(id = "i2", name = "Photo", filePath = "/nonexistent/x.png"))
            }
        }
        awaitNotFound()
    }

    @Test
    fun `an image source shows a placeholder when the file is not a valid image`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test-corrupt", ".png")
        try {
            file.writeText("this is not image data")
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(SceneSource.ImageSource(
                        id = "i3",
                        name = "Photo",
                        filePath = file.absolutePath,
                    ))
                }
            }
            // A corrupt file must be caught, not crash the renderer.
            awaitNotFound()
        } finally {
            file.delete()
        }
    }

    @Test
    fun `every image contentScale value renders without error`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test-scale", ".png")
        try {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", file)
            for (scale in listOf("FIT", "FILL", "STRETCH", "NONE", "unknown-falls-back-to-fit")) {
                setContent {
                    MaterialTheme {
                        SceneSourceRenderer(
                            SceneSource.ImageSource(
                                id = "i4",
                                name = "P",
                                filePath = file.absolutePath,
                                contentScale = scale,
                            )
                        )
                    }
                }
                awaitPicture("P")
            }
        } finally {
            file.delete()
        }
    }
}
