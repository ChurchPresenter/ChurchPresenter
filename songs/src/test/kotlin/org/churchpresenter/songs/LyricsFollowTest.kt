@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The lyrics list follows the selection only as far as it takes to show it, so a row the operator
 * can already see never moves out from under the second press of a double-click (#1028).
 */
class LyricsFollowTest {

    private val window = 300

    /** A 300 px window over [heights] px rows, density 1 so dp are pixels. */
    private fun followList(
        heights: List<Int>,
        block: ComposeUiTest.(LazyListState, CoroutineScope) -> Unit,
    ) = runComposeUiTest {
        val state = LazyListState()
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
            ) {
                Box(Modifier.height(window.dp)) {
                    LazyColumn(state = state) {
                        items(heights) { h -> Box(Modifier.fillMaxWidth().height(h.dp)) }
                    }
                }
            }
        }
        waitForIdle()
        block(state, scope)
    }

    private fun ComposeUiTest.follow(state: LazyListState, scope: CoroutineScope, index: Int) {
        var done = false
        scope.launch {
            state.followLyricsEntry(index)
            done = true
        }
        waitUntil { done }
        waitForIdle()
    }

    private fun LazyListState.item(index: Int): LazyListItemInfo =
        layoutInfo.visibleItemsInfo.first { it.index == index }

    private val rows = List(20) { 100 }

    @Test
    fun `a row already in full view does not move the list`() = followList(rows) { state, scope ->
        assertIs<LyricsFollow.Stay>(lyricsFollow(state.layoutInfo, 1))
        follow(state, scope, 1)
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun `a row cut off at the bottom scrolls up just far enough to show it`() =
        followList(List(20) { 120 }) { state, scope ->
            // Rows at 0, 120, 240: the third is cut off 60 px short of the window's 300.
            assertEquals(LyricsFollow.By(60f), lyricsFollow(state.layoutInfo, 2))
            follow(state, scope, 2)
            val row = state.item(2)
            assertEquals(window, row.offset + row.size)
        }

    @Test
    fun `a row below the window comes in at the bottom edge`() = followList(rows) { state, scope ->
        assertIs<LyricsFollow.ToBottom>(lyricsFollow(state.layoutInfo, 10))
        follow(state, scope, 10)
        val row = state.item(10)
        assertEquals(window, row.offset + row.size)
    }

    @Test
    fun `a row above the window lines up at the top`() = followList(rows) { state, scope ->
        follow(state, scope, 15)
        assertIs<LyricsFollow.ToTop>(lyricsFollow(state.layoutInfo, 5))
        follow(state, scope, 5)
        assertEquals(5, state.firstVisibleItemIndex)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun `a row cut off at the top scrolls down to show its first line`() = followList(rows) { state, scope ->
        var done = false
        scope.launch {
            state.scrollToItem(3, scrollOffset = 40)
            done = true
        }
        waitUntil { done }
        waitForIdle()
        assertEquals(LyricsFollow.By(-40f), lyricsFollow(state.layoutInfo, 3))
        follow(state, scope, 3)
        assertEquals(0, state.item(3).offset)
    }

    @Test
    fun `a section taller than the window stays while it fills half of it`() =
        followList(listOf(100, 500, 100)) { state, scope ->
            // The tall section starts at 100 and fills the last 200 px of the window.
            assertIs<LyricsFollow.Stay>(lyricsFollow(state.layoutInfo, 1))
            follow(state, scope, 1)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }

    @Test
    fun `a section taller than the window showing only a sliver lines up at the top`() =
        followList(listOf(250, 500, 100)) { state, scope ->
            assertEquals(LyricsFollow.By(250f), lyricsFollow(state.layoutInfo, 1))
            follow(state, scope, 1)
            assertEquals(0, state.item(1).offset)
        }
}
