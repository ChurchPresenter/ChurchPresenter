@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BibleColumnsScrollAheadTest {

    private class Item(override val index: Int, override val offset: Int, override val size: Int) : LazyListItemInfo {
        override val key: Any = index
    }

    private class Layout(
        override val visibleItemsInfo: List<LazyListItemInfo>,
        override val viewportStartOffset: Int = 0,
        override val viewportEndOffset: Int = 100,
    ) : LazyListLayoutInfo {
        override val totalItemsCount: Int = 50
    }

    private fun rows(vararg indexes: Int) = indexes.map { Item(it, (it - indexes.first()) * 30, 30) }

    @Test
    fun `nothing visible needs no scroll`() = assertEquals(0f, scrollAheadAmount(Layout(emptyList()), 0))

    @Test
    fun `an anchor well above the bottom needs no scroll`() =
        assertEquals(0f, scrollAheadAmount(Layout(rows(0, 1, 2, 3)), 0))

    @Test
    fun `the second verse after a visible anchor sets the scroll`() {
        val layout = Layout(listOf(Item(2, 30, 30), Item(3, 60, 30), Item(5, 120, 30), Item(4, 90, 30)))
        assertEquals(50f, scrollAheadAmount(layout, 3))
    }

    @Test
    fun `only the next verse visible adds a row's height`() =
        assertEquals(50f, scrollAheadAmount(Layout(rows(0, 1, 2, 3)), 2))

    @Test
    fun `nothing after the anchor scrolls two rows, capped by the anchor's offset`() =
        assertEquals(60f, scrollAheadAmount(Layout(rows(0, 1, 2, 3)), 3))

    @Test
    fun `an anchor off screen is limited only by what it wants`() {
        val layout = Layout(listOf(Item(5, 0, 30), Item(6, 30, 30)), viewportEndOffset = 50)
        assertEquals(60f, scrollAheadAmount(layout, 7))
    }

    private val chapter = (1..40).map { "$it. verse number $it" }

    @Test
    fun `the live panel follows the live verse down the chapter and back`() = runComposeUiTest {
        var live by mutableStateOf(emptySet<Int>())
        var verses by mutableStateOf(chapter)
        val clicked = mutableListOf<Int>()
        var withClicks by mutableStateOf(true)
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    LiveChapterPanel(
                        verses = verses,
                        liveVerseNumbers = live,
                        onVerseClicked = if (withClicks) { n -> clicked += n } else null,
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("1. verse number 1").performClick()
        live = setOf(4)
        waitForIdle()
        live = setOf(38)
        waitForIdle()
        live = setOf(40)
        waitForIdle()
        verses = chapter.take(20)
        withClicks = false
        waitForIdle()
        live = setOf(2)
        waitForIdle()

        assertEquals(listOf(1), clicked)
    }

    @Test
    fun `the verse column with only its required arguments scrolls ahead as the selection moves`() =
        runComposeUiTest {
            var selected by mutableIntStateOf(-1)
            var verses by mutableStateOf(chapter)
            setContent {
                MaterialTheme {
                    Box(Modifier.size(300.dp, 200.dp)) {
                        BibleVerseColumn(verses = verses, selectedIndex = selected, onItemSelected = { selected = it })
                    }
                }
            }
            waitForIdle()
            onNodeWithText("2. verse number 2").performClick()
            waitForIdle()
            selected = 5
            waitForIdle()
            selected = 39
            waitForIdle()
            verses = chapter.take(10)
            selected = 3
            waitForIdle()

            assertEquals(3, selected)
        }

    @Test
    fun `a browser column brings a selection made elsewhere into view and ignores one out of range`() =
        runComposeUiTest {
            var selected by mutableIntStateOf(0)
            setContent {
                MaterialTheme {
                    Box(Modifier.size(200.dp, 150.dp)) {
                        BibleBrowserColumn(
                            items = chapter,
                            selectedIndex = selected,
                            onItemSelected = { selected = it },
                        )
                    }
                }
            }
            waitForIdle()
            selected = 30
            waitForIdle()
            selected = 99
            waitForIdle()
            selected = 1
            waitForIdle()
            onNodeWithText("2. verse number 2").performClick()
            waitForIdle()

            assertEquals(1, selected)
        }

    // ── Jumping to a verse that is off screen (#799) ─────────────────────────────────────────────

    @Test
    fun `a verse below the window is jumped to with the one before it`() =
        assertEquals(48, jumpTargetFor(Layout(rows(0, 1, 2, 3)), 49))

    @Test
    fun `a verse above the window is jumped to the same way`() =
        assertEquals(4, jumpTargetFor(Layout(rows(20, 21, 22)), 5))

    @Test
    fun `the first verse has nothing before it to show`() =
        assertEquals(0, jumpTargetFor(Layout(rows(20, 21, 22)), 0))

    @Test
    fun `a verse already on screen, even in part, is left to the scroll ahead`() =
        assertNull(jumpTargetFor(Layout(rows(0, 1, 2, 3)), 3))

    @Test
    fun `before the first layout there is nothing to jump from`() =
        assertNull(jumpTargetFor(Layout(emptyList()), 10))
}
