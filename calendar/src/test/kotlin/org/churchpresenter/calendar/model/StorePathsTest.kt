package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StorePathsTest {

    private val folder: File = Files.createTempDirectory("calendar-store").toFile()

    private fun inside(vararg parts: String) = File(folder, parts.joinToString(File.separator)).absolutePath

    private fun picture(path: String) = ScheduleItem.PictureItem("p", path, "Easter", 3)
    private fun deck(path: String) = ScheduleItem.PresentationItem("d", path, "Sermon.pptx", 12, "pptx")
    private fun media(url: String) = ScheduleItem.MediaItem("m", url, "Opener", "video")

    @Test
    fun `a folder inside the store is written relative to it`() {
        val stored = picture(inside("pictures", "easter")).withPathsRelativeTo(folder) as ScheduleItem.PictureItem

        assertEquals("./pictures/easter", stored.folderPath)
    }

    @Test
    fun `a relative path resolves against the store folder`() {
        val loaded = deck("./decks/sermon.pptx").withPathsResolvedFrom(folder) as ScheduleItem.PresentationItem

        assertEquals(inside("decks", "sermon.pptx"), loaded.filePath)
    }

    @Test
    fun `a path outside the store stays absolute`() {
        val elsewhere = Files.createTempDirectory("elsewhere").toFile().absolutePath
        val stored = picture(elsewhere).withPathsRelativeTo(folder) as ScheduleItem.PictureItem

        assertEquals(elsewhere, stored.folderPath)
    }

    @Test
    fun `a stream is a URL and is left alone`() {
        val stream = media("rtsp://camera.local/live")

        assertSame(stream, stream.withPathsRelativeTo(folder))
        assertSame(stream, stream.withPathsResolvedFrom(folder))
    }

    @Test
    fun `a local video is a path`() {
        val stored = media(inside("media", "opener.mp4")).withPathsRelativeTo(folder) as ScheduleItem.MediaItem

        assertEquals("./media/opener.mp4", stored.mediaUrl)
    }

    @Test
    fun `a cue's payload travels the same way`() {
        val cue = ScheduleItem.CueItem("c", "show", payload = picture(inside("pictures")))

        val stored = cue.withPathsRelativeTo(folder) as ScheduleItem.CueItem
        val back = stored.withPathsResolvedFrom(folder)

        assertEquals("./pictures", (stored.payload as ScheduleItem.PictureItem).folderPath)
        assertEquals(cue, back)
    }

    @Test
    fun `relative then resolved is the original`() {
        val document = CalendarDocument(
            services = listOf(
                PlannedService("s", "2026-09-20", "Sunday", "10:00", items = listOf(deck(inside("a.pptx")))),
            ),
            templates = listOf(SavedTemplate("t", "Usual", "10:00", items = listOf(media(inside("x.mp4"))))),
        )

        assertEquals(document, document.withPathsRelativeTo(folder).withPathsResolvedFrom(folder))
    }

    @Test
    fun `an already relative path is not made relative twice`() {
        val stored = picture("./pictures").withPathsRelativeTo(folder) as ScheduleItem.PictureItem

        assertEquals("./pictures", stored.folderPath)
    }
}
