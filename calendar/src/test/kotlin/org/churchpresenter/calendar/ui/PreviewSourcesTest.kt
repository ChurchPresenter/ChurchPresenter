@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The previews that need the app to draw them: a video, a scene, a deck's slides.
 *
 * The app supplies each as a lambda — this module has no player, no canvas and no rasteriser — so
 * what is asserted here is that the preview asks for the right one and frames what comes back.
 */
class PreviewSourcesTest {

    /** A thumbnail the app would have rendered, small enough to cost nothing. */
    private fun stubThumbnail(): ImageBitmap =
        java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB).toComposeImageBitmap()

    private fun preview(
        item: ScheduleItem,
        sources: PreviewSources,
        body: androidx.compose.ui.test.ComposeUiTest.() -> Unit,
    ) {
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    PresetPreview(item = item, sources = sources, modifier = Modifier)
                }
            }
            waitForIdle()
            body()
        }
    }

    private fun shows(test: androidx.compose.ui.test.ComposeUiTest, text: String) =
        test.onAllNodesWithText(text, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a clip is played by the app's own player`() {
        val file = Files.createTempFile("clip", ".mp4").toFile()
        file.writeBytes(ByteArray(16))
        try {
            var asked: String? = null
            val sources = PreviewSources(
                video = { path, modifier ->
                    asked = path
                    Text("playing", modifier = modifier.background(Color.Black))
                },
            )
            val item = ScheduleItem.MediaItem(
                id = "m", mediaUrl = file.absolutePath, mediaTitle = "Welcome", mediaType = "local",
            )

            preview(item, sources) {
                assertTrue(asked == file.absolutePath, "the player is handed the file, not the row")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a scene is drawn by the app's canvas`() {
        var asked: String? = null
        val sources = PreviewSources(
            scene = { sceneId, modifier ->
                asked = sceneId
                Text("scene", modifier = modifier.fillMaxSize().background(Color.Blue))
            },
        )
        val item = ScheduleItem.SceneItem(id = "s", sceneId = "scene-7", sceneName = "Bible with Background")

        preview(item, sources) {
            assertTrue(asked == "scene-7")
            assertTrue(shows(this, "Bible with Background"), "and the caption names it")
        }
    }

    @Test
    fun `a deck asks the app to rasterise its first slides`() {
        val file = Files.createTempFile("deck", ".pptx").toFile()
        file.writeBytes(ByteArray(16))
        try {
            var askedFor = 0
            val sources = PreviewSources(
                slideThumbnails = { _, cap ->
                    askedFor = cap
                    listOf(stubThumbnail(), stubThumbnail())
                },
            )
            val item = ScheduleItem.PresentationItem(
                id = "d", filePath = file.absolutePath, fileName = "deck", slideCount = 2, fileType = "pptx",
            )

            preview(item, sources) {
                waitUntil("the slides came back") { shows(this, "slides") }
                assertTrue(askedFor == PREVIEW_CAP, "it asks for the first ten and says so")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a long deck says how many it is showing of how many`() {
        val file = Files.createTempFile("big-deck", ".pptx").toFile()
        file.writeBytes(ByteArray(16))
        try {
            val sources = PreviewSources(
                slideThumbnails = { _, cap -> List(cap) { stubThumbnail() } },
            )
            val item = ScheduleItem.PresentationItem(
                id = "d", filePath = file.absolutePath, fileName = "deck", slideCount = 40, fileType = "pptx",
            )

            preview(item, sources) {
                waitUntil("the slides came back") { shows(this, "40") }
                assertTrue(shows(this, "40"), "the deck's real length, not the ten it drew")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a picture folder with more than ten says so`() {
        val folder = Files.createTempDirectory("many-pics").toFile()
        try {
            repeat(12) { index ->
                val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
                javax.imageio.ImageIO.write(image, "png", File(folder, "pic$index.png"))
            }
            val item = ScheduleItem.PictureItem(
                id = "p", folderPath = folder.absolutePath, folderName = folder.name, imageCount = 12,
            )

            preview(item, PreviewSources()) {
                waitUntil("the folder was read") { shows(this, "12") }
                assertTrue(shows(this, "12"), "how many there are, beside the ten it drew")
            }
        } finally {
            folder.deleteRecursively()
        }
    }
}
