package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.bible.EngineBook
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.engine.DetectionEngine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DetectionEngineChapterScanTest {

    private val verses = listOf(
        EngineVerse("B043C003V001", 43, 3, 1, "there was a man of the pharisees named nicodemus", false),
        EngineVerse("B043C003V002", 43, 3, 2, "the same came by night with questions", false),
        EngineVerse("B043C003V003", 43, 3, 3, "except a man be born again he cannot see the kingdom", false),
        EngineVerse("B043C005V001", 43, 5, 1, "after this there was a feast of the jews", false),
        EngineVerse("B043C005V002", 43, 5, 2, "at jerusalem by the sheep market is a pool called bethesda", false),
    )

    private val kjv = EngineTranslation(
        id = "ENG_KJV", title = "KJV", abbreviation = "KJV", language = "ENG",
        numbering = "hebrew", script = Script.LATIN,
        books = listOf(EngineBook(43, "John", 21)),
        byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
        byChapter = verses.groupBy { it.bookNum to it.chapter },
        byCode = verses.associateBy { it.code },
    )

    private val savedLevel = Config.level
    private val savedHistory = Config.chapterHistoryEnabled

    @BeforeTest
    fun setUp() {
        Config.applyLevel("off")
    }

    @AfterTest
    fun restore() {
        Config.applyLevel(savedLevel)
        Config.chapterHistoryEnabled = savedHistory
    }

    private fun engine() = DetectionEngine(listOf(kjv), clock = { 1_000L })

    @Test
    fun `a verse read from the announced chapter is a chapter scan`() {
        val e = engine()
        e.processTranscription("live", "turn to John chapter 3")

        val event = assertNotNull(
            e.processTranscription("live", "except a man be born again he cannot see the kingdom").singleOrNull(),
        )

        assertEquals("chapter-scan", event.matchType)
        assertEquals("scripture.continuation", event.type)
        assertEquals(3, event.reference.verseStart)
    }

    @Test
    fun `a verse read from an earlier chapter is matched through the history`() {
        Config.chapterHistoryEnabled = true
        val e = engine()
        e.processTranscription("live", "turn to John chapter 5")
        e.processTranscription("live", "turn to John chapter 3")

        val event = assertNotNull(
            e.processTranscription("live", "at jerusalem by the sheep market is a pool called bethesda")
                .singleOrNull(),
        )

        assertEquals("chapter-history", event.matchType)
        assertEquals(5, event.reference.chapter)
        assertEquals(2, event.reference.verseStart)
    }
}
