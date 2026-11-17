package org.churchpresenter.bibleeditor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseAddress
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MATTHEW
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BibleEditorStateEdgeTest {

    private val fixture = EditorFixture()

    @AfterTest
    fun delete() = fixture.delete()

    /** Opened on RST, with every read finished inline. */
    private fun started(): BibleEditorState {
        val dispatcher = UnconfinedTestDispatcher()
        return BibleEditorState(fixture.bibles, listOf(fixture.rst), fixture.store, TestScope(dispatcher), dispatcher)
            .also { it.start() }
    }

    private fun unstarted(): BibleEditorState {
        val dispatcher = UnconfinedTestDispatcher()
        return BibleEditorState(fixture.bibles, emptyList(), fixture.store, TestScope(dispatcher), dispatcher)
    }

    @Test
    fun `before a Bible is open every question has an empty answer`() {
        val state = unstarted()
        assertEquals(emptyList(), state.chapterRows())
        assertEquals(emptySet(), state.missingInChapter())
        assertFalse(state.isRestored(MissingVerse(MATTHEW, 17, 11)))
        assertNull(state.address(1))
        assertNull(state.missingHere)
        assertEquals("", state.bookName)
        assertEquals(1, state.bookId)
        assertEquals(1, state.chapterCount)
        assertEquals("40", state.bookNameOf(MATTHEW))
        assertEquals("40", state.bookNameIn("rst.spb", MATTHEW))
        state.draft = "Something."
        state.saveDraft(ChapterRow.Gap(1))
        state.toggleRemoved(ChapterRow.Verse(1, "t", "t", null, null))
        assertEquals(emptyList(), state.history.entries)
        assertEquals(emptyList(), state.visibleHistory())
        state.goTo("rst.spb", MATTHEW, 17, null)
        assertEquals(MATTHEW - 1, state.bookIndex)
    }

    @Test
    fun `only spb files go on the shelf, from folders inside the Bible folder too`() {
        File(fixture.bibles, "notes.txt").writeText("not a Bible")
        val nested = File(fixture.bibles, "more").also { it.mkdirs() }
        SpbFixture.spbFile(nested, "web.SPB", SpbFixture.sampleContent("World English Bible"))
        val shelf = scanShelf(fixture.bibles)
        assertEquals(listOf("kjv.spb", "more/web.SPB", "rst.spb"), shelf.map { it.key.replace('\\', '/') }.sorted())
        assertEquals(emptyList(), scanShelf(File(fixture.root, "missing")))
    }

    @Test
    fun `a KJV is found by its file name when its title does not abbreviate to KJV`() {
        val bare = EditorFixture(withReference = false)
        try {
            SpbFixture.spbFile(bare.bibles, "kjv1769.spb", EditorFixture.kjv().replace("King James Version",
                "Authorized"))
            val dispatcher = UnconfinedTestDispatcher()
            val state = BibleEditorState(bare.bibles, listOf(bare.rst), bare.store, TestScope(dispatcher), dispatcher)
            state.start()
            assertEquals("kjv1769.spb", state.reference?.key)
            assertEquals(listOf(MissingVerse(MATTHEW, 17, 11)), state.missingHere)
        } finally {
            bare.delete()
        }
    }

    @Test
    fun `the reference is read once, however many Bibles are checked against it`() {
        SpbFixture.spbFile(fixture.bibles, "ukr.spb", EditorFixture.rst().replace("Russian Synodal", "Ukrainian"))
        val state = started()
        state.selectBible(state.shelf.first { it.key == "ukr.spb" })
        assertEquals(1, state.missing["ukr.spb"]?.size)
        state.selectBible(state.shelf.first { it.key == "rst.spb" })
        state.selectBible(state.shelf.first { it.key == "rst.spb" })
        assertEquals("rst.spb", state.selected?.key)
    }

    @Test
    fun `a Bible picked while another is still loading wins`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val state = BibleEditorState(fixture.bibles, emptyList(), fixture.store, scope, dispatcher)
        state.start()
        scope.advanceUntilIdle()
        val (kjv, rst) = state.shelf
        state.selectBible(rst)
        state.selectBible(kjv)
        scope.advanceUntilIdle()
        assertEquals(kjv, state.selected)
        assertEquals("King James Version", state.bible?.getBibleTitle())
    }

    @Test
    fun `a save with no one listening still reaches the disk`() {
        val state = started()
        state.history.commit(VerseAddress("rst.spb", MATTHEW, 17, 3), VerseChange(removed = true), null)
        state.save()
        assertEquals(1, fixture.store.load().size)
    }

    @Test
    fun `a second flash replaces the first`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val state = BibleEditorState(fixture.bibles, emptyList(), fixture.store, scope, dispatcher)
        state.flashVerse(2)
        state.flashVerse(3)
        assertEquals(3, state.flash)
        scope.advanceUntilIdle()
        assertNull(state.flash)
    }

    @Test
    fun `changes elsewhere stay out of the open chapter`() {
        val state = started()
        state.goToChapter(17)
        val added = VerseChange(text = "Elsewhere.", added = true)
        listOf(
            VerseAddress("kjv.spb", MATTHEW, 17, 30),
            VerseAddress("rst.spb", MATTHEW + 1, 17, 31),
            VerseAddress("rst.spb", MATTHEW, 16, 32),
        ).forEach { state.history.commit(it, added, null) }
        assertEquals(12, state.chapterRows().size)
        assertEquals(emptyList(), state.visibleHistory())
        state.history.commit(VerseAddress("rst.spb", MATTHEW, 17, 11), VerseChange(added = true), null)
        assertFalse(state.isRestored(MissingVerse(MATTHEW, 17, 11)), "added with no text is not restored")
        state.history.commit(VerseAddress("rst.spb", MATTHEW, 17, 11), VerseChange(text = "  ", added = true), null)
        assertFalse(state.isRestored(MissingVerse(MATTHEW, 17, 11)))
        state.history.commit(VerseAddress("rst.spb", MATTHEW, 17, 3), VerseChange(text = "Edited."), null)
        assertFalse(state.isRestored(MissingVerse(MATTHEW, 17, 3)), "an edit is not a restore")
    }

    @Test
    fun `what a row says about its change`() {
        fun row(change: VerseChange?) = ChapterRow.Verse(1, "t", "t", change, null)
        assertFalse(row(null).edited)
        assertTrue(row(VerseChange(text = "x")).edited)
        assertFalse(row(VerseChange(text = "x", added = true)).edited)
        assertFalse(row(VerseChange(text = "x", removed = true)).edited)
        assertFalse(row(VerseChange()).edited)
        assertEquals("added", textOf(VerseChange(text = "added", added = true), null))
    }

    @Test
    fun `the history for one verse leaves the others out`() {
        fixture.saveHistory(EditorFixture.savedFixes() + EditEntry(3, 0, "rst.spb", MATTHEW, 16, 2,
            after = VerseChange(text = "x")))
        val state = started()
        state.goToChapter(17)
        state.historyVerse = 9
        assertEquals(listOf(2L), state.visibleHistory().map { it.id })
    }
}
