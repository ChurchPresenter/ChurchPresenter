package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnAirStateFromPresenterTest {

    @Test
    fun `a presenter with nothing up reads as nothing on air`() {
        val state = PresenterManager().onAirState()

        assertTrue(state.content.isEmpty())
        assertNull(state.deckName)
        assertNull(state.sceneId)
        assertNull(state.dictionaryWord)
        assertNull(state.imagePath)
        assertEquals("", state.mediaUrl)
        assertFalse(scheduleRowOnAir(ScheduleItem.SongItem("s", 0, "", ""), state))
    }

    @Test
    fun `what the presenter shows is read into the state`() {
        val pm = PresenterManager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setLyricSection(LyricSection(title = "Amazing Grace", songNumber = 42, lines = listOf("words")))
        pm.setSelectedVerses(
            listOf(SelectedVerse(bookName = "John", chapter = 3, verseNumber = 16, verseRange = "16-18")),
        )
        pm.setLiveSlide("/decks/Welcome.pptx", 2)
        pm.setActiveScene(Scene(id = "scene-1", name = "Welcome"))
        pm.setDisplayedDictionaryEntry(StrongsEntry("G26", "agape", "agape", "ag-ah'-pay", "love"))
        pm.setSelectedImagePath("/pictures/one.jpg")
        pm.setWebsiteUrl("https://example.org")

        val state = pm.onAirState()

        assertTrue(Presenting.LYRICS in state.content)
        assertEquals(42, state.songNumber)
        assertEquals("Amazing Grace", state.songTitle)
        assertEquals("John", state.verseBook)
        assertEquals(3, state.verseChapter)
        assertEquals(16, state.verseNumber)
        assertEquals("16-18", state.verseRange)
        assertEquals("/decks/Welcome.pptx", state.deckName)
        assertEquals("scene-1", state.sceneId)
        assertEquals("agape", state.dictionaryWord)
        assertEquals("/pictures/one.jpg", state.imagePath)
        assertEquals("https://example.org", state.websiteUrl)
        assertTrue(scheduleRowOnAir(ScheduleItem.SongItem("s", 42, "Amazing Grace", "Hymnal"), state))
    }

    @Test
    fun `a row whose kind of content is not up is never on air, whatever else matches`() {
        val nothing = OnAirState(
            content = emptySet(), imagePath = "/pictures/one.jpg", websiteUrl = "https://example.org",
            sceneId = "scene-1", lowerThirdName = "Pastor", dictionaryWord = "agape",
        )
        assertFalse(scheduleRowOnAir(ScheduleItem.PictureItem("p", "/pictures", "pictures", 1), nothing))
        assertFalse(scheduleRowOnAir(ScheduleItem.WebsiteItem("w", "https://example.org"), nothing))
        assertFalse(scheduleRowOnAir(ScheduleItem.SceneItem("c", "scene-1", "Welcome"), nothing))
        assertFalse(
            scheduleRowOnAir(ScheduleItem.LowerThirdItem("l", "pastor", "Pastor", false, 0), nothing),
        )
        assertFalse(scheduleRowOnAir(ScheduleItem.DictionaryItem("x", "G26", "agape", "agape", "love"), nothing))
    }

    @Test
    fun `a deck row is not on air while a presentation with no deck name is up`() {
        val deck = ScheduleItem.PresentationItem("d", "/decks/Sermon.pptx", "Sermon.pptx", 24, "pptx")
        assertFalse(scheduleRowOnAir(deck, OnAirState(content = setOf(Presenting.PRESENTATION))))
    }

    @Test
    fun `a numbered song row against an unnumbered live song goes by title`() {
        val row = ScheduleItem.SongItem("s", 42, "Amazing Grace", "Hymnal")
        val live = OnAirState(content = setOf(Presenting.LYRICS), songNumber = 0, songTitle = "AMAZING GRACE")
        assertTrue(scheduleRowOnAir(row, live))
        assertFalse(scheduleRowOnAir(row, live.copy(songTitle = "Be Thou My Vision")))
    }

    @Test
    fun `a verse of another book is not the row's verse`() {
        val verse = ScheduleItem.BibleVerseItem("b", "John", 3, 16, "For God so loved the world.")
        val live = OnAirState(
            content = setOf(Presenting.BIBLE), verseBook = "Luke", verseChapter = 3, verseNumber = 16,
        )
        assertFalse(scheduleRowOnAir(verse, live))
    }

    @Test
    fun `a picture shown by a bare file name is in no folder`() {
        val folder = ScheduleItem.PictureItem("p", "/pictures", "pictures", 2)
        assertFalse(scheduleRowOnAir(folder, OnAirState(content = setOf(Presenting.PICTURES), imagePath = "one.jpg")))
    }
}
