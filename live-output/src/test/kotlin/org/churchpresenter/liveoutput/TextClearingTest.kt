package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.dictionary.StrongsEntry
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextClearingTest {

    private val manager = PresenterManager(showPresenterWindowInitially = false)

    @AfterTest
    fun stopTickers() {
        manager.pauseAnnouncementTimer()
    }

    private fun line(text: String) = LyricSection(type = "verse", title = "Amazing Grace", lines = listOf(text))

    private fun verse(number: Int) =
        SelectedVerse(bookName = "John", chapter = 3, verseNumber = number, verseText = "Verse $number")

    private fun entry(number: String) =
        StrongsEntry(number = number, word = "", transliteration = "", pronunciation = "", definition = "")

    private fun singing(text: String) = manager.apply {
        setPresentingMode(Presenting.LYRICS)
        setDisplayedLyricSection(line(text))
    }

    @Test
    fun `nothing is cleared until asked`() {
        singing("Amazing grace")
        assertFalse(manager.textCleared)
    }

    @Test
    fun `clearing hides the line on air and the next line shows again`() {
        singing("Amazing grace")
        manager.clearTextOnly()
        assertTrue(manager.textCleared)

        manager.setDisplayedLyricSection(line("How sweet the sound"))
        assertFalse(manager.textCleared)
    }

    @Test
    fun `restoring puts the cleared line back`() {
        singing("Amazing grace")
        manager.clearTextOnly()
        manager.restoreText()
        assertFalse(manager.textCleared)
    }

    @Test
    fun `the next verse shows again`() {
        manager.setPresentingMode(Presenting.BIBLE)
        manager.setDisplayedVerses(listOf(verse(16)))
        manager.clearTextOnly()
        assertTrue(manager.textCleared)

        manager.setDisplayedVerses(listOf(verse(17)))
        assertFalse(manager.textCleared)
    }

    @Test
    fun `the next question shows again`() {
        manager.setDisplayedQuestion(Question(id = "q1", text = "Why?", timestamp = 0))
        manager.clearTextOnly()
        assertTrue(manager.hidesQuestion())

        manager.setDisplayedQuestion(Question(id = "q2", text = "How?", timestamp = 0))
        assertFalse(manager.hidesQuestion())
    }

    @Test
    fun `a QR code stays up while the text is cleared`() {
        manager.setDisplayedQuestion(Question(id = "q1", text = "Why?", timestamp = 0))
        manager.setShowQRCodeOnDisplay(true)
        manager.clearTextOnly()

        assertTrue(manager.textCleared)
        assertFalse(manager.hidesQuestion())
    }

    @Test
    fun `the next dictionary entry shows again`() {
        manager.setDisplayedDictionaryEntry(entry("H430"))
        manager.clearTextOnly()
        assertTrue(manager.textCleared)

        manager.setDisplayedDictionaryEntry(entry("G26"))
        assertFalse(manager.textCleared)
    }

    @Test
    fun `an announcement changing leaves the cleared text cleared`() {
        singing("Amazing grace")
        manager.clearTextOnly()

        manager.setDisplayedAnnouncementText("10:00")
        manager.setDisplayedAnnouncementText("09:59")
        assertTrue(manager.textCleared)
    }

    @Test
    fun `going live again brings back the same line`() {
        singing("Amazing grace")
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.LYRICS)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `going live again brings back the same verse`() {
        manager.setPresentingMode(Presenting.BIBLE)
        manager.setDisplayedVerses(listOf(verse(16)))
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.BIBLE)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `going live again brings back the same question`() {
        manager.setDisplayedQuestion(Question(id = "q1", text = "Why?", timestamp = 0))
        manager.setPresentingMode(Presenting.QA)
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.QA)
        assertFalse(manager.hidesQuestion())
    }

    @Test
    fun `going live again brings back the same dictionary entry`() {
        manager.setDisplayedDictionaryEntry(entry("H430"))
        manager.setPresentingMode(Presenting.DICTIONARY)
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.DICTIONARY)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `going live again brings back the same announcement`() {
        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.clearTextOnly()
        assertTrue(manager.textCleared)

        manager.previewBus.present(Presenting.ANNOUNCEMENTS)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `an overlay with no text going live leaves the text cleared`() {
        singing("Amazing grace")
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.LOWER_THIRD)
        assertTrue(manager.textCleared)
    }

    @Test
    fun `a new announcement shows over cleared text whichever way it arrives`() {
        singing("Amazing grace")
        manager.clearTextOnly()

        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `the announcement on air reporting itself live again stays cleared`() {
        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.clearTextOnly()

        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        assertTrue(manager.textCleared)
    }

    @Test
    fun `a timer on air goes live again past preview mode and shows`() {
        manager.setAnnouncementTickerLive(true)
        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.previewBus.setEnabled(true)
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.ANNOUNCEMENTS)
        assertFalse(manager.textCleared)
    }

    @Test
    fun `cueing another song leaves the text on air cleared`() {
        singing("Amazing grace")
        manager.previewBus.setEnabled(true)
        manager.clearTextOnly()

        manager.previewBus.present(Presenting.LYRICS)
        assertTrue(manager.previewBus.manager.isLive(Presenting.LYRICS))
        assertTrue(manager.textCleared)
    }

    @Test
    fun `taking the announcement already on air shows it`() {
        manager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        manager.previewBus.setEnabled(true)
        manager.clearTextOnly()
        manager.previewBus.manager.setPresentingMode(Presenting.ANNOUNCEMENTS)

        manager.previewBus.take()
        assertFalse(manager.textCleared)
    }
}
