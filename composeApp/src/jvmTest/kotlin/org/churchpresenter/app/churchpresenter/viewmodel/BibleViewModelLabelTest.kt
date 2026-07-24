package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two small label helpers behind auto-follow's training log and detection chips.
 *
 * [toMatchTypeLabel] must mirror the engine's own matchType strings exactly — the training log
 * correlates the two, so a drift here silently mislabels every logged suggestion. [buildDetectionLabel]
 * is what the operator reads on a detected-reference chip; it has to render a bare book+chapter, a
 * single verse and a verse range each distinctly, and degrade to just the chapter number when the
 * book index is unknown rather than showing a blank chip.
 */
class BibleViewModelLabelTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-label-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Label Bible",
            books = listOf(SpbFixture.Book(1, "Genesis", 1)),
            verses = (1..5).map { SpbFixture.Verse(1, 1, it, "Genesis one verse $it") },
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.books.value.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
    }

    @AfterTest
    fun cleanUp() {
        runCatching { vm.dispose() }
        dir.deleteRecursively()
    }

    // ── toMatchTypeLabel: every detection source has a stable string ─────────────

    @Test
    fun `each detection source maps to the engine's matchType string`() {
        assertEquals("explicit", DetectionSource.EXPLICIT.toMatchTypeLabel())
        assertEquals("reverse", DetectionSource.REVERSE.toMatchTypeLabel())
        assertEquals("continuation", DetectionSource.CONTINUATION.toMatchTypeLabel())
        assertEquals("chapter-scan", DetectionSource.CHAPTER_SCAN.toMatchTypeLabel())
        assertEquals("chapter-history", DetectionSource.CHAPTER_HISTORY.toMatchTypeLabel())
    }

    @Test
    fun `every detection source has a non-blank label`() {
        // A blank would correlate to nothing in the training log; the when must be exhaustive.
        DetectionSource.entries.forEach {
            assertEquals(true, it.toMatchTypeLabel().isNotBlank(), "$it produced a blank matchType label")
        }
    }

    // ── buildDetectionLabel: how a detected reference reads on its chip ──────────

    @Test
    fun `a bare book and chapter reads without a verse part`() {
        assertEquals("Genesis 1", vm.buildDetectionLabel(bookIndex = 0, chapter = 1, vs = null, ve = null))
    }

    @Test
    fun `a single verse appends just that verse`() {
        assertEquals("Genesis 1:3", vm.buildDetectionLabel(bookIndex = 0, chapter = 1, vs = 3, ve = null))
    }

    @Test
    fun `a verse range appends the whole span`() {
        assertEquals("Genesis 1:3-5", vm.buildDetectionLabel(bookIndex = 0, chapter = 1, vs = 3, ve = 5))
    }

    @Test
    fun `an end verse that is not past the start is not shown as a range`() {
        // ve must be strictly greater than vs to read as a range; equal or lesser falls back to
        // the single verse, so "3-3" or a malformed "5-3" never reaches a chip.
        assertEquals("Genesis 1:3", vm.buildDetectionLabel(bookIndex = 0, chapter = 1, vs = 3, ve = 3))
    }

    @Test
    fun `an unknown book index degrades to the chapter number alone`() {
        assertEquals(
            "7",
            vm.buildDetectionLabel(bookIndex = 999, chapter = 7, vs = 2, ve = 4),
            "a detection for a book this bible does not have must still show something, not a blank chip",
        )
    }
}
