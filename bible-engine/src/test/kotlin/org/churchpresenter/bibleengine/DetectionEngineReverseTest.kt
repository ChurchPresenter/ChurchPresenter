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

class DetectionEngineReverseTest {

    private val v28 = "come unto me all ye that labour and are heavy laden and i will give you rest"
    private val v29 = "take my yoke upon you and learn of me for i am meek and lowly in heart"
    private val v30 = "for my yoke is easy and my burden is light"

    private val verses = listOf(
        EngineVerse("B040C011V027", 40, 11, 27, "all things are delivered unto me of my father", false),
        EngineVerse("B040C011V028", 40, 11, 28, v28, false),
        EngineVerse("B040C011V029", 40, 11, 29, v29, false),
        EngineVerse("B040C011V030", 40, 11, 30, v30, false),
    )

    private val kjv = EngineTranslation(
        id = "ENG_KJV", title = "KJV", abbreviation = "KJV", language = "ENG",
        numbering = "hebrew", script = Script.LATIN,
        books = listOf(EngineBook(40, "Matthew", 28)),
        byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
        byChapter = verses.groupBy { it.bookNum to it.chapter },
        byCode = verses.associateBy { it.code },
    )

    private var now = 1_000L
    private fun engine() = DetectionEngine(listOf(kjv), clock = { now })

    private val savedLevel = Config.level
    private val savedAgreement = Config.reverseMinAgreement
    private val savedCandidates = Config.logCandidates

    @AfterTest
    fun restore() {
        Config.applyLevel(savedLevel)
        Config.reverseMinAgreement = savedAgreement
        Config.logCandidates = savedCandidates
    }

    @Test
    fun `a verbatim single verse is found by the reverse lookup`() {
        Config.applyLevel("aggressive")

        val event = assertNotNull(engine().processTranscription("live", v30).firstOrNull())

        assertEquals(30, event.reference.verseStart)
        assertEquals("reverse", event.matchType)
    }

    @Test
    fun `a reverse hit carries its bm25 score`() {
        Config.applyLevel("aggressive")

        val event = assertNotNull(engine().processTranscription("live", v30).firstOrNull())

        assertNotNull(event.bm25Score)
    }

    @Test
    fun `a hit that shares too few spoken words does not fire`() {
        Config.applyLevel("aggressive")
        Config.reverseMinAgreement = 0.99

        assertTrue(engine().processTranscription("live", "yoke").isEmpty())
    }

    @Test
    fun `the reverse lookup also runs against the translation track`() {
        Config.applyLevel("aggressive")

        val event = assertNotNull(engine().processTranslation("live", v30).firstOrNull())

        assertEquals(30, event.reference.verseStart)
    }

    @Test
    fun `the reverse lookup is skipped when disabled`() {
        Config.applyLevel("off")

        assertTrue(engine().processTranscription("live", v30).isEmpty())
    }

    @Test
    fun `a near-miss is not logged when candidate logging is off`() {
        Config.applyLevel("aggressive")
        Config.reverseMinAgreement = 0.99
        Config.logCandidates = false

        assertTrue(engine().processTranscription("live", "yoke").isEmpty())
    }

    @Test
    fun `the best hit across both tracks wins`() {
        Config.applyLevel("aggressive")
        val e = engine()

        e.processTranscription("live", "some unrelated words entirely")
        val event = assertNotNull(e.processTranslation("live", v30).firstOrNull())

        assertEquals(30, event.reference.verseStart)
    }
}
