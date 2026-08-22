package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.EngineBook
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.engine.DetectionEngine
import org.churchpresenter.bibleengine.engine.ScriptureEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DetectionEngineTracksTest {

    // The shape of EngineTranslation itself: naming five of its fields is what building one takes.
    @Suppress("LongParameterList")
    private fun translation(
        id: String, abbrev: String, lang: String, script: Script, bookName: String,
        texts: Map<Int, String>,
    ): EngineTranslation {
        val verses = texts.map { (v, text) -> EngineVerse("B043C003V%03d".format(v), 43, 3, v, text, false) }
        return EngineTranslation(
            id = id, title = abbrev, abbreviation = abbrev, language = lang,
            numbering = "hebrew", script = script,
            books = listOf(EngineBook(43, bookName, 21)),
            byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
            byChapter = verses.groupBy { it.bookNum to it.chapter },
            byCode = verses.associateBy { it.code },
        )
    }

    private val eng = translation(
        "ENG_KJV", "KJV", "ENG", Script.LATIN, "John",
        mapOf(
            16 to "For God so loved the world that he gave his only begotten Son",
            17 to "For God sent not his Son into the world to condemn the world",
        ),
    )

    private val rus = translation(
        "RUS_RST", "RST", "RUS", Script.CYRILLIC, "Иоанна",
        mapOf(
            16 to "Ибо так возлюбил Бог мир что отдал Сына Своего Единородного",
            17 to "Ибо не послал Бог Сына Своего в мир чтобы судить мир",
        ),
    )

    private var now = 1_000L
    private fun engine(vararg t: EngineTranslation) = DetectionEngine(t.toList(), clock = { now })

    private val savedLevel = Config.level
    private val savedReverse = Config.reverseEnabled

    @AfterTest
    fun restore() {
        Config.applyLevel(savedLevel)
        Config.reverseEnabled = savedReverse
    }

    @Test
    fun `a cyrillic citation picks the cyrillic translation`() {
        val event = assertNotNull(
            engine(eng, rus).processTranscription("live", "Иоанна 3 глава 16 стих").firstOrNull()
        )

        assertEquals("RST", event.translation)
    }

    @Test
    fun `a latin citation picks the latin translation`() {
        val event = assertNotNull(
            engine(eng, rus).processTranscription("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals("KJV", event.translation)
    }

    @Test
    fun `with no translation of the spoken script the first one is used`() {
        val event = assertNotNull(
            engine(eng).processTranscription("live", "Иоанна 3 глава 16 стих").firstOrNull()
        )

        assertEquals("KJV", event.translation)
    }

    @Test
    fun `a blank transcript falls back to the translation track for script choice`() {
        val e = engine(eng, rus)

        val event = assertNotNull(e.processTranslation("live", "Иоанна 3 глава 16 стих").firstOrNull())

        assertEquals("RST", event.translation)
    }

    @Test
    fun `a citation spoken in one track marks only that track`() {
        val event = assertNotNull(
            engine(eng).processTranscription("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals(listOf("transcription"), event.tracks)
    }

    @Test
    fun `a citation present in both tracks marks both`() {
        val e = engine(eng)
        e.processTranscription("live", "turn to John chapter 3 verse 16")
        now += 60_000

        val event = assertNotNull(
            e.processTranslation("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals(listOf("transcription", "translation"), event.tracks)
    }

    @Test
    fun `reading the verse text corroborates the track that carried it`() {
        Config.applyLevel("aggressive")

        val event = assertNotNull(
            engine(eng).processTranscription(
                "live", "for God so loved the world that he gave his only begotten son"
            ).firstOrNull()
        )

        assertTrue("transcription" in event.tracks)
    }

    @Test
    fun `the speech type and sticky context are stamped onto the event`() {
        val e = engine(eng)

        val event = assertNotNull(
            e.processTranscription("live", "turn to John chapter 3 verse 16", speechType = "Speech").firstOrNull()
        )

        assertEquals("Speech", event.speechType)
        assertEquals(43, event.stickyBook)
        assertEquals(3, event.stickyChapter)
    }

    @Test
    fun `a chapter announcement then a verbatim reading is reported as a chapter scan`() {
        Config.reverseEnabled = false
        val e = engine(eng)
        e.processTranscription("live", "turn with me to John chapter 3")

        val events: List<ScriptureEvent> = e.processTranscription(
            "live", "for God sent not his Son into the world to condemn the world"
        )

        assertTrue(events.isNotEmpty(), "expected the chapter-scope scan to find the verse")
        assertEquals("chapter-scan", events.first().matchType)
        assertEquals(17, events.first().reference.verseStart)
    }

    @Test
    fun `an utterance of digits alone resolves to the first translation`() {
        val e = engine(eng, rus)
        e.processTranscription("live", "turn to John chapter 3")

        assertTrue(e.processTranscription("live", "123 456").isEmpty())
    }
}
