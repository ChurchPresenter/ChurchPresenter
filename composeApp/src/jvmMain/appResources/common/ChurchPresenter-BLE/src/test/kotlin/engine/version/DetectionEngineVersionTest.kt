package engine.version

import engine.Config
import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import engine.bible.Script
import engine.engine.DetectionEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end: version detection reaches an emitted [engine.engine.ScriptureEvent], or stays absent. */
class DetectionEngineVersionTest {

    private val verses = listOf(
        EngineVerse("B040C018V013", 40, 18, 13, KJV_MATT_18_13, false),
        EngineVerse("B040C018V014", 40, 18, 14, KJV_MATT_18_14, false),
    )

    private val detecting = EngineTranslation(
        id = "ENG_KJV", title = "King James Version", abbreviation = "KJV", language = "ENG",
        numbering = "hebrew", script = Script.LATIN,
        books = listOf(EngineBook(40, "Matthew", 28)),
        byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
        byChapter = verses.groupBy { it.bookNum to it.chapter },
        byCode = verses.associateBy { it.code },
    )

    private val corpus = MapVersionCorpus(
        mapOf(
            MATT_18_13 to listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)),
            "B040C018V014" to listOf(candidate("KJV", KJV_MATT_18_14), candidate("NASB", NASB_MATT_18_14)),
        )
    )

    private var saved: Map<String, Any> = emptyMap()

    @BeforeTest fun snapshot() {
        saved = mapOf(
            "enabled" to Config.versionDetectionEnabled,
            "reverseEnabled" to Config.reverseEnabled,
            "minConfidenceEmit" to Config.minConfidenceEmit,
        )
        Config.versionDetectionEnabled = true
    }

    @AfterTest fun restore() {
        Config.versionDetectionEnabled = saved["enabled"] as Boolean
        Config.reverseEnabled = saved["reverseEnabled"] as Boolean
        Config.minConfidenceEmit = saved["minConfidenceEmit"] as Double
    }

    // Score inline: version scoring is asynchronous in production, so asserting on it right after
    // feeding a transcript would otherwise race the scoring thread.
    private fun engine(withCorpus: Boolean) =
        if (withCorpus) DetectionEngine(
            listOf(detecting),
            versionCorpus = { corpus },
            versionScoringExecutor = { it.run() },
        )
        else DetectionEngine(listOf(detecting), versionScoringExecutor = { it.run() })

    /** Announce the chapter, then read two verses in NASB wording — the chapter-scan path. */
    private fun readNasbPassage(e: DetectionEngine): List<engine.engine.ScriptureEvent> {
        e.processTranscription("live", "turn with me to Matthew chapter 18")
        val a = e.processTranscription(
            "live",
            "if it turns out that he finds it truly i say to you he rejoices over it more than " +
                "over the ninety nine which have not gone astray",
        )
        val b = e.processTranscription(
            "live",
            "so it is not the will of your father who is in heaven that one of these little ones perish",
        )
        return a + b
    }

    @Test fun `an emitted event names the version being read`() {
        val events = readNasbPassage(engine(withCorpus = true))
        assertTrue(events.isNotEmpty(), "expected the passage to be detected at all")
        val last = events.last()
        assertEquals("NASB", last.detectedVersion, "the reading was NASB wording, not the KJV text on screen")
        assertEquals("ENG_NASB", last.detectedVersionId)
        assertNotNull(last.detectedVersionConfidence)
        // The displayed text still comes from the loaded bible — version detection never changes it.
        assertEquals("KJV", last.translation)
    }

    @Test fun `an engine with no version corpus reports no version at all`() {
        // The guard that the default construction (replay, every other test) is untouched.
        val events = readNasbPassage(engine(withCorpus = false))
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.detectedVersion == null && it.detectedVersionId == null })
    }

    @Test fun `the first verse of a passage carries no version yet`() {
        val e = engine(withCorpus = true)
        e.processTranscription("live", "turn with me to Matthew chapter 18")
        val first = e.processTranscription(
            "live",
            "if it turns out that he finds it truly i say to you he rejoices over it more than " +
                "over the ninety nine which have not gone astray",
        )
        assertTrue(first.isNotEmpty())
        assertNull(first.last().detectedVersion, "one verse must never be enough to name a version")
    }
}
