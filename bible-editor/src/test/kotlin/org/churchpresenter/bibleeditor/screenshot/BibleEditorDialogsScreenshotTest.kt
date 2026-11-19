@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.bibleeditor.screenshot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import org.churchpresenter.bibleeditor.ui.GateDispatcher
import org.churchpresenter.bibleeditor.ui.click
import org.churchpresenter.bibleeditor.ui.draftField
import org.churchpresenter.bibleeditor.ui.openChapterPicker
import kotlin.test.Test

/** The missing verses dialog in each of its states, and the Bible, book and chapter pickers. */
class BibleEditorDialogsScreenshotTest {

    private fun shoot(name: String, shot: Shot = Shot(), drive: ComposeUiTest.() -> Unit = {}) =
        shootEditor(SECTION, name, shot, drive)

    private fun ComposeUiTest.restoreVerse11() {
        click("Validate")
        click("Type the next one")
        draftField().performTextReplacement(KJV_17[10])
        click("Add verse 11")
    }

    @Test
    fun `missing verses`() = shoot("missing") { click("Validate") }

    @Test
    fun `missing verses, nothing restored yet`() = shoot("missing_restored_empty") {
        click("Validate")
        click("Restored (0)")
    }

    @Test
    fun `missing verses, all of them`() = shoot("missing_all") {
        click("Validate")
        click("All (1)")
    }

    @Test
    fun `missing verses, every one restored`() = shoot("missing_all_restored") {
        restoreVerse11()
        click("Validate")
    }

    @Test
    fun `missing verses, the restored list`() = shoot("missing_restored") {
        restoreVerse11()
        click("Validate")
        click("Restored (1)")
    }

    @Test
    fun `missing verses, still checking`() = shoot("missing_checking", Shot(io = { GateDispatcher(allowed = 2) })) {
        click("Validate")
    }

    @Test
    fun `missing verses, with no KJV to compare with`() = shoot("missing_no_reference", Shot(withReference = false)) {
        click("Validate")
    }

    @Test
    fun `missing verses, the KJV itself`() = shoot("missing_none") {
        click("Russian Synodal")
        click("King James Version")
        click("Validate")
    }

    @Test
    fun `the Bible picker`() = shoot("bible_picker", Shot(rootIndex = 1)) { click("Russian Synodal") }

    @Test
    fun `the book picker`() = shoot("book_picker", Shot(rootIndex = 1)) { click("Matthew") }

    @Test
    fun `the chapter picker`() = shoot("chapter_picker", Shot(rootIndex = 1)) { openChapterPicker() }

    @Test
    fun `the chapter picker in a book with nothing missing`() = shoot("chapter_picker_complete_book",
        Shot(rootIndex = 1)) {
        click("Matthew")
        click("Mark")
        openChapterPicker()
    }

    @Test
    fun `the chapter picker with nothing left missing`() = shoot("chapter_picker_nothing_missing",
        Shot(rootIndex = 1)) {
        restoreVerse11()
        openChapterPicker()
    }

    private companion object {
        const val SECTION = "bibleEditorDialogs"
    }
}
