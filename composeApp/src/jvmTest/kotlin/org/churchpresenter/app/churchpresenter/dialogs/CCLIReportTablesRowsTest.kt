@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.SongSummary
import org.churchpresenter.app.churchpresenter.data.VerseSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CCLIReportTablesRowsTest {

    @Test
    fun `songs never sung still draw their badges, and blanks read as dashes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SongsReportContent(
                    listOf(
                        song("Silent", count = 0, author = "", songbook = "", ccli = ""),
                        song("Quiet", count = 0),
                    ),
                ) {}
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText(CcliLabel.BLANK).fetchSemanticsNodes().size >= 3)
    }

    @Test
    fun `a new song list replaces the rows, and a hovered row's clear removes it`() = runComposeUiTest {
        var songs by mutableStateOf(listOf(song("Amazing Grace", count = 3), song("Be Thou My Vision", count = 1)))
        val cleared = mutableListOf<SongSummary>()
        setContent { MaterialTheme { SongsReportContent(songs) { cleared += it } } }
        waitForIdle()

        songs = listOf(
            song("How Great Thou Art", count = 5),
            song("Amazing Grace", count = 3),
            song("Abide", count = 9),
        )
        waitForIdle()
        onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Abide").performMouseInput { enter(center); moveBy(center) }
        waitForIdle()
        onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Abide").performMouseInput { exit() }
        onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Abide").performClick()
        waitForIdle()

        assertEquals(listOf("Abide"), cleared.map { it.title })
        assertTrue(onAllNodesWithText("How Great Thou Art").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `verses never shown still draw, and a new verse list replaces the rows`() = runComposeUiTest {
        var verses by mutableStateOf(listOf(verse("John", count = 0), verse("Romans", count = 0)))
        val cleared = mutableListOf<VerseSummary>()
        setContent { MaterialTheme { BibleReportContent(verses) { cleared += it } } }
        waitForIdle()

        verses = listOf(
            verse("Psalms", count = 4),
            verse("John", count = 2),
            verse("Genesis", number = 1, count = 1, bible = ""),
        )
        waitForIdle()
        val tag = REPORT_CLEAR_ROW_TAG
        assertTrue(onAllNodesWithText(CcliLabel.BLANK).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(tag + "Psalms 3:16").performMouseInput { enter(center) }
        onNodeWithTag(tag + "Psalms 3:16").performClick()
        waitForIdle()

        assertEquals(listOf("Psalms"), cleared.map { it.bookName })
    }
}
