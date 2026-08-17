package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.CrossReferenceRepository
import org.churchpresenter.app.churchpresenter.data.LearnedRef
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleCrossReferenceStateTest {

    private val fixture = """
        {"v":1,"r":{
          "043003016":"045005008 062004009",
          "043003017":"045005008 001001001",
          "043003018":"019023001",
          "001001001":"043001001-003"
        }}
    """.trimIndent()

    private fun repository() = CrossReferenceRepository { fixture.toByteArray() }

    private val books = (1..66).map { "B$it" }

    private fun moduleRef(bookId: Int, chapter: Int, verse: Int) =
        BibleViewModel.ModuleRef("Bk$bookId", chapter, verse, "text of $bookId $chapter:$verse")

    private fun ComposeUiTest.state(
        available: Boolean = true,
        panelDocked: Boolean = true,
        selectedVerseIndex: Int = 0,
        verses: List<String> = listOf("16. For God so loved", "17. For God sent not", "18. He that believeth"),
        selectedVerseNumbers: () -> List<Int> = { emptyList() },
        successors: (Int, Int, Int) -> List<LearnedRef> = { _, _, _ -> emptyList() },
        moduleRefFor: (Int, Int, Int) -> BibleViewModel.ModuleRef? = ::moduleRef,
        drive: (BibleCrossReferenceState) -> Unit = {},
    ): BibleCrossReferenceState {
        lateinit var built: BibleCrossReferenceState
        setContent {
            built = rememberBibleCrossReferenceState(
                available = available,
                panelDocked = panelDocked,
                repository = repository(),
                fallbackAbbreviations = books,
                selectedBookIndex = 42,
                selectedChapter = 3,
                selectedVerseIndex = selectedVerseIndex,
                verses = verses,
                verseSelectionToken = 0,
                loadedModule = "module",
                moduleRefFor = moduleRefFor,
                canonicalRefForDisplay = { _, chapter, verse -> Triple(43, chapter, verse) },
                selectedVerseNumbers = selectedVerseNumbers,
                successors = successors,
            )
        }
        waitForIdle()
        drive(built)
        waitForIdle()
        return built
    }

    @Test
    fun `the column describes the selected verse`() {
        runComposeUiTest {
            val state = state()

            waitUntil("the column resolved") { state.rows.isNotEmpty() }
            assertEquals(listOf(45 to 5, 62 to 4), state.rows.map { it.bookId to it.chapter })
            assertEquals(
                listOf(43, 3, 16),
                listOf(state.anchors.single().first, state.anchors.single().second, state.anchors.single().third),
            )
        }
    }

    @Test
    fun `a verse with nothing to say gets an empty column rather than an error`() {
        runComposeUiTest {
            val state = state(verses = listOf("99. A verse TSK never mentions"))

            waitForIdle()
            assertTrue(state.rows.isEmpty())
        }
    }

    @Test
    fun `the column is empty while the feature is switched off`() {
        runComposeUiTest {
            val state = state(available = false)

            waitForIdle()
            assertTrue(state.rows.isEmpty())
            assertTrue(state.counts.isEmpty(), "no chips either when the dataset is not available")
        }
    }

    @Test
    fun `the column is empty while the panel is undocked`() {
        runComposeUiTest {
            val state = state(panelDocked = false)

            waitForIdle()
            assertTrue(state.rows.isEmpty())
        }
    }

    @Test
    fun `a multi-verse selection is anchored on at most three verses`() {
        runComposeUiTest {
            val state = state(selectedVerseNumbers = { listOf(16, 17, 18, 19, 20) })

            waitUntil("the column resolved") { state.rows.isNotEmpty() }
            assertEquals(3, state.anchors.size, "a long passage would otherwise scroll near-duplicates")
        }
    }

    @Test
    fun `what this operator usually shows next comes before what the dataset points at`() {
        runComposeUiTest {
            val state = state(successors = { _, _, _ -> listOf(LearnedRef(58, 11, 1, count = 4)) })

            waitUntil("the column resolved") { state.rows.isNotEmpty() }
            assertTrue(state.rows.first().learned, "a habit leads the list")
            assertEquals(58, state.rows.first().bookId)
        }
    }

    @Test
    fun `a habit is not repeated as a bare cross-reference`() {
        runComposeUiTest {
            val state = state(successors = { _, _, _ -> listOf(LearnedRef(45, 5, 8, count = 4)) })

            waitUntil("the column resolved") { state.rows.isNotEmpty() }
            assertEquals(
                1,
                state.rows.count { it.bookId == 45 && it.chapter == 5 && it.verse == 8 },
                "the same reference must not appear twice",
            )
        }
    }

    @Test
    fun `a row falls back to the book list when the module has no such verse`() {
        runComposeUiTest {
            val state = state(moduleRefFor = { _, _, _ -> null })

            waitUntil("the column resolved") { state.rows.isNotEmpty() }
            assertEquals("B45", state.rows.first().label.substringBefore(" "))
        }
    }

    @Test
    fun `each verse of the chapter is counted`() {
        runComposeUiTest {
            val state = state()

            waitUntil("the counts resolved") { state.counts.isNotEmpty() }
            assertEquals(2, state.counts[16])
            assertEquals(2, state.counts[17])
            assertEquals(1, state.counts[18])
        }
    }

    @Test
    fun `a verse with nothing to say gets no chip at all`() {
        runComposeUiTest {
            val state = state(verses = listOf("16. For God so loved", "99. Nothing here"))

            waitUntil("the counts resolved") { state.counts.isNotEmpty() }
            assertNull(state.counts[99], "an absent entry is what draws no chip")
        }
    }

    @Test
    fun `a line with no verse number in it is skipped`() {
        runComposeUiTest {
            val state = state(verses = listOf("16. For God so loved", "not a verse line at all"))

            waitUntil("the counts resolved") { state.counts.isNotEmpty() }
            assertEquals(1, state.counts.size)
        }
    }

    @Test
    fun `opening the popover on a verse lists that verse's references`() {
        runComposeUiTest {
            val state = state(drive = { it.popoverAnchor = Triple(43, 3, 18) })

            waitUntil("the popover resolved") { state.popoverRows.isNotEmpty() }
            assertEquals(listOf(19), state.popoverRows.map { it.bookId })
        }
    }

    @Test
    fun `closing the popover empties it`() {
        runComposeUiTest {
            val state = state(drive = { it.popoverAnchor = Triple(43, 3, 16) })
            waitUntil("the popover resolved") { state.popoverRows.isNotEmpty() }

            state.closePopover()
            waitUntil("the popover emptied") { state.popoverRows.isEmpty() }

            assertEquals(-1, state.popoverIndex)
            assertNull(state.popoverAnchor)
        }
    }

    @Test
    fun `following a reference remembers where it sent the selection`() {
        runComposeUiTest {
            val state = state(drive = { it.popoverAnchor = Triple(43, 3, 16) })
            waitUntil("the popover resolved") { state.popoverRows.isNotEmpty() }
            val row = state.popoverRows.first()

            state.followed(row)

            assertEquals(Triple(row.bookId, row.chapter, row.verse), state.navigatedTo)
            assertNull(state.popoverAnchor, "following closes the popover it was opened from")
        }
    }

    @Test
    fun `a single live verse is not yet a passage`() {
        val state = BibleCrossReferenceState()

        state.anchorLiveVerse(Triple(43, 3, 16))

        assertTrue(state.anchorIsLive)
        assertTrue(!state.passageMode)
        assertNull(state.passageSpan)
    }

    @Test
    fun `reading on through a chapter builds a passage`() {
        val state = BibleCrossReferenceState()

        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))
        state.anchorLiveVerse(Triple(43, 3, 18))

        assertTrue(state.passageMode)
        assertEquals("3:16-18", state.passageSpan)
        assertEquals(3, state.run.size)
    }

    @Test
    fun `jumping to another book starts the passage over`() {
        val state = BibleCrossReferenceState()
        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))

        state.anchorLiveVerse(Triple(45, 5, 8))

        assertEquals(listOf(Triple(45, 5, 8)), state.run)
        assertTrue(!state.passageMode)
    }

    @Test
    fun `jumping to another chapter starts the passage over`() {
        val state = BibleCrossReferenceState()
        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))

        state.anchorLiveVerse(Triple(43, 4, 1))

        assertEquals(listOf(Triple(43, 4, 1)), state.run)
    }

    @Test
    fun `going back up the chapter starts the passage over`() {
        val state = BibleCrossReferenceState()
        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))

        state.anchorLiveVerse(Triple(43, 3, 15))

        assertEquals(listOf(Triple(43, 3, 15)), state.run)
    }

    @Test
    fun `skipping a verse forward still continues the passage`() {
        val state = BibleCrossReferenceState()
        state.anchorLiveVerse(Triple(43, 3, 16))

        state.anchorLiveVerse(Triple(43, 3, 18))

        assertEquals(2, state.run.size)
        assertEquals("3:16-18", state.passageSpan)
    }

    @Test
    fun `browsing away leaves the run alone but stops describing a passage`() {
        val state = BibleCrossReferenceState()
        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))

        state.anchorIsLive = false

        assertEquals(2, state.run.size, "what was read is not thrown away by looking ahead")
        assertTrue(!state.passageMode)
        assertNull(state.passageSpan)
    }

    @Test
    fun `going live clears where the column had sent the selection`() {
        val state = BibleCrossReferenceState()
        state.navigatedTo = Triple(45, 5, 8)

        state.anchorLiveVerse(Triple(43, 3, 16))

        assertNull(state.navigatedTo)
    }

    @Test
    fun `picking a verse again asks the column to follow it once more`() {
        val state = BibleCrossReferenceState()
        state.navigatedTo = Triple(45, 5, 8)
        val before = state.anchorEpoch

        state.restartFrom()

        assertNull(state.navigatedTo)
        assertEquals(before + 1, state.anchorEpoch)
    }
}
