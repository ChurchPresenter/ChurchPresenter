package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a Schedule row is still what the outputs show (#1049): each kind is told apart by what
 * it put up, not only by its kind of content, and nothing is on air once the output is cleared.
 */
class OnAirStateTest {

    private fun on(vararg content: Presenting) = OnAirState(content = content.toSet())

    @Test
    fun `a song stays live through its slides and not once another song is up`() {
        val numbered = ScheduleItem.SongItem("s", 42, "Amazing Grace", "Hymnal")
        val live = on(Presenting.LYRICS).copy(songNumber = 42, songTitle = "Amazing Grace")
        assertTrue(scheduleRowOnAir(numbered, live))
        assertFalse(scheduleRowOnAir(numbered, live.copy(songNumber = 43, songTitle = "Be Thou My Vision")))

        val unnumbered = ScheduleItem.SongItem("u", 0, "Amazing Grace", "")
        assertTrue(scheduleRowOnAir(unnumbered, live.copy(songNumber = 0, songTitle = "amazing grace")))
        assertFalse(scheduleRowOnAir(unnumbered, live.copy(songNumber = 0, songTitle = "Be Thou My Vision")))
    }

    @Test
    fun `a verse row is live for its own verse or range only`() {
        val verse = ScheduleItem.BibleVerseItem("b", "John", 3, 16, "For God so loved the world.")
        val live = on(Presenting.BIBLE).copy(verseBook = "John", verseChapter = 3, verseNumber = 16)
        assertTrue(scheduleRowOnAir(verse, live))
        assertFalse(scheduleRowOnAir(verse, live.copy(verseNumber = 17)), "another verse of the same chapter")
        assertFalse(scheduleRowOnAir(verse, live.copy(verseChapter = 4)))

        val range = verse.copy(verseRange = "16-18")
        assertTrue(scheduleRowOnAir(range, live.copy(verseRange = "16-18")))
        assertFalse(scheduleRowOnAir(range, live))
    }

    @Test
    fun `a folder row is live for any of its pictures, a single-picture row for that picture`() {
        val dir = createTempDirectory("on-air-pictures").toFile()
        try {
            val folder = ScheduleItem.PictureItem("p", dir.path, dir.name, 2)
            val one = File(dir, "one.jpg").path
            val two = File(dir, "two.jpg").path
            val elsewhere = File(dir.parentFile, "other.jpg").path
            val live = on(Presenting.PICTURES)
            assertTrue(scheduleRowOnAir(folder, live.copy(imagePath = two)))
            assertFalse(scheduleRowOnAir(folder, live.copy(imagePath = elsewhere)))
            assertFalse(scheduleRowOnAir(folder, live), "no picture up")

            val single = folder.copy(imagePath = one)
            assertTrue(scheduleRowOnAir(single, live.copy(imagePath = one)))
            assertFalse(scheduleRowOnAir(single, live.copy(imagePath = two)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a deck, clip, page and scene are live while they are the one up`() {
        val deck = ScheduleItem.PresentationItem("d", "/decks/Sermon.pptx", "Sermon.pptx", 24, "pptx")
        assertTrue(scheduleRowOnAir(deck, on(Presenting.PRESENTATION).copy(deckName = "Sermon.pptx")))
        assertFalse(scheduleRowOnAir(deck, on(Presenting.PRESENTATION).copy(deckName = "Notices.pptx")))

        val clip = ScheduleItem.MediaItem("m", "/media/loop.mp4", "Loop", "local")
        assertTrue(scheduleRowOnAir(clip, on(Presenting.MEDIA).copy(mediaUrl = "/media/loop.mp4")))
        assertFalse(scheduleRowOnAir(clip, on(Presenting.MEDIA).copy(mediaUrl = "/media/other.mp4")))

        val page = ScheduleItem.WebsiteItem("w", "https://example.org")
        assertTrue(scheduleRowOnAir(page, on(Presenting.WEBSITE).copy(websiteUrl = "https://example.org")))
        assertFalse(scheduleRowOnAir(page, on(Presenting.WEBSITE).copy(websiteUrl = "https://example.com")))

        val scene = ScheduleItem.SceneItem("c", "scene-1", "Welcome")
        assertTrue(scheduleRowOnAir(scene, on(Presenting.CANVAS).copy(sceneId = "scene-1")))
        assertFalse(scheduleRowOnAir(scene, on(Presenting.CANVAS).copy(sceneId = "scene-2")))
    }

    @Test
    fun `an announcement goes by its text, a timer by its kind`() {
        val text = ScheduleItem.AnnouncementItem("a", "Fellowship lunch")
        val live = on(Presenting.ANNOUNCEMENTS).copy(announcementText = "Fellowship lunch")
        assertTrue(scheduleRowOnAir(text, live))
        assertFalse(scheduleRowOnAir(text, live.copy(announcementText = "Choir practice")))
        assertTrue(scheduleRowOnAir(text.copy(isTimer = true), live.copy(announcementText = "04:59")))
    }

    @Test
    fun `a lower third goes by its preset's name, a dictionary row by its word`() {
        val third = ScheduleItem.LowerThirdItem("l", "pastor", "Pastor", pauseAtFrame = false, pauseDurationMs = 0)
        assertTrue(scheduleRowOnAir(third, on(Presenting.LOWER_THIRD).copy(lowerThirdName = "Pastor")))
        assertTrue(scheduleRowOnAir(third, on(Presenting.LOWER_THIRD).copy(lowerThirdName = "pastor")))
        assertFalse(scheduleRowOnAir(third, on(Presenting.LOWER_THIRD).copy(lowerThirdName = "Worship")))
        assertFalse(scheduleRowOnAir(third, on(Presenting.LOWER_THIRD)), "no name reported")

        val word = ScheduleItem.DictionaryItem("x", "G26", "agape", "agapē", "love")
        assertTrue(scheduleRowOnAir(word, on(Presenting.DICTIONARY).copy(dictionaryWord = "agape")))
        assertTrue(scheduleRowOnAir(word, on(Presenting.ANNOUNCEMENTS).copy(dictionaryWord = "agape")))
        assertFalse(scheduleRowOnAir(word, on(Presenting.DICTIONARY).copy(dictionaryWord = "logos")))
    }

    @Test
    fun `nothing is live once the output is cleared, even with the last content still recorded`() {
        val cleared = on().copy(
            songNumber = 42, verseBook = "John", verseChapter = 3, verseNumber = 16,
            deckName = "Sermon.pptx", mediaUrl = "/media/loop.mp4", announcementText = "Fellowship lunch",
        )
        assertFalse(scheduleRowOnAir(ScheduleItem.SongItem("s", 42, "Amazing Grace", ""), cleared))
        assertFalse(scheduleRowOnAir(ScheduleItem.BibleVerseItem("b", "John", 3, 16, ""), cleared))
        assertFalse(
            scheduleRowOnAir(ScheduleItem.PresentationItem("d", "/d/Sermon.pptx", "Sermon.pptx", 1, "pptx"), cleared),
        )
        assertFalse(scheduleRowOnAir(ScheduleItem.MediaItem("m", "/media/loop.mp4", "Loop", "local"), cleared))
        assertFalse(scheduleRowOnAir(ScheduleItem.AnnouncementItem("a", "Fellowship lunch"), cleared))
    }

    @Test
    fun `a label, a cue and a ministry row are never on screen`() {
        val everything = on(*Presenting.entries.toTypedArray())
        assertFalse(scheduleRowOnAir(ScheduleItem.LabelItem("l", "Welcome", "#FFFFFF", "#203040"), everything))
        assertFalse(scheduleRowOnAir(ScheduleItem.CueItem("c", CueAction.PROJECT, label = "Offering"), everything))
        assertFalse(scheduleRowOnAir(ScheduleItem.MinistryItem("m", "Prayer"), everything))
    }
}
