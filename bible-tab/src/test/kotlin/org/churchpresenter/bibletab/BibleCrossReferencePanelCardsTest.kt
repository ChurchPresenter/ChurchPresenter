package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BibleCrossReferencePanelCardsTest {

    private fun row(
        label: String,
        learned: Boolean = false,
        available: Boolean = true,
        preview: String = "",
        count: Int = 0,
    ) = CrossRefRow(
        bookId = 1, chapter = 1, verse = 1, endVerse = null, learned = learned,
        label = label, preview = preview, available = available, count = count,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `fallback rows take the book list's abbreviation, or nothing past its end`() {
        val spec = CrossRefRowSpec(
            { _, _, _ -> null }, listOf("Gen", "Exo"), bookId = 2, chapter = 3, verse = 4, endVerse = 6,
        )
        val row = crossRefRow(spec)
        assertEquals("Exo 3:4-6", row.label)
        assertTrue(!row.available && row.preview.isEmpty())

        assertEquals(" 1:1", crossRefRow(spec.copy(bookId = 99, chapter = 1, verse = 1, endVerse = null)).label)
    }

    @Test
    fun `module rows use the module's own position and text`() {
        val spec = CrossRefRowSpec(
            { _, _, _ -> BibleViewModel.ModuleRef("Jn", 3, 17, "text") }, emptyList(), 43, 3, 16, null,
            learned = true, count = 2,
        )
        val row = crossRefRow(spec)
        assertEquals("Jn 3:17", row.label)
        assertTrue(row.available && row.learned)
        assertEquals(2, row.count)
    }

    @Test
    fun `the panel heads learned rows, counts pooled ones, and reports every action`() = runComposeUiTest {
        val clicks = mutableListOf<String>()
        var rows by mutableStateOf(
            listOf(
                row("Ps 23:1", count = 3, preview = "The Lord is my shepherd"),
                row("Jn 1:1", learned = true),
                row("Gen 1:1", available = false),
            ),
        )
        var span by mutableStateOf<String?>("3:16-18")
        setContent {
            MaterialTheme {
                Box(Modifier.size(400.dp, 600.dp)) {
                    CrossReferencePanel(
                        rows = rows,
                        selectedIndex = 0,
                        onClick = { clicks += "click $it" },
                        onDoubleClick = { clicks += "double $it" },
                        onAddToSchedule = { clicks += "add $it" },
                        onClose = { clicks += "close" },
                        passageSpan = span,
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(has("Passage 3:16-18"))
        assertTrue(has("Often next"))
        assertTrue(has("×3"))
        assertTrue(has("The Lord is my shepherd"))

        onAllNodes(hasText("Ps 23:1"), useUnmergedTree = true)[0].performClick()
        onAllNodes(hasContentDescription("Add to Schedule Jn 1:1"), useUnmergedTree = true)[0].performClick()
        onAllNodes(hasContentDescription("Close panel"), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertTrue("add 1" in clicks && "close" in clicks, clicks.toString())
        assertTrue(onAllNodes(hasContentDescription("Add to Schedule Gen 1:1"), useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty())

        span = null
        rows = emptyList()
        waitForIdle()
        assertTrue(has("Refs"))
        assertTrue(has("No cross references"))
    }

    @Test
    fun `the popover lists the verse's references and can be kept open or dismissed`() = runComposeUiTest {
        val events = mutableListOf<String>()
        var rows by mutableStateOf(listOf(row("Rom 5:8", preview = "God commendeth"), row("1Jn 4:9")))
        setContent {
            MaterialTheme {
                CrossReferencePopover(
                    title = "John 3:16",
                    rows = rows,
                    onDismiss = { events += "dismiss" },
                    onDock = { events += "dock" },
                    onOpen = { events += "open ${it.label}" },
                    onGoLive = { events += "live ${it.label}" },
                    onAddToSchedule = { events += "add ${it.label}" },
                )
            }
        }
        waitForIdle()
        assertTrue(has("John 3:16") && has("Esc to close") && has("God commendeth"))

        onAllNodes(hasContentDescription("Keep open"), useUnmergedTree = true)[0].performClick()
        onAllNodes(hasContentDescription("Add to Schedule 1Jn 4:9"), useUnmergedTree = true)[0].performClick()
        onAllNodes(hasContentDescription("Close"), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertTrue(listOf("dock", "add 1Jn 4:9", "dismiss").all { it in events }, events.toString())

        rows = emptyList()
        waitForIdle()
        assertTrue(has("No cross references"))
    }

    @Test
    fun `the chip shows its count and reports clicks whether active or not`() = runComposeUiTest {
        var active by mutableStateOf(false)
        var clicks = 0
        setContent {
            MaterialTheme { CrossRefChip(count = 7, active = active, tooltipText = "refs", onClick = { clicks++ }) }
        }
        waitForIdle()
        assertTrue(has("7"))
        onAllNodes(hasText("7"), useUnmergedTree = true)[0].performClick()

        active = true
        waitForIdle()
        onAllNodes(hasText("7"), useUnmergedTree = true)[0].performClick()
        waitForIdle()
        assertEquals(2, clicks)
    }
}
