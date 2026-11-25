@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import org.churchpresenter.bibleeditor.ui.GateDispatcher
import org.churchpresenter.bibleeditor.ui.awaitText
import org.churchpresenter.bibleeditor.ui.click
import org.churchpresenter.bibleeditor.ui.draftField
import org.churchpresenter.bibleeditor.ui.press
import kotlin.test.Test
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import org.churchpresenter.bibleeditor.EditorFixture
import org.churchpresenter.bibleeditor.ui.goToChapter
import org.churchpresenter.bibleeditor.ui.isShowing

/**
 * The editor window: loading, the chapter in each of its states, the verse card, the footer.
 *
 * A verse's Edit, History and Remove buttons show only under the pointer, which `captureTo` clears
 * before every shot; the UI suites cover them instead.
 */
class BibleEditorScreenshotTest {

    private fun shoot(name: String, shot: Shot = Shot(), drive: ComposeUiTest.() -> Unit = {}) =
        shootEditor(SECTION, name, shot, drive)

    @Test
    fun `reading the Bible folder`() = shoot("loading_shelf", Shot(io = { GateDispatcher(allowed = 0) },
        openMatthew17 = false))

    @Test
    fun `an empty Bible folder`() = shoot("no_bibles", Shot(emptyShelf = true, openMatthew17 = false)) {
        awaitText("No Bible files found in", substring = true)
    }

    @Test
    fun `a Bible still being read`() = shoot("loading_bible", Shot(io = { GateDispatcher(allowed = 1) },
        openMatthew17 = false)) {
        awaitText("Russian Synodal")
    }

    @Test
    fun `a chapter with a missing verse and its history`() = shoot("chapter")

    @Test
    fun `a complete chapter with nothing changed and History closed`() =
        shoot("chapter_complete", Shot(history = emptyList(), openMatthew17 = false)) { press("Close history") }

    @Test
    fun `a chapter while the Bible's numbering is still being matched`() =
        shoot("chapter_checking", Shot(history = emptyList(), io = { GateDispatcher(allowed = 2) },
            openMatthew17 = false)) {
            awaitText("Checking…")
        }

    @Test
    fun `a KJV-numbered Bible`() = shoot("chapter_kjv", Shot(history = emptyList())) {
        click("Russian Synodal")
        click("King James Version")
    }

    @Test
    fun `a chapter only the Synodal numbering runs this long`() =
        shoot("chapter_synodal", Shot(history = emptyList(), openMatthew17 = false)) {
            click("Matthew")
            click("Romans")
            goToChapter(14)
        }

    @Test
    fun `a verse missing after the chapter's last one`() =
        shoot("gap_at_chapter_end", Shot(history = emptyList(), rst = EditorFixture.rstShortMark(),
            openMatthew17 = false)) {
            click("Matthew")
            click("Mark")
            // The title and the care note come before the verses: verse 45 is the list's 47th item.
            onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(46)
        }

    @Test
    fun `the care note hidden`() = shoot("note_hidden") { press("Hide until next time") }

    @Test
    fun `a verse opened with nothing changed yet`() = shoot("editing_unchanged", Shot(history = emptyList())) {
        press("Edit verse 2")
    }

    @Test
    fun `a typo being fixed`() = shoot("fixing_typo", Shot(history = emptyList())) {
        press("Edit verse 2")
        draftField().performTextReplacement(KJV_17[1])
    }

    @Test
    fun `a missing verse opened to type in`() = shoot("typing_missing_verse_empty") {
        click("Validate")
        click("Type the next one")
    }

    @Test
    fun `a missing verse being typed in`() = shoot("typing_missing_verse") {
        click("Validate")
        click("Type the next one")
        draftField().performTextReplacement(KJV_17[10])
    }

    @Test
    fun `verses added and removed, not saved yet`() = shoot("added_and_removed", Shot(history = emptyList())) {
        press("Remove verse")
        click("Validate")
        click("Type the next one")
        draftField().performTextReplacement(KJV_17[10])
        pressInView("Add verse 11")
        check(isShowing("ADDED")) { "verse 11 was not added" }
        press("Close history")
    }

    private companion object {
        const val SECTION = "bibleEditor"
    }
}
