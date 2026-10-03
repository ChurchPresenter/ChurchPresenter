package org.churchpresenter.bibletab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleCrossReferenceLiveRunTest {

    private val fixture = """
        {"v":1,"r":{
          "043003016":"045005008 062004009",
          "043003017":"045005008 001001001",
          "043003018":"019023001",
          "040001001":"001001001"
        }}
    """.trimIndent()

    private class Inputs(repository: CrossReferenceRepository) {
        var repository by mutableStateOf(repository)
        var bookIndex by mutableStateOf(42)
        var bookId by mutableStateOf(43)
        var selectedNumbers by mutableStateOf<List<Int>>(emptyList())
        var learned by mutableStateOf<List<LearnedRef>>(emptyList())
        var named by mutableStateOf(false)
    }

    private fun repository(text: String = fixture) = CrossReferenceRepository { text.toByteArray() }

    private fun ComposeUiTest.state(inputs: Inputs): BibleCrossReferenceState {
        lateinit var built: BibleCrossReferenceState
        setContent {
            val bookId = inputs.bookId
            val named = inputs.named
            val numbers = inputs.selectedNumbers
            val learned = inputs.learned
            built = rememberBibleCrossReferenceState(
                available = true,
                panelDocked = true,
                repository = inputs.repository,
                fallbackAbbreviations = (1..66).map { "B$it" },
                selectedBookIndex = inputs.bookIndex,
                selectedChapter = 3,
                selectedVerseIndex = 0,
                verses = listOf("16. For God so loved", "17. For God sent not", "18. He that believeth"),
                verseSelectionToken = 0,
                loadedModule = "module",
                moduleRefFor = { book, chapter, verse ->
                    if (named) BibleViewModel.ModuleRef("N$book", chapter, verse, "text") else null
                },
                canonicalRefForDisplay = { _, chapter, verse -> Triple(bookId, chapter, verse) },
                selectedVerseNumbers = { numbers },
                successors = { _, _, _ -> learned },
            )
        }
        waitForIdle()
        return built
    }

    @Test
    fun `consecutive live verses become a passage whose rows count their sources`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        waitUntil { state.rows.isNotEmpty() }

        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))
        waitForIdle()

        assertTrue(state.passageMode)
        assertEquals("3:16-17", state.passageSpan)
        waitUntil { state.rows.any { it.count == 2 } }
    }

    @Test
    fun `a verse out of order starts a new run`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        state.anchorLiveVerse(Triple(43, 3, 17))
        state.anchorLiveVerse(Triple(43, 3, 16))
        waitForIdle()

        assertFalse(state.passageMode)
        assertEquals(listOf(Triple(43, 3, 16)), state.run)
    }

    @Test
    fun `learned successors lead the column and are not repeated`() = runComposeUiTest {
        val inputs = Inputs(repository())
        inputs.learned = listOf(LearnedRef(45, 5, 8, 3))
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }

        assertTrue(state.rows.first().learned)
        assertEquals(1, state.rows.count { it.bookId == 45 && it.chapter == 5 && it.verse == 8 })
    }

    @Test
    fun `a selection of several verses anchors on each of them`() = runComposeUiTest {
        val inputs = Inputs(repository())
        inputs.selectedNumbers = listOf(16, 17, 18, 19)
        val state = state(inputs)

        waitUntil { state.anchors.size == CROSS_REF_RANGE_ANCHORS }
    }

    @Test
    fun `following a row leaves the column alone until the anchor moves`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        waitUntil { state.rows.isNotEmpty() }
        val before = state.rows

        state.followed(state.rows.first())
        state.anchors = listOf(Triple(state.rows.first().bookId, state.rows.first().chapter, state.rows.first().verse))
        waitForIdle()
        assertEquals(before, state.rows)

        state.restartFrom()
        waitForIdle()
        assertEquals(null, state.navigatedTo)
    }

    @Test
    fun `the popover lists the clicked verse and empties when closed`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        state.popoverAnchor = Triple(43, 3, 18)
        waitUntil { state.popoverRows.isNotEmpty() }
        assertEquals(19, state.popoverRows.single().bookId)

        state.closePopover()
        waitUntil { state.popoverRows.isEmpty() }
    }

    @Test
    fun `another book re-anchors the column`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }

        inputs.bookIndex = 39
        inputs.bookId = 40
        waitUntil { state.anchors.firstOrNull()?.first == 40 }
    }

    @Test
    fun `a module that can name the verse relabels the rows`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }

        inputs.named = true
        state.restartFrom()
        waitUntil { state.rows.firstOrNull()?.label?.startsWith("N") == true }
    }

    @Test
    fun `a new dataset recounts the chips`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.counts.isNotEmpty() }

        inputs.repository = repository("""{"v":1,"r":{"043003018":"019023001"}}""")
        waitUntil { state.counts == mapOf(18 to 1) }
    }
}
