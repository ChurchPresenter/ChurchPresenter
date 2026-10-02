package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.CaptionReading
import org.churchpresenter.settings.RSVP_FLASH_PHRASE
import org.churchpresenter.settings.STTSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic of caption pacing: the speaker's pace measured from the STT server's timings, the
 * pace each presentation reveals at, and how RSVP cuts and times its flashes.
 */
class SttRevealPaceTest {

    private fun said(text: String, start: Double, end: Double, completed: Boolean = true) =
        STTSegment(id = 0, timestamp = "", text = text, start = start, end = end, completed = completed)

    // ── The speaker's pace ──────────────────────────────────────────────────────

    @Test
    fun `the pace is the characters said over the seconds they took`() {
        assertEquals(100L, speakerMsPerChar(listOf(said("a".repeat(40), 0.0, 4.0))))
        assertEquals(200L, speakerMsPerChar(listOf(said("a".repeat(20), 0.0, 4.0))), "a slower speaker")
    }

    @Test
    fun `there is no pace until enough timed speech has arrived`() {
        assertNull(speakerMsPerChar(emptyList()))
        assertNull(speakerMsPerChar(listOf(said("Amen", 0.0, 1.0))), "a second of speech says too little")
        assertNull(
            speakerMsPerChar(listOf(said("no timings from this server at all", 0.0, 0.0))),
            "a server that sends no timings leaves them at 0",
        )
    }

    @Test
    fun `segments still being spoken, or without timings, are left out`() {
        val pace = speakerMsPerChar(
            listOf(
                said("a".repeat(40), 0.0, 4.0),
                said("b".repeat(400), 4.0, 4.1, completed = false),
                said("c".repeat(400), 0.0, 0.0),
            ),
        )
        assertEquals(100L, pace)
    }

    @Test
    fun `only the newest speech counts, so the pace follows a speaker who changes speed`() {
        val slowThenFast = listOf(
            said("a".repeat(10), 0.0, 10.0),        // very slow, long ago
            said("b".repeat(100), 10.0, 15.0),
            said("c".repeat(100), 15.0, 20.0),     // two fast segments fill the window
        )
        assertEquals(50L, speakerMsPerChar(slowThenFast))
    }

    @Test
    fun `the pace is held between a very fast talker and a very slow one`() {
        assertEquals(20L, speakerMsPerChar(listOf(said("a".repeat(1000), 0.0, 3.0))))
        assertEquals(250L, speakerMsPerChar(listOf(said("a".repeat(5), 0.0, 9.0))))
    }

    // ── The pace each presentation reveals at ───────────────────────────────────

    private val typing = STTSettings(dripFeedEnabled = true, dripFeedSpeed = 25)

    @Test
    fun `at a fixed speed the speaker's pace is ignored`() {
        assertEquals(RevealPace(25, RevealUnit.LETTER), revealPace(typing, speakerMsPerChar = 90))
    }

    @Test
    fun `matching the speaker, typing goes at their pace, and at the fixed speed until it is known`() {
        val matching = typing.copy(matchSpeakerPace = true)
        assertEquals(RevealPace(90, RevealUnit.LETTER), revealPace(matching, speakerMsPerChar = 90))
        assertEquals(RevealPace(25, RevealUnit.LETTER), revealPace(matching, speakerMsPerChar = null))
    }

    @Test
    fun `matching the speaker without typing brings words at their pace, from an ordinary one`() {
        val words = STTSettings(dripFeedEnabled = false, matchSpeakerPace = true)
        assertEquals(RevealPace(90, RevealUnit.WORD), revealPace(words, speakerMsPerChar = 90))
        assertEquals(RevealPace(TYPICAL_SPEECH_MS_PER_CHAR, RevealUnit.WORD), revealPace(words, null))
    }

    @Test
    fun `the reading-speed limit still holds a fast speaker back`() {
        val limited = typing.copy(
            matchSpeakerPace = true,
            reading = CaptionReading(readingSpeedLimit = true, readingSpeedCps = 10),
        )
        assertEquals(RevealPace(100, RevealUnit.LETTER), revealPace(limited, speakerMsPerChar = 40))
    }

    @Test
    fun `pop-on and the ticker ignore the speaker`() {
        val popOn = typing.copy(matchSpeakerPace = true, reading = CaptionReading(style = CAPTION_STYLE_POP_ON))
        val ticker = typing.copy(matchSpeakerPace = true, reading = CaptionReading(style = CAPTION_STYLE_TICKER))
        assertNull(revealPace(popOn, speakerMsPerChar = 90))
        assertNull(revealPace(ticker, speakerMsPerChar = 90))
    }

    @Test
    fun `RSVP flashes its words at its words a minute, or the speaker's pace under that ceiling`() {
        val rsvp = STTSettings(
            reading = CaptionReading(style = CAPTION_STYLE_RSVP, rsvpWordsPerFlash = 2, rsvpWpm = 300),
        )
        assertEquals(RevealPace(0, RevealUnit.FLASH, wordsPerStep = 2, minMsPerWord = 200), revealPace(rsvp, 90))
        assertEquals(
            RevealPace(90, RevealUnit.FLASH, wordsPerStep = 2, minMsPerWord = 200),
            revealPace(rsvp.copy(matchSpeakerPace = true), 90),
        )
        assertEquals(
            RSVP_FLASH_PHRASE,
            revealPace(rsvp.copy(reading = rsvp.reading.copy(rsvpWordsPerFlash = RSVP_FLASH_PHRASE)))!!.wordsPerStep,
        )
    }

    @Test
    fun `RSVP paces whether or not typing is on`() {
        val rsvp = STTSettings(dripFeedEnabled = true, reading = CaptionReading(style = CAPTION_STYLE_RSVP))
        assertEquals(RevealUnit.FLASH, revealPace(rsvp)!!.unit)
    }

    // ── RSVP's flashes ──────────────────────────────────────────────────────────

    private fun phrases(text: String): List<String> {
        val out = mutableListOf<String>()
        var from = 0
        while (from < text.length) {
            val end = phraseEnd(text, from)
            if (end == from) break
            out += text.substring(from, end).trim()
            from = end
        }
        return out
    }

    @Test
    fun `phrases end at punctuation and never on a short word`() {
        assertEquals(
            listOf(
                "Blessed are the peacemakers,", "for they shall", "be called the children", "of God,", "and the meek",
            ),
            phrases("Blessed are the peacemakers, for they shall be called the children of God, and the meek"),
        )
    }

    @Test
    fun `a phrase holds four words at the most`() {
        assertEquals(listOf("of the in to", "at"), phrases("of the in to at"))
    }

    @Test
    fun `a set count steps that many words`() {
        val text = "one two three four five"
        assertEquals("one two", text.substring(0, flashEnd(text, 0, 2)))
        assertEquals(" three four", text.substring(7, flashEnd(text, 7, 2)))
    }

    @Test
    fun `a flash is held for its characters, but never less than its words a minute allow`() {
        val pace = RevealPace(delayMs = 100, unit = RevealUnit.FLASH, minMsPerWord = 200)
        val text = "go peacemakers"
        assertEquals(200L, flashDelayMs(pace, text, 0, 2), "a short word is held for the words-a-minute minimum")
        assertEquals(1200L, flashDelayMs(pace, text, 2, text.length), "the space before the word counts with it")
    }

    @Test
    fun `the newest words of a caption, and the words between two places in it`() {
        val text = "Blessed are\nthe peacemakers"
        assertEquals("the peacemakers", text.substring(lastWordsStart(text, 2)).trim())
        assertEquals(2, wordsBetween("one two three", 3, 13))
        assertEquals(3, lastFlashWords("one two three", 3))
        assertEquals(3, lastFlashWords("Blessed are the peacemakers, for they shall", RSVP_FLASH_PHRASE))
        assertTrue(lastFlashWords("", RSVP_FLASH_PHRASE) >= 1, "an empty caption still flashes something")
    }
}
