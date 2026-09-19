@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What a preset shows of itself before it is picked.
 *
 * A picture folder is drawn from real files on disk, because the point of the preview is that it
 * answers "which folder is this" — a fake list would assert the caption and not the pictures.
 */
class PresetPreviewTest {

    private fun preview(
        item: ScheduleItem,
        sources: PreviewSources = PreviewSources(),
        body: ComposeUiTest.() -> Unit,
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

    private fun timer(
        mode: String = TimerModes.DURATION,
        minutes: Int = 5,
        expired: String = "",
        targetHour: Int = 10,
    ) = ScheduleItem.AnnouncementItem(
        id = "t",
        text = "",
        isTimer = true,
        timerMode = mode,
        timerMinutes = minutes,
        timerExpiredText = expired,
        targetHour = targetHour,
        targetMinute = 30,
    )

    @Test
    fun `a countdown shows the time it starts on`() = preview(timer()) {
        assertTrue(shows("5:00"), "the readout it opens with")
        assertTrue(shows("Counts down"), "and what it will do")
    }

    @Test
    fun `an expired message is part of what the timer will show`() =
        preview(timer(expired = "We begin shortly")) {
            assertTrue(shows("We begin shortly"))
        }

    @Test
    fun `a count-up timer starts at zero`() = preview(timer(mode = TimerModes.COUNT_UP)) {
        assertTrue(shows("0:00"))
        assertTrue(shows("Counts up"))
    }

    @Test
    fun `a clock countdown says the time it counts to`() = preview(timer(mode = TimerModes.CLOCK)) {
        assertTrue(shows("Counts down to"))
    }

    @Test
    fun `a clock display says what it is`() = preview(timer(mode = TimerModes.CLOCK_DISPLAY)) {
        assertTrue(shows("time of day"))
    }

    @Test
    fun `a picture folder shows its pictures and how many there are`() {
        val folder = Files.createTempDirectory("preview-pics").toFile()
        try {
            repeat(3) { index -> writePng(File(folder, "shot$index.png")) }
            val item = ScheduleItem.PictureItem(
                id = "p", folderPath = folder.absolutePath, folderName = folder.name, imageCount = 3,
            )

            preview(item) {
                // Thumbnails are decoded off the composing thread; the caption follows them.
                awaitText("pictures")
                assertTrue(shows("3"), "how many the folder holds")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `a folder that is not there says so rather than drawing nothing`() {
        val item = ScheduleItem.PictureItem(
            id = "p", folderPath = "/no/such/folder", folderName = "gone", imageCount = 3,
        )

        preview(item) { assertTrue(shows("/no/such/folder")) }
    }

    @Test
    fun `a deck that is not there says so`() {
        val item = ScheduleItem.PresentationItem(
            id = "d", filePath = "/no/such/deck.pptx", fileName = "deck", slideCount = 4, fileType = "pptx",
        )

        preview(item) { assertTrue(shows("/no/such/deck.pptx")) }
    }

    @Test
    fun `a stream is described rather than played`() {
        val item = ScheduleItem.MediaItem(
            id = "m", mediaUrl = "rtsp://camera.local/live", mediaTitle = "camera", mediaType = "url",
        )

        preview(item, PreviewSources(video = { _, _ -> })) { assertTrue(shows("rtsp://camera.local/live")) }
    }

    @Test
    fun `without a player a clip says the preview is unavailable`() {
        val item = ScheduleItem.MediaItem(
            id = "m", mediaUrl = "/clips/welcome.mp4", mediaTitle = "welcome", mediaType = "local",
        )

        preview(item) { assertTrue(shows("needs the app")) }
    }

    @Test
    fun `a scene without a drawer says so`() {
        val item = ScheduleItem.SceneItem(id = "s", sceneId = "scene-1", sceneName = "Bible with Background")

        preview(item) { assertTrue(shows("needs the app")) }
    }

    @Test
    fun `an announcement is described in a line`() {
        val item = ScheduleItem.AnnouncementItem(id = "a", text = "Welcome to church")

        preview(item) { assertTrue(shows("Welcome to church")) }
    }

    /** The smallest real PNG the decoder will accept — a 1×1 white pixel. */
    private fun writePng(file: File) {
        val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFFFFFF)
        javax.imageio.ImageIO.write(image, "png", file)
    }
}
