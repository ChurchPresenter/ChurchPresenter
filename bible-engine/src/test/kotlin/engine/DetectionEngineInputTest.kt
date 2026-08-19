package engine

import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import engine.bible.Script
import engine.engine.DetectionEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectionEngineInputTest {

    private val verses = listOf(
        EngineVerse("B043C003V016", 43, 3, 16, "For God so loved the world", false),
        EngineVerse("B043C003V017", 43, 3, 17, "For God sent not his Son to condemn the world", false),
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
    private lateinit var savedLevel: String
    private var savedSession: String? = null

    private fun engine() = DetectionEngine(listOf(translation), clock = { now })

    @BeforeTest
    fun snapshot() {
        savedLevel = Config.level
        savedSession = engine.engine.DetectionLogger.sessionId
    }

    @AfterTest
    fun restore() {
        Config.applyLevel(savedLevel)
        engine.engine.DetectionLogger.sessionId = savedSession
    }

    @Test
    fun `a transcription with no optional metadata is accepted`() {
        val events = engine().processTranscription("live", "turn to John chapter 3 verse 16")

        assertTrue(events.isNotEmpty(), "an explicit reference should emit")
    }

    @Test
    fun `a transcription carrying every optional field is accepted`() {
        val events = engine().processTranscription(
            id = "live",
            text = "turn to John chapter 3 verse 16",
            speechType = "Speech",
            segmentId = "seg-1",
            startTime = 12.5,
            sessionId = "session-a",
        )

        assertTrue(events.isNotEmpty())
        assertEquals("seg-1", events.first().segmentId)
    }

    @Test
    fun `a translation track with no optional metadata is accepted`() {
        val events = engine().processTranslation("live", "turn to John chapter 3 verse 16")

        assertTrue(events.isNotEmpty())
    }

    @Test
    fun `a translation track carrying every optional field is accepted`() {
        val events = engine().processTranslation(
            id = "live",
            text = "turn to John chapter 3 verse 16",
            speechType = "Speech",
            segmentId = "seg-2",
            startTime = 3.0,
            sessionId = "session-b",
        )

        assertTrue(events.isNotEmpty())
        assertEquals("seg-2", events.first().segmentId)
    }

    @Test
    fun `a session id on the utterance reaches the logger`() {
        engine().processTranscription("live", "hello", sessionId = "session-c")

        assertEquals("session-c", engine.engine.DetectionLogger.sessionId)
    }

    @Test
    fun `a music segment does not produce a reference`() {
        Config.suppressDuringMusic = true

        val events = engine().processTranscription("live", "turn to John chapter 3 verse 16", speechType = "Music")

        assertTrue(events.isEmpty(), "sung lyrics must not be treated as a citation")
    }

    @Test
    fun `the music check ignores case`() {
        Config.suppressDuringMusic = true

        val events = engine().processTranscription("live", "turn to John chapter 3 verse 16", speechType = "music")

        assertTrue(events.isEmpty())
    }

    @Test
    fun `an empty utterance emits nothing`() {
        assertTrue(engine().processTranscription("live", "").isEmpty())
    }

    @Test
    fun `an utterance with no reference emits nothing`() {
        Config.applyLevel("off")

        assertTrue(engine().processTranscription("live", "good morning everyone").isEmpty())
    }

    @Test
    fun `the utterance table is bounded and keeps working past the cap`() {
        val e = engine()
        repeat(300) { e.processTranscription("utt-$it", "good morning") }

        assertTrue(e.processTranscription("utt-final", "turn to John chapter 3 verse 16").isNotEmpty())
    }

    @Test
    fun `separate utterance ids keep separate state`() {
        val e = engine()
        e.processTranscription("a", "turn to John chapter 3")

        assertTrue(e.processTranscription("b", "good morning").isEmpty())
    }

    @Test
    fun `shutdown is safe to call`() {
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3 verse 16")

        e.shutdown()
    }
}
