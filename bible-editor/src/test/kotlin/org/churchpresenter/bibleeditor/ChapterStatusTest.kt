package org.churchpresenter.bibleeditor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibleedits.VerseAddress
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleeditor.EditorFixture.Companion.KJV_17
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MARK
import org.churchpresenter.bibleeditor.EditorFixture.Companion.MATTHEW
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChapterStatusTest {

    private val fixture = EditorFixture()

    @AfterTest
    fun delete() = fixture.delete()

    /** RST with a second gap, in Mark 1, so the search has somewhere to go round to. */
    private fun state(): BibleEditorState {
        SpbFixture.spbFile(fixture.bibles, "kjv.spb", EditorFixture.kjv().replace(
            "B041C001V001 41 1 1 The beginning of the gospel of Jesus Christ.",
            "B041C001V001 41 1 1 The beginning of the gospel of Jesus Christ.\nB041C001V002 41 1 2 As it is written.",
        ))
        val dispatcher = UnconfinedTestDispatcher()
        return BibleEditorState(fixture.bibles, listOf(fixture.rst), fixture.store, TestScope(dispatcher), dispatcher)
            .also { it.start() }
    }

    @Test
    fun `a chapter is complete, missing a verse, or restored`() {
        val state = state()
        assertEquals(ChapterStatus.COMPLETE, state.chapterStatus(1))
        assertEquals(ChapterStatus.MISSING, state.chapterStatus(17))
        assertTrue(state.bookHasMissing)
        state.history.commit(VerseAddress("rst.spb", MATTHEW, 17, 11), VerseChange(text = KJV_17[10], added = true),
            null)
        assertEquals(ChapterStatus.RESTORED, state.chapterStatus(17))
        assertFalse(state.bookHasMissing)
    }

    @Test
    fun `the next missing verse is after the open chapter, going round to the start`() {
        val state = state()
        assertEquals(MissingVerse(MATTHEW, 17, 11), state.nextMissing())
        state.goToChapter(17)
        assertEquals(MissingVerse(MARK, 1, 2), state.nextMissing())
        state.selectBook(1)
        assertEquals(MissingVerse(MATTHEW, 17, 11), state.nextMissing(), "nothing after Mark 1, so round to the start")
        state.goToNextMissing()
        assertEquals(0, state.bookIndex)
        assertEquals(17, state.chapter)
        assertEquals(11, state.flash)
    }

    @Test
    fun `with nothing open or nothing missing there is no next verse`() {
        val dispatcher = UnconfinedTestDispatcher()
        val unopened = BibleEditorState(fixture.bibles, emptyList(), fixture.store, TestScope(dispatcher), dispatcher)
        assertNull(unopened.nextMissing())
        unopened.goToNextMissing()
        assertNull(unopened.flash)
        val state = state()
        state.selectBible(state.shelf.first { it.key == "kjv.spb" })
        assertNull(state.nextMissing())
        state.goToNextMissing()
        assertNull(state.flash)
    }
}
