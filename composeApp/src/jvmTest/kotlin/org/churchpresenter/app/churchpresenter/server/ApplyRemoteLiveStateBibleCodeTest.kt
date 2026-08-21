package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.settings.BibleSyncMode
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The follower's **reference-only** Bible branch — the one path [ApplyRemoteLiveStateTest] leaves
 * out, saying it "needs a real `Bible`, which `BibleViewModel`'s own suites already build". They
 * build one, but none of them drives this branch, so it has never been exercised by anything.
 *
 * It is the whole point of a second-campus link between congregations that do not share a language.
 * In [BibleSyncMode.FULL_REPLICA] the follower shows the primary's wording verbatim; in
 * [BibleSyncMode.REFERENCE_ONLY] it receives only the **canonical verse code** and resolves that
 * code in its own, independently configured Bible — so an English primary drives a Russian screen
 * in Russian, and the verse text on the wire is ignored.
 *
 * Two things make this worth its own suite rather than a happy-path check:
 *
 *  * **The follower's own numbering must win.** Translations disagree about where a verse lives
 *    (Synodal Psalm 135 vs KJV 136), which is exactly why the code exists. The fixture below gives
 *    the follower's Bible a *different* display numbering from the code, so a branch that echoed the
 *    incoming numbers instead of resolving them would fail here and pass against a same-numbering
 *    fixture.
 *  * **Every way it can fail is a quiet no-op**, not a crash and not stale content: no local Bible
 *    configured, and a code this translation has no verse at (a versification mismatch). Both still
 *    have to leave the follower switched into BIBLE mode.
 */
class ApplyRemoteLiveStateBibleCodeTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        // InstanceLinkLogger resolves its path from user.home once per JVM and every branch here
        // logs, so it is pinned to the test home before anything touches it.
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-apply-bible-code").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * A follower Bible whose display numbering differs from the canonical code, the way a Synodal
     * module does: canonical Psalm 23 is this module's Psalm 22, and the second verse of canonical
     * John 3:16 sits at its own 3:15.
     */
    private fun followerBible(): Bible = SpbFixture.loadedBible(
        dir,
        SpbFixture.buildContent(
            title = "Follower Translation",
            books = listOf(
                SpbFixture.Book(19, "Псалтирь", 23),
                SpbFixture.Book(43, "Иоанна", 3),
            ),
            verses = listOf(
                SpbFixture.Verse(
                    book = 19, chapter = 22, verse = 1, text = "Господь — Пастырь мой",
                    codeBook = 19, codeChapter = 23, codeVerse = 1
                ),
                SpbFixture.Verse(
                    book = 43, chapter = 3, verse = 15, text = "Ибо так возлюбил Бог мир",
                    codeBook = 43, codeChapter = 3, codeVerse = 16
                ),
            ),
        )
    )

    private fun apply(
        state: LiveStateDto,
        localPrimaryBible: Bible?,
        presenter: PresenterManager = PresenterManager(),
    ): PresenterManager {
        runBlocking {
            applyRemoteLiveState(
                state = state,
                presenterManager = presenter,
                instanceLinkViewModel = InstanceLinkViewModel(),
                bibleSyncMode = BibleSyncMode.REFERENCE_ONLY,
                localPrimaryBible = localPrimaryBible,
            )
        }
        return presenter
    }

    /** What the primary sends in reference-only mode: a code, plus wording the follower must ignore. */
    private fun codeState(book: Int, chapter: Int, verse: Int, verseRange: String? = null) = LiveStateDto(
        contentType = Presenting.BIBLE.name,
        bookName = "Psalms",
        chapter = chapter,
        verseNumber = verse,
        verseText = "The LORD is my shepherd; I shall not want.",
        verseRange = verseRange,
        verseCodeBook = book,
        verseCodeChapter = chapter,
        verseCodeVerse = verse,
    )

    // ── Resolving against the follower's own translation ────────────────────────

    @Test
    fun `the verse is shown in the follower's own translation, not the primary's wording`() {
        val presenter = apply(codeState(19, 23, 1), followerBible())

        val verse = presenter.selectedVerses.value.single()
        assertEquals("Господь — Пастырь мой", verse.verseText, "the follower renders its own translation")
        assertEquals("Псалтирь", verse.bookName, "and its own book name")
        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
    }

    @Test
    fun `the follower's own chapter and verse numbers are shown, not the incoming ones`() {
        // The whole reason the code exists: this module puts canonical Psalm 23 at its own Psalm 22.
        val presenter = apply(codeState(19, 23, 1), followerBible())

        val verse = presenter.selectedVerses.value.single()
        assertEquals(22, verse.chapter, "the incoming canonical chapter 23 must be translated, not echoed")
        assertEquals(1, verse.verseNumber)
    }

    @Test
    fun `a verse whose own numbering differs within the chapter is resolved too`() {
        val presenter = apply(codeState(43, 3, 16), followerBible())

        val verse = presenter.selectedVerses.value.single()
        assertEquals("Ибо так возлюбил Бог мир", verse.verseText)
        assertEquals("Иоанна", verse.bookName)
        assertEquals(15, verse.verseNumber, "this module numbers that verse 15, and says so")
    }

    @Test
    fun `the verse carries the follower's own translation name, so the screen labels it correctly`() {
        val bible = followerBible()

        val presenter = apply(codeState(19, 23, 1), bible)

        val verse = presenter.selectedVerses.value.single()
        assertEquals(bible.getBibleTitle(), verse.bibleName)
        assertEquals(bible.getBibleAbbreviation(), verse.bibleAbbreviation)
        assertTrue(verse.bibleName.isNotBlank(), "an unlabelled verse would read as coming from nowhere")
    }

    @Test
    fun `a multi-verse range is preserved, since only the wording is re-resolved`() {
        val presenter = apply(codeState(19, 23, 1, verseRange = "1-2"), followerBible())

        assertEquals("1-2", presenter.selectedVerses.value.single().verseRange)
    }

    // ── When the code cannot be resolved ────────────────────────────────────────

    @Test
    fun `a code this translation has no verse at leaves the screen alone but still switches mode`() {
        // A versification mismatch: the primary is on a book this follower's module does not carry.
        val presenter = PresenterManager()
        presenter.setPresentingMode(Presenting.LYRICS)

        apply(codeState(1, 1, 1), followerBible(), presenter)

        assertTrue(
            presenter.selectedVerses.value.isEmpty(),
            "showing nothing is right; showing the primary's wording would defeat reference-only mode"
        )
        assertEquals(
            Presenting.BIBLE, presenter.presentingMode.value,
            "the follower still moves off the song it was showing rather than leaving it up"
        )
    }

    @Test
    fun `a follower with no Bible configured is a quiet no-op`() {
        val presenter = apply(codeState(19, 23, 1), localPrimaryBible = null)

        assertTrue(presenter.selectedVerses.value.isEmpty())
        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
    }

    @Test
    fun `a state with no verse code falls back to the primary's wording`() {
        // Reference-only is only possible when the primary sent a code. An older primary that sends
        // none must not blank the follower's screen — it falls through to the full-replica path.
        val presenter = apply(
            LiveStateDto(
                contentType = Presenting.BIBLE.name,
                bookName = "Psalms",
                chapter = 23,
                verseNumber = 1,
                verseText = "The LORD is my shepherd; I shall not want.",
            ),
            followerBible(),
        )

        val verse = presenter.selectedVerses.value.single()
        assertEquals("The LORD is my shepherd; I shall not want.", verse.verseText)
        assertEquals("Psalms", verse.bookName, "with no code to resolve, the primary's own reference stands")
    }
}
