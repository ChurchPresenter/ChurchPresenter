package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.EngineBook
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.engine.DetectionEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.churchpresenter.bibleengine.engine.ScriptureEvent

class DetectionEngineRefEventTest {

    private val verses = listOf(
        EngineVerse("B043C003V001", 43, 3, 1, "The Gospel According to John", true),
        EngineVerse("B043C003V016", 43, 3, 16, "For God so loved the world that he gave his only begotten Son", false),
        EngineVerse("B043C003V017", 43, 3, 17, "For God sent not his Son into the world to condemn the world", false),
        EngineVerse("B043C003V018", 43, 3, 18, "He that believeth on him is not condemned", false),
    )

    private val translation = EngineTranslation(
        id = "ENG_KJV", title = "King James Version", abbreviation = "KJV", language = "ENG",
        numbering = "hebrew", script = Script.LATIN,
        books = listOf(EngineBook(43, "John", 21)),
        byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
        byChapter = verses.groupBy { it.bookNum to it.chapter },
        byCode = verses.associateBy { it.code },
    )

    private var now = 1_000L
    private fun engine() = DetectionEngine(listOf(translation), clock = { now })

    private val savedLevel = Config.level
    private val savedLogCandidates = Config.logCandidates
    private val savedCandidateMin = Config.candidateLogMinConfidence

    @AfterTest
    fun restore() {
        Config.applyLevel(savedLevel)
        Config.logCandidates = savedLogCandidates
        Config.candidateLogMinConfidence = savedCandidateMin
    }

    @Test
    fun `an explicit reference is trusted outright`() {
        val event: ScriptureEvent = assertNotNull(
            engine().processTranscription("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals(0.95, event.confidence)
        assertEquals("explicit", event.matchType)
        assertEquals("John 3:16", event.reference.displayRef)
    }

    @Test
    fun `a verse that does not exist in the translation is never fabricated`() {
        assertTrue(engine().processTranscription("live", "turn to John chapter 3 verse 99").isEmpty())
    }

    @Test
    fun `a chapter with no verse cited emits nothing but primes the sticky`() {
        val e = engine()

        assertTrue(e.processTranscription("live", "turn to John chapter 3").isEmpty())

        assertTrue(e.processTranscription("live", "verse 16").isNotEmpty(), "the sticky should resolve the verse")
    }

    @Test
    fun `a header verse is never emitted as a reference`() {
        assertTrue(engine().processTranscription("live", "turn to John chapter 3 verse 1").isEmpty())
    }

    @Test
    fun `a sticky continuation is scored by agreement rather than trusted`() {
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3")

        val event: ScriptureEvent = assertNotNull(e.processTranscription("live", "verse 17").firstOrNull())

        assertEquals("continuation", event.matchType)
        assertTrue(event.confidence in 0.60..0.88, "continuation confidence was ${event.confidence}")
    }

    @Test
    fun `a continuation reports the continuation event type`() {
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3")

        val event: ScriptureEvent = assertNotNull(e.processTranscription("live", "verse 17").firstOrNull())

        assertEquals("scripture.continuation", event.type)
    }

    @Test
    fun `an explicit reference reports the detected event type`() {
        val event: ScriptureEvent = assertNotNull(
            engine().processTranscription("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals("scripture.detected", event.type)
    }

    @Test
    fun `the emitted event carries the verse code and text`() {
        val event: ScriptureEvent = assertNotNull(
            engine().processTranscription("live", "turn to John chapter 3 verse 16").firstOrNull()
        )

        assertEquals("B043C003V016", event.reference.canonicalCodeStart)
        assertTrue(event.verseText.startsWith("For God so loved"))
    }

    @Test
    fun `candidate logging is skipped entirely when disabled`() {
        Config.logCandidates = false
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3 verse 16")

        assertTrue(e.processTranscription("live", "turn to John chapter 3 verse 16").isEmpty())
    }

    @Test
    fun `a repeat of the same reference is deduped rather than re-emitted`() {
        val e = engine()
        assertTrue(e.processTranscription("live", "turn to John chapter 3 verse 16").isNotEmpty())

        assertTrue(e.processTranscription("live", "turn to John chapter 3 verse 16").isEmpty())
    }

    @Test
    fun `a low-confidence candidate is below the candidate log floor`() {
        Config.logCandidates = true
        Config.candidateLogMinConfidence = 0.99
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3 verse 16")

        assertTrue(e.processTranscription("live", "turn to John chapter 3 verse 16").isEmpty())
    }

    @Test
    fun `the reverse lookup is skipped entirely at the off level`() {
        Config.applyLevel("off")

        assertTrue(engine().processTranscription("live", "for God so loved the world").isEmpty())
    }

    @Test
    fun `reading a verse verbatim finds it without an explicit citation`() {
        Config.applyLevel("aggressive")

        val events = engine(
            ).processTranscription("live",
            "for God so loved the world that he gave his only begotten son",
        )

        assertTrue(events.isNotEmpty(), "the reverse lookup should recognise a verbatim reading")
    }
}
