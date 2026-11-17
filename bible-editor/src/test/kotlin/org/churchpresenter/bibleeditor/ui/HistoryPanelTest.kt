@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.bibleeditor.EditorFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryPanelTest {

    @Test
    fun `a chapter with no changes says so`() = withEditor(openMatthew17 = false) { _, _ ->
        assertTrue(isShowing("No changes here yet"))
        assertTrue(isShowing("Each edit, added verse and removal shows up here with its time, and can be reverted."))
    }

    @Test
    fun `saved changes are grouped by day, newest first`() = withEditor(EditorFixture.savedFixes()) { _, _ ->
        assertTrue(isShowing("TODAY"))
        assertTrue(isShowing("YESTERDAY"))
        assertTrue(isShowing("Matthew 17:9"))
        assertTrue(isShowing("Matthew 17:2"))
        assertEquals(2, onAllNodesWithText("Revert").fetchSemanticsNodes().size)
    }

    @Test
    fun `reverting a change puts the verse back and lists the revert, unsaved until saved`() =
        withEditor(EditorFixture.savedFixes()) { _, _ ->
            onAllNodesWithText("Revert")[0].performClick()
            waitForIdle()
            assertTrue(isShowing("REVERTED"))
            assertTrue(isShowing("Not saved yet"))
            assertTrue(isShowing(EditorFixture.RST_TYPO_9))
            onAllNodesWithText("Already reverted")[0].assertIsNotEnabled()
            click("Save 1 change")
            assertTrue(isShowing("Undid the change from", substring = true))
        }

    @Test
    fun `the scopes widen the list to the Bible and to every Bible`() = withEditor(EditorFixture.savedFixes()) { _, _ ->
        goToChapter(1)
        assertTrue(isShowing("No changes here yet"))
        click("This Bible")
        assertTrue(isShowing("Matthew 17:9"))
        click("All Bibles")
        assertTrue(isShowing("Matthew 17:9 · RS"))
        click("This chapter")
        assertTrue(isShowing("No changes here yet"))
    }

    @Test
    fun `a change's reference goes to its verse`() = withEditor(EditorFixture.savedFixes()) { _, _ ->
        goToChapter(1)
        click("This Bible")
        click("Matthew 17:2")
        chapterField().assert(hasText("17"))
        assertTrue(isShowing(EditorFixture.KJV_17[1]), "verse 2 is in view, as it was fixed")
    }
}
