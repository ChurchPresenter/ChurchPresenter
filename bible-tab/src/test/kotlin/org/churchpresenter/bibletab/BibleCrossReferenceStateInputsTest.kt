package org.churchpresenter.bibletab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleCrossReferenceStateInputsTest {

    private val fixture = """
        {"v":1,"r":{
          "043003016":"045005008 062004009",
          "043003017":"045005008 001001001",
          "043004001":"019023001",
          "001001001":"043001001-003"
        }}
    """.trimIndent()

    private val books = (1..66).map { "B$it" }

    private class Inputs(repository: CrossReferenceRepository) {
        var available by mutableStateOf(true)
        var docked by mutableStateOf(true)
        var repository by mutableStateOf(repository)
        var fallback by mutableStateOf((1..66).map { "B$it" })
        var chapter by mutableStateOf(3)
        var verseIndex by mutableStateOf(0)
        var verses by mutableStateOf(listOf("16. For God so loved", "17. For God sent not"))
        var token by mutableStateOf(0)
        var module by mutableStateOf<Any?>("module")
        var verseKnown by mutableStateOf(true)
    }

    private fun repository() = CrossReferenceRepository { fixture.toByteArray() }

    private fun ComposeUiTest.state(inputs: Inputs): BibleCrossReferenceState {
        lateinit var built: BibleCrossReferenceState
        setContent {
            built = rememberBibleCrossReferenceState(
                available = inputs.available,
                panelDocked = inputs.docked,
                repository = inputs.repository,
                fallbackAbbreviations = inputs.fallback,
                selectedBookIndex = 42,
                selectedChapter = inputs.chapter,
                selectedVerseIndex = inputs.verseIndex,
                verses = inputs.verses,
                verseSelectionToken = inputs.token,
                loadedModule = inputs.module,
                moduleRefFor = { _, _, _ -> null },
                canonicalRefForDisplay = { _, chapter, verse ->
                    Triple(43, chapter, verse.takeIf { inputs.verseKnown })
                },
                selectedVerseNumbers = { emptyList() },
                successors = { _, _, _ -> emptyList() },
            )
        }
        waitForIdle()
        return built
    }

    @Test
    fun `rows fall back to the book list's abbreviation when the module cannot resolve the verse`() =
        runComposeUiTest {
            val state = state(Inputs(repository()))
            waitUntil { state.rows.isNotEmpty() }

            assertTrue(state.rows.all { !it.available && it.preview.isEmpty() })
            assertEquals("B45 5:8", state.rows.first().label)
        }

    @Test
    fun `a new fallback list relabels the column`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }

        inputs.fallback = books.map { "X$it" }
        waitUntil { state.rows.firstOrNull()?.label?.startsWith("XB45") == true }
    }

    @Test
    fun `moving to the next verse re-anchors the column`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }

        inputs.verseIndex = 1
        waitUntil { state.anchors.singleOrNull()?.third == 17 }
        waitUntil { state.rows.any { it.bookId == 1 } }
    }

    @Test
    fun `a new chapter re-anchors and recounts the chips`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.counts.isNotEmpty() }

        inputs.chapter = 4
        inputs.verses = listOf("1. Let not your heart")
        waitUntil { state.anchors.singleOrNull()?.second == 4 }
        waitUntil { state.counts == mapOf(1 to 1) }
    }

    @Test
    fun `a verse the module cannot place leaves the column without an anchor`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.anchors.isNotEmpty() }

        inputs.verseKnown = false
        inputs.token++
        waitUntil { state.anchors.isEmpty() }
        waitUntil { state.rows.isEmpty() }
    }

    @Test
    fun `switching the feature off empties the column and the chips`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() && state.counts.isNotEmpty() }

        inputs.available = false
        waitUntil { state.rows.isEmpty() && state.counts.isEmpty() }
        assertNull(state.navigatedTo)
    }

    @Test
    fun `undocking the panel empties the column but keeps the chips`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() && state.counts.isNotEmpty() }

        inputs.docked = false
        waitUntil { state.rows.isEmpty() }
        assertTrue(state.counts.isNotEmpty())
    }

    @Test
    fun `a different dataset recounts the chips`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.counts.isNotEmpty() }

        inputs.repository = CrossReferenceRepository { """{"v":1,"r":{}}""".toByteArray() }
        waitUntil { state.counts.isEmpty() }
    }

    @Test
    fun `reloading the module resolves the column and the popover again`() = runComposeUiTest {
        val inputs = Inputs(repository())
        val state = state(inputs)
        waitUntil { state.rows.isNotEmpty() }
        state.popoverAnchor = Triple(43, 3, 16)
        waitUntil { state.popoverRows.size == 2 }

        inputs.module = "reloaded"
        waitForIdle()
        assertEquals(2, state.popoverRows.size)
        assertTrue(state.rows.isNotEmpty())

        state.closePopover()
        waitUntil { state.popoverRows.isEmpty() }
        assertEquals(-1, state.popoverIndex)
    }

    @Test
    fun `a passage read in order pools its references and spans its verses`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        waitUntil { state.rows.isNotEmpty() }

        state.anchorLiveVerse(Triple(43, 3, 16))
        state.anchorLiveVerse(Triple(43, 3, 17))
        waitForIdle()

        assertTrue(state.passageMode)
        assertEquals("3:16-17", state.passageSpan)
        waitUntil { state.rows.any { it.count > 1 } }

        state.anchorLiveVerse(Triple(1, 1, 1))
        assertEquals(listOf(Triple(1, 1, 1)), state.run)
        assertNull(state.passageSpan)
    }

    @Test
    fun `following a row pins the column until the operator picks again`() = runComposeUiTest {
        val state = state(Inputs(repository()))
        waitUntil { state.rows.isNotEmpty() }
        val rows = state.rows
        state.popoverAnchor = Triple(43, 3, 16)

        state.followed(rows.first())
        assertEquals(Triple(45, 5, 8), state.navigatedTo)
        assertNull(state.popoverAnchor)

        val epoch = state.anchorEpoch
        state.restartFrom()
        assertNull(state.navigatedTo)
        assertEquals(epoch + 1, state.anchorEpoch)
    }
}
