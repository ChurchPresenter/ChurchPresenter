package org.churchpresenter.bibleeditor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.churchpresenter.bibleedits.BibleEditsStore
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MATTHEW
import org.churchpresenter.bibleeditor.EditorFixture.Companion.NOW
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BibleEditorStateTest {

    private val fixture = EditorFixture()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    private var saves = 0

    @AfterTest
    fun delete() = fixture.delete()

    private fun state(preferred: List<File> = listOf(fixture.rst), folder: File = fixture.bibles) =
        BibleEditorState(folder, preferred, fixture.store, scope, dispatcher, onSaved = { saves++ }, clock = { NOW })
            .also { it.start() }

    private fun BibleEditorState.openMatthew17() {
        selectBook(0)
        goToChapter(17)
    }

    @Test
    fun `it opens the preferred Bible and finds what it lacks in the numbering it matches`() {
        val state = state()
        assertEquals(listOf("King James Version", "Russian Synodal"), state.shelf.map { it.title })
        assertEquals("rst.spb", state.selected?.key)
        assertEquals(Numbering.SYNODAL, state.numberingHere)
        assertEquals(listOf(MissingVerse(MATTHEW, 17, 11)), state.missing["rst.spb"])
        state.selectBible(state.shelf.first())
        assertEquals(Numbering.KJV, state.numberingHere)
        assertEquals(emptyList(), state.missing["kjv.spb"])
    }

    @Test
    fun `with nothing preferred it opens the first Bible, and an empty folder opens none`() {
        assertEquals("kjv.spb", state(preferred = emptyList()).selected?.key)
        val empty = state(folder = File(fixture.root, "none"))
        assertEquals(emptyList(), empty.shelf)
        assertNull(empty.selected)
    }

    @Test
    fun `no KJV is needed on the shelf to check a Bible`() {
        fixture.kjv.delete()
        val state = state()
        assertEquals(listOf("rst.spb"), state.shelf.map { it.key })
        assertEquals(listOf(MissingVerse(MATTHEW, 17, 11)), state.missingHere)
    }

    @Test
    fun `the chapter reads verse by verse with a gap where one is missing`() {
        val state = state()
        state.openMatthew17()
        val rows = state.chapterRows()
        assertEquals(27, rows.size)
        assertIs<ChapterRow.Gap>(rows[10])
        assertEquals(EditorFixture.RST_TYPO_2, (rows[1] as ChapterRow.Verse).text)
        assertEquals(setOf(11), state.missingInChapter())
    }

    @Test
    fun `typing a missing verse adds it, and saving reaches the disk`() {
        val state = state()
        state.openMatthew17()
        state.addingAt = 11
        state.draft = "  ${KJV_17[10]}  "
        state.saveDraft(state.chapterRows()[10])
        val row = state.chapterRows()[10] as ChapterRow.Verse
        assertTrue(row.added)
        assertTrue(state.isRestored(MissingVerse(MATTHEW, 17, 11)))
        state.save()
        scope.advanceUntilIdle()
        assertEquals(1, saves)
        assertEquals(state.history.entries, BibleEditsStore(fixture.data).load())
    }

    @Test
    fun `a blank draft saves nothing, and typing the file's text back clears the edit`() {
        val state = state()
        state.openMatthew17()
        val verse2 = state.chapterRows()[1]
        state.draft = "   "
        state.saveDraft(verse2)
        assertEquals(0, state.history.entries.size)
        state.draft = KJV_17[1]
        state.saveDraft(verse2)
        assertTrue((state.chapterRows()[1] as ChapterRow.Verse).edited)
        state.draft = EditorFixture.RST_TYPO_2
        state.saveDraft(state.chapterRows()[1])
        assertNull((state.chapterRows()[1] as ChapterRow.Verse).change)
    }

    @Test
    fun `removing, restoring and taking out an added verse`() {
        val state = state()
        state.openMatthew17()
        fun row(n: Int) = state.chapterRows()[n - 1] as ChapterRow.Verse
        state.toggleRemoved(row(3))
        assertTrue(row(3).removed)
        state.toggleRemoved(row(3))
        assertNull(row(3).change)
        state.draft = "Edited."
        state.saveDraft(row(4))
        state.toggleRemoved(row(4))
        state.toggleRemoved(row(4))
        assertEquals(VerseChange(text = "Edited."), row(4).change)
        state.addingAt = 11
        state.draft = "Added."
        state.saveDraft(state.chapterRows()[10])
        state.draft = "Added again."
        state.saveDraft(row(11))
        assertEquals(VerseChange(text = "Added again.", added = true), row(11).change)
        state.toggleRemoved(row(11))
        assertIs<ChapterRow.Gap>(state.chapterRows()[10])
        // A verse added and then marked removed (as an older history can hold) comes back as added.
        state.history.commit(state.address(11)!!, VerseChange(text = "Added.", added = true, removed = true), null)
        assertTrue(row(11).removed)
        state.toggleRemoved(row(11))
        assertEquals(VerseChange(text = "Added.", added = true), row(11).change)
    }

    @Test
    fun `the history panel lists the chapter, one verse, the Bible or every Bible, newest first`() {
        fixture.saveHistory(EditorFixture.savedFixes())
        val state = state()
        state.openMatthew17()
        assertEquals(listOf(2L, 1L), state.visibleHistory().map { it.id })
        state.historyVerse = 2
        assertEquals(listOf(1L), state.visibleHistory().map { it.id })
        state.historyScope = HistoryScope.BIBLE
        state.goToChapter(1)
        assertEquals(2, state.visibleHistory().size)
        state.selectBible(state.shelf.first())
        assertEquals(0, state.visibleHistory().size)
        state.historyScope = HistoryScope.ALL
        assertEquals(2, state.visibleHistory().size)
    }

    @Test
    fun `going to a verse opens its Bible and chapter and lights it up for a moment`() {
        val state = state(preferred = emptyList())
        state.goTo("rst.spb", MATTHEW, 17, 9)
        assertEquals("rst.spb", state.selected?.key)
        assertEquals(17, state.chapter)
        assertEquals(9, state.flash)
        scope.advanceUntilIdle()
        assertNull(state.flash)
        state.goTo("missing.spb", MATTHEW, 1, null)
        assertEquals(1, state.chapter)
    }

    @Test
    fun `chapters are kept inside the book`() {
        val state = state()
        state.goToChapter(99)
        assertEquals(17, state.chapter)
        state.goToChapter(0)
        assertEquals(1, state.chapter)
        assertEquals(17, state.chapterCount)
    }

    @Test
    fun `what a change makes a verse read`() {
        assertEquals("", textOf(VerseChange(removed = true), "file"))
        assertEquals("new", textOf(VerseChange(text = "new"), "file"))
        assertEquals("", textOf(VerseChange(added = true), "file"))
        assertEquals("file", textOf(null, "file"))
        assertEquals("", textOf(null, null))
    }
}
