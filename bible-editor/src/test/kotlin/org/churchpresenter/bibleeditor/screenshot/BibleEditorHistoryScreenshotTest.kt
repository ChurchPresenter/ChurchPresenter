@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.screenshot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.ui.click
import org.churchpresenter.bibleeditor.ui.press
import kotlin.test.Test
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex

/** The History panel: empty, every kind of change, one verse, and its three scopes. */
class BibleEditorHistoryScreenshotTest {

    private fun shoot(name: String, shot: Shot = Shot(history = EditorFixture.everyKind()),
        drive: ComposeUiTest.() -> Unit = {}) =
        shootEditor(SECTION, name, shot, drive)

    @Test
    fun `no changes yet`() = shoot("empty", Shot(history = emptyList()))

    @Test
    fun `every kind of change, saved`() = shoot("every_kind")

    @Test
    fun `a change reverted and not saved yet`() = shoot("reverted_unsaved", Shot()) {
        onAllNodesWithText("Revert")[0].performClick()
        waitForIdle()
    }

    @Test
    fun `one verse's history`() = shoot("one_verse", Shot()) {
        // Below the care note at this height: the title and the note come first, verse 9 is the 11th item.
        onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(10)
        press("History of verse 9")
    }

    @Test
    fun `this Bible`() = shoot("this_bible") { click("This Bible") }

    @Test
    fun `every Bible`() = shoot("all_bibles") { click("All Bibles") }

    private companion object {
        const val SECTION = "bibleEditorHistory"
    }
}
