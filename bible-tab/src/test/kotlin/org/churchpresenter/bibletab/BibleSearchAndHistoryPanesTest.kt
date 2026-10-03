package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleSearchAndHistoryPanesTest {

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `the search mode chip names each mode and cycles on click, wide or narrow`() = runComposeUiTest {
        var mode by mutableStateOf(BibleSearchMode.AUTO)
        var wide by mutableStateOf(true)
        var cycles = 0
        setContent {
            MaterialTheme {
                Box(Modifier.width(if (wide) 800.dp else 300.dp)) {
                    BibleSearchRow(
                        searchQuery = "love", searchPlaceholder = "Search", searchMode = mode,
                        scopeOptions = listOf("All", "Book"), selectedScope = "All",
                        modeOptions = listOf("Contains", "Exact"), selectedMode = "Contains",
                        onQueryChange = {}, onClear = {}, onSubmit = {}, onFocusChanged = {},
                        onCycleSearchMode = { cycles++ }, onScopeSelected = {}, onModeSelected = {},
                    )
                }
            }
        }
        listOf(
            BibleSearchMode.AUTO to "Auto",
            BibleSearchMode.REFERENCE to "Reference",
            BibleSearchMode.TEXT to "Text",
        ).forEach { (m, label) ->
                mode = m
                waitForIdle()
                assertTrue(has(label), label)
                onAllNodes(hasText(label), useUnmergedTree = true)[0].performClick()
                waitForIdle()
            }
        assertEquals(3, cycles)

        wide = false
        waitForIdle()
        assertTrue(has("Text"))
    }

    @Test
    fun `results follow the query and the list they are handed`() = runComposeUiTest {
        var results by mutableStateOf(listOf(BibleSearch("John", "3", "16", "For God so loved the world")))
        var query by mutableStateOf("loved")
        val chosen = mutableListOf<BibleSearch>()
        setContent {
            MaterialTheme {
                Box(Modifier.size(500.dp, 400.dp)) {
                    Column { BibleSearchResults(results, query, onResultChosen = { chosen += it }) }
                }
            }
        }
        waitForIdle()
        assertTrue(has("Found 1 result(s)"))

        query = "world"
        results = results + BibleSearch("1 John", "4", "9", "sent his only begotten Son into the world")
        waitForIdle()
        assertTrue(has("Found 2 result(s)"))
        onAllNodes(hasText("begotten", substring = true), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals("1 John", chosen.single().book)
    }

    @Test
    fun `history collapses, clears and reports its entries`() = runComposeUiTest {
        var expanded by mutableStateOf(true)
        var entries by mutableStateOf(
            listOf(
                BibleViewModel.HistoryEntry("John", 3, 16, "For God so loved", verseRange = "16-17"),
                BibleViewModel.HistoryEntry("Genesis", 1, 1, "In the beginning"),
            ),
        )
        val events = mutableListOf<String>()
        setContent {
            MaterialTheme {
                Box(Modifier.size(500.dp, 400.dp)) {
                    BibleHistoryPanel(
                        entries = entries, expanded = expanded, selectedIndex = 1,
                        onToggleExpanded = { events += "toggle" }, onClear = { events += "clear" },
                        onEntryClick = { events += "click $it" }, onEntryDoubleClick = { events += "double $it" },
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(has("John 3:16-17") && has("Genesis 1:1"))

        onAllNodes(hasText("History"), useUnmergedTree = true)[0].performClick()
        onAllNodes(hasContentDescription("Clear"), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertTrue("toggle" in events && "clear" in events, events.toString())

        expanded = false
        waitForIdle()
        entries = emptyList()
        waitForIdle()
        assertTrue(!has("History"))
    }

    @Test
    fun `an inert chip shows its label with or without a leading icon`() = runComposeUiTest {
        var icon by mutableStateOf(false)
        setContent {
            MaterialTheme {
                Column {
                    InertChip(label = { Text("Hold") })
                    InertChip(
                        label = { Text("Live") },
                        modifier = Modifier.width(120.dp),
                        leadingIcon = if (icon) ({ Text("*") }) else null,
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(has("Hold") && has("Live") && !has("*"))

        icon = true
        waitForIdle()
        assertTrue(has("*"))
    }

    @Test
    fun `the translation order button names translations by their display names`() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent {
            MaterialTheme {
                Column {
                    TranslationOrderSelector(
                        label = "Translations",
                        translations = listOf(
                            BibleTranslationSettings(fileName = "kjv.spb"),
                            BibleTranslationSettings(fileName = "rst.spb"),
                        ),
                        displayNames = mapOf("kjv.spb" to "King James  (1611)"),
                        onMove = { i, o -> moves += i to o },
                    )
                    TranslationOrderSelector(
                        label = "Second",
                        translations = listOf(BibleTranslationSettings(fileName = "web.spb")),
                        displayNames = emptyMap(),
                        onMove = { _, _ -> },
                        modifier = Modifier.width(200.dp),
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(has("King James"))
        assertTrue(!has("(1611)"))
        assertTrue(has("web"))
    }
}
