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
}
