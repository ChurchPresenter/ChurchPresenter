@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderColumnTest {

    private val ordered = SongItem(
        number = "001",
        title = "Amazing Grace",
        songbook = "Hymnal",
        lyrics = listOf("[Verse 1]", "a", "{Chorus}", "b", "[Verse 2]", "c", "[Bridge]", "d"),
        sectionOrder = listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]", "{Chorus}", "{Chorus}"),
    )
    private val written =
        SongItem(number = "002", title = "Be Thou My Vision", songbook = "Hymnal", lyrics = listOf("a"))

    private fun ComposeUiTest.showOrder() {
        click(Text.COLUMNS)
        clickLast("Order")
        click("Song Library Manager")
    }

    @Test
    fun `the order column is off until it is switched on`() = withLibrary(listOf(ordered, written)) { _ ->
        assertFalse(isShowingText("ORDER"))

        showOrder()

        assertTrue(isShowingText("ORDER"))
        assertEquals(2, countShowing("V1") + countShowing("V2"))
        assertTrue(isShowingText("+2"), "five chips, then how many more")
        assertTrue(isShowingText("As written"), "a song with no order of its own says so")
    }

    @Test
    fun `Show all switches the order column on too`() = withLibrary(listOf(ordered, written)) { _ ->
        click(Text.COLUMNS)
        click(Text.SHOW_ALL)
        click("Song Library Manager")

        assertTrue(isShowingText("ORDER"))
    }

    @Test
    fun `hovering a song's order shows all of it`() = withLibrary(listOf(ordered, written)) { _ ->
        narrowToTitleOnly()
        showOrder()

        onAllNodesWithText("V1")[0].performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        waitForIdle()

        assertTrue(isShowingText("V1 C V2 C B C C"))
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 1_000L
    }
}
