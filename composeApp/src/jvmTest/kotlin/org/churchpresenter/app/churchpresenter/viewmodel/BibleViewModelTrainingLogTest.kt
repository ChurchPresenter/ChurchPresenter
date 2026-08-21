package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.utils.TrainingDataLogger
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the training logs record when a verse goes live, and in whose numbering.
 *
 * The ground-truth logs are scored against the detection engine's own log, so both sides have to
 * name a verse the same way — and the two numberings genuinely differ. A Synodal module follows the
 * LXX, so the Psalm printed as 22 in the operator's Bible is canonical 23; its book order differs
 * too, with the General Epistles ahead of Paul's, so a book's position in the list is not its id.
 * Logging what was on screen therefore scores every Psalm as a miss and misnames NT epistles —
 * that is exactly what the 2026-07-22 service produced (16 Psalm 23 go-lives, none of which lined
 * up with the engine's canonically-numbered detections of the same verses).
 *
 * The fixture below is built with both divergences on purpose, so a regression here fails rather
 * than silently producing an unscoreable service recording.
 */
class BibleViewModelTrainingLogTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel
    private val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs")

    /** Psalms display 22 = canonical 23 (and one verse shifted too); 1 John sits at display index 1. */
    private fun synodalShapedContent(): String = SpbFixture.buildContent(
        title = "Synodal-shaped",
        books = listOf(
            SpbFixture.Book(19, "Псалтирь", 1),
            SpbFixture.Book(62, "1-е Иоанна", 1),
            SpbFixture.Book(45, "Римлянам", 1),
        ),
        verses = listOf(
            SpbFixture.Verse(19, 22, 1, "Господь — Пастырь мой", codeChapter = 23),
            SpbFixture.Verse(19, 22, 5, "Ты приготовил предо мною трапезу", codeChapter = 23),
            // Verse numbering can shift independently of the chapter (Synodal merges/splits verses).
            SpbFixture.Verse(19, 22, 6, "Так, благость и милость", codeChapter = 23, codeVerse = 7),
            SpbFixture.Verse(62, 1, 9, "Если исповедуем грехи наши", codeChapter = 1),
            SpbFixture.Verse(45, 8, 1, "Итак нет ныне никакого осуждения", codeChapter = 8),
        ),
    )

    @BeforeTest
    fun setUp() {
        logDir.listFiles()?.forEach { it.delete() }
        dir = Files.createTempDirectory("cp-bible-training-log-test").toFile()
        SpbFixture.spbFile(dir, name = "synodal.spb", content = synodalShapedContent())
        vm = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "synodal.spb"),
                bibleEngineSettings = BibleEngineSettings(autoFollow = false),
            ),
        )
        awaitUntil("books to load") { vm.books.value.isNotEmpty() }
        awaitUntil("verse data to load") { vm.isFullyLoaded }
    }

    @AfterTest
    fun tearDown() {
        TrainingDataLogger.sessionId = null
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun rows(prefix: String): List<JsonObject> {
        val file = assertNotNull(
            logDir.listFiles()?.firstOrNull { it.name.startsWith(prefix) },
            "expected a $prefix log in ${logDir.absolutePath}",
        )
        return file.readLines().filter { it.isNotBlank() }
            .map { Json.parseToJsonElement(it) as JsonObject }
            .filterNot { it["type"] != null && it["type"] != JsonNull }
    }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.takeIf { it != JsonNull }?.jsonPrimitive?.content?.toInt()

    private val psalmsIndex get() = vm.books.value.indexOfFirst { it.startsWith("Псалтирь") }
    private val johnIndex get() = vm.books.value.indexOfFirst { it.startsWith("1-е Иоанна") }

    @Test
    fun `a go-live is logged in canonical numbering with the displayed numbers alongside`() {
        TrainingDataLogger.sessionId = "canonical-psalm"
        vm.logLiveReference(
            displayBookIndex = psalmsIndex, chapter = 22, verseStart = 5, verseEnd = null,
            source = "manual", autoFollow = false,
        )

        val row = rows("live-references-").single()
        assertEquals(19, row.int("book"))
        assertEquals(23, row.int("chapter"), "the operator's Psalm 22 is canonical Psalm 23")
        assertEquals(5, row.int("verseStart"))
        assertEquals(22, row.int("displayChapter"), "what was on screen stays in the record too")
        assertEquals(5, row.int("displayVerseStart"))
    }

    @Test
    fun `a verse that is renumbered as well as its chapter maps on both axes`() {
        TrainingDataLogger.sessionId = "canonical-verse-shift"
        vm.logLiveReference(
            displayBookIndex = psalmsIndex, chapter = 22, verseStart = 6, verseEnd = null,
            source = "manual", autoFollow = false,
        )

        val row = rows("live-references-").single()
        assertEquals(23, row.int("chapter"))
        assertEquals(7, row.int("verseStart"), "display verse 6 is canonical verse 7 here")
        assertEquals(6, row.int("displayVerseStart"))
    }

    @Test
    fun `a range maps its end verse too`() {
        TrainingDataLogger.sessionId = "canonical-range"
        vm.logLiveReference(
            displayBookIndex = psalmsIndex, chapter = 22, verseStart = 5, verseEnd = 6,
            source = "manual", autoFollow = false,
        )

        val row = rows("live-references-").single()
        assertEquals(5, row.int("verseStart"))
        assertEquals(7, row.int("verseEnd"), "the end of the span is renumbered independently")
        assertEquals(6, row.int("displayVerseEnd"))
    }

    @Test
    fun `the book is named by its id, not by where it sits in the list`() {
        TrainingDataLogger.sessionId = "canonical-book-order"
        // 1 John is the second book in this module; `index + 1` would call it Exodus.
        assertEquals(1, johnIndex, "fixture check: 1 John must not be at its canonical position")
        vm.logLiveReference(
            displayBookIndex = johnIndex, chapter = 1, verseStart = 9, verseEnd = null,
            source = "manual", autoFollow = false,
        )

        assertEquals(62, rows("live-references-").single().int("book"))
    }

    @Test
    fun `an unresolvable reference falls back to the displayed numbers rather than inventing one`() {
        TrainingDataLogger.sessionId = "canonical-fallback"
        // Chapter 99 is not in the module, so there is no code reference to map through.
        vm.logLiveReference(
            displayBookIndex = psalmsIndex, chapter = 99, verseStart = 1, verseEnd = null,
            source = "manual", autoFollow = false,
        )

        val row = rows("live-references-").single()
        assertEquals(19, row.int("book"))
        assertEquals(99, row.int("chapter"))
        assertEquals(1, row.int("verseStart"))
    }

    @Test
    fun `a suggestion outcome names the book canonically, not by chip position`() {
        TrainingDataLogger.sessionId = "outcome-canonical"
        vm.onEngineScripture(
            bookId = 62, chapter = 1, verseStart = 9, verseEnd = null,
            verseText = "Если исповедуем грехи наши", matchType = "reverse",
        )
        awaitUntil("the detection to be staged") { vm.detectedReferences.value.isNotEmpty() }

        vm.clearDetectedReferences()

        val row = rows("suggestion-outcomes-").single()
        assertEquals(62, row.int("suggestedBook"), "the chip's list position must never be logged as a book id")
        assertEquals("dismissed", row["action"]?.jsonPrimitive?.content)
    }

    @Test
    fun `an operator flag names the live verse the same way a go-live does`() {
        TrainingDataLogger.sessionId = "flag-canonical"
        vm.logOperatorFlag(
            kind = "wrong_passage", bookName = "Псалтирь", chapter = 22, verseStart = 5,
        )

        val row = rows("operator-flags-").single()
        assertEquals(19, row.int("book"), "a flag with book 0 cannot be anchored to anything in triage")
        assertEquals(23, row.int("chapter"))
        assertEquals(22, row.int("displayChapter"))
    }

    @Test
    fun `a missed-passage flag still records no reference at all`() {
        TrainingDataLogger.sessionId = "flag-missed-canonical"
        vm.logOperatorFlag(kind = "missed_passage")

        val row = rows("operator-flags-").single()
        for (key in listOf("book", "chapter", "verseStart", "displayChapter")) {
            assertTrue(row[key] == JsonNull, "$key should stay null when there is nothing to anchor to")
        }
    }

    @Test
    fun `the verse shown in the live panel carries its book id`() {
        // The Help-Dev flag buttons read the live verse; a verse built without its book id logged
        // book 0 for the whole service (seen in the 2026-07-19 evening recording).
        val verses = kotlinx.coroutines.runBlocking { vm.getVersesForDisplay("Псалтирь", 22, 5) }
        assertEquals(19, verses.first().bookId)
    }
}
