package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.EngineBook
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.detection.ReferenceWatcher
import org.churchpresenter.bibleengine.engine.DetectionEvents
import org.churchpresenter.bibleengine.engine.UtteranceState
import org.churchpresenter.bibleengine.engine.dominantScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class DetectionEventsTest {

    private val johnVerses = listOf(
        EngineVerse("B043C003V000", 43, 3, 0, "Jesus and Nicodemus", true),
        EngineVerse("B043C003V001", 43, 3, 1, "there was a man of the pharisees named nicodemus", false),
        EngineVerse("B043C003V002", 43, 3, 2, "the same came to jesus by night and said unto him", false),
        EngineVerse("B043C003V003", 43, 3, 3, "jesus answered and said unto him verily verily", false),
        EngineVerse("B043C003V004", 43, 3, 4, "nicodemus saith unto him how can a man be born", false),
    )

    private val kjv = translation("ENG_KJV", "KJV", Script.LATIN, johnVerses, EngineBook(43, "John", 21))

    private val rst = translation(
        "RUS_RST", "RST", Script.CYRILLIC,
        listOf(EngineVerse("B043C003V016", 43, 3, 16, "Ибо так возлюбил Бог мир", false)),
        EngineBook(43, "Иоанна", 21),
    )

    private fun translation(id: String, abbr: String, script: Script, verses: List<EngineVerse>, book: EngineBook) =
        EngineTranslation(
            id = id, title = abbr, abbreviation = abbr, language = id.substringBefore('_'),
            numbering = "hebrew", script = script, books = listOf(book),
            byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
            byChapter = verses.groupBy { it.bookNum to it.chapter },
            byCode = verses.associateBy { it.code },
        )

    private fun state(transcript: String, translation: String = "") =
        UtteranceState(id = "u").apply {
            this.transcript = transcript
            this.translation = translation
        }

    @Test
    fun `a ranged reference carries its end verse and code`() {
        val event = assertNotNull(
            DetectionEvents(listOf(kjv)).buildRefEvent(state("john 3 2 to 4"), ReferenceWatcher.Ref(43, 3, 2, 4, 1)),
        )

        assertEquals("John 3:2-4", event.reference.displayRef)
        assertEquals(4, event.reference.verseEnd)
        assertEquals("B043C003V004", event.reference.canonicalCodeEnd)
        assertEquals("John", event.reference.bookName)
        assertEquals("hebrew", event.reference.numbering)
    }

    @Test
    fun `an end verse not after the start is dropped`() {
        val event = assertNotNull(
            DetectionEvents(listOf(kjv)).buildRefEvent(state("john 3 3"), ReferenceWatcher.Ref(43, 3, 3, 2, 1)),
        )

        assertEquals("John 3:3", event.reference.displayRef)
        assertNull(event.reference.verseEnd)
        assertNull(event.reference.canonicalCodeEnd)
    }

    @Test
    fun `an end verse missing from the translation keeps the range but has no end code`() {
        val event = assertNotNull(
            DetectionEvents(listOf(kjv)).buildRefEvent(state("john 3 3"), ReferenceWatcher.Ref(43, 3, 3, 9, 1)),
        )

        assertEquals("John 3:3-9", event.reference.displayRef)
        assertNull(event.reference.canonicalCodeEnd)
    }

    @Test
    fun `a reference with no verse builds no event`() {
        val noVerse = ReferenceWatcher.Ref(43, 3, null, null, 1)
        assertNull(DetectionEvents(listOf(kjv)).buildRefEvent(state("john 3"), noVerse))
    }

    @Test
    fun `the passage start steps back over previous verses that were read`() {
        val read = "there was a man of the pharisees named nicodemus " +
            "the same came to jesus by night and said unto him " +
            "jesus answered and said unto him verily verily"
        val hit = johnVerses.first { it.verse == 3 }

        val start = DetectionEvents(listOf(kjv)).passageStart(kjv, hit, state(read))

        assertEquals(1, start.verse)
    }

    @Test
    fun `the passage start never steps back onto a section header`() {
        val read = "there was a man of the pharisees named nicodemus"
        val hit = johnVerses.first { it.verse == 1 }

        assertSame(hit, DetectionEvents(listOf(kjv)).passageStart(kjv, hit, state("", read)))
    }

    @Test
    fun `a cyrillic citation picks the cyrillic translation`() {
        val events = DetectionEvents(listOf(kjv, rst))

        assertEquals("RUS_RST", events.pickTranslation(state("Иоанна 3 16")).id)
        assertEquals("RUS_RST", events.pickTranslation(state("", "Иоанна 3 16")).id)
        assertEquals("ENG_KJV", events.pickTranslation(state("John 3 16")).id)
    }

    @Test
    fun `a citation in no loaded script falls back to the first translation`() {
        assertEquals("RUS_RST", DetectionEvents(listOf(rst, kjv)).pickTranslation(state("約翰福音 3 16")).id)
    }

    @Test
    fun `the dominant script is counted from letters only`() {
        assertEquals(Script.OTHER, dominantScript("123 約翰 ⅷ"))
        assertEquals(Script.CYRILLIC, dominantScript("Иоанна John"))
        assertEquals(Script.LATIN, dominantScript("John Ин"))
    }
}
