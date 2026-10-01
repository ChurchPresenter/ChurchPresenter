package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.elementMove
import org.churchpresenter.app.churchpresenter.presenter.movedOn
import org.churchpresenter.app.churchpresenter.presenter.referenceShiftFor
import org.churchpresenter.app.churchpresenter.presenter.withMovesCleared
import org.churchpresenter.app.churchpresenter.presenter.withReferenceShift
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Moving elements on their own: where a move is kept, what an output adds up, and taking moves back. */
class ElementMovesTest {

    @Test
    fun `a song move is kept per element, per output, and per language where there is one`() {
        assertEquals("LYRICS#1", songShiftKey(SongStyleElement.LYRICS, lowerThird = false, language = 1))
        assertEquals("LYRICS@LT", songShiftKey(SongStyleElement.LYRICS, lowerThird = true))
        // The number is the same digits in every language, so it has no language's own move.
        assertEquals("NUMBER", songShiftKey(SongStyleElement.NUMBER, lowerThird = false, language = 1))
        // The title slide's title is its own, so moving it leaves the title above every verse.
        assertEquals(
            "TITLE_SLIDE_TITLE#0",
            songShiftKey(SongStyleElement.TITLE, lowerThird = false, language = 0, titleSlide = true),
        )
    }

    @Test
    fun `a language's lines move by the element's move and their own together`() {
        val song = SongSettings()
            .shiftedAt(songShiftKey(SongStyleElement.LYRICS, false), 10, 5)
            .shiftedAt(songShiftKey(SongStyleElement.LYRICS, false, 1), 3, -2)
        assertEquals(13 to 3, song.elementMove(SongStyleElement.LYRICS, lowerThird = false, language = 1))
        assertEquals(10 to 5, song.elementMove(SongStyleElement.LYRICS, lowerThird = false, language = 0))
        assertEquals(0 to 0, song.elementMove(SongStyleElement.LYRICS, lowerThird = true, language = 1))
    }

    @Test
    fun `moving back to nothing drops the move`() {
        val key = songShiftKey(SongStyleElement.TITLE, false)
        val song = SongSettings().shiftedAt(key, 4, 4).shiftedAt(key, 0, 0)
        assertEquals(emptyMap(), song.layoutExtras.elementShifts)
        assertEquals(0 to 0, song.shiftAt(key))
    }

    @Test
    fun `Reset positions on the Songs page takes back only this output's moves`() {
        val song = SongSettings()
            .shiftedAt(songShiftKey(SongStyleElement.LYRICS, false, 1), 5, 5)
            .shiftedAt(songShiftKey(SongStyleElement.NUMBER, true), 7, 7)
        var draft = AppSettings(songSettings = song)
        val profile = OutputProfile(songMode = Constants.SONG_LANG_BOTH)
        val targets = SongTargets(
            element = Adjustable(CustomizeElement.SONG_LYRICS) {},
            slideElement = Adjustable(SongStyleElement.TITLE) {},
            language = Adjustable(null) {},
        )
        val model = songAdjustModel(draft, profile, targets) { t -> draft = t(draft) }
        val positions = model.positions!!
        assertTrue(positions.moved)
        positions.onReset()
        assertEquals(setOf("NUMBER@LT"), draft.songSettings.layoutExtras.elementShifts.keys)
    }

    @Test
    fun `a reference is moved on its own, per output, and taken back with the block's move`() {
        val moved = BibleTranslationSettings(fileName = "kjv.spb", shiftX = 5)
            .withReferenceShift(lowerThird = false, x = 81, y = 250)
        assertEquals(81 to 250, moved.referenceShiftFor(lowerThird = false))
        assertEquals(0 to 0, moved.referenceShiftFor(lowerThird = true))
        assertTrue(moved.movedOn(lowerThird = false))
        assertFalse(moved.movedOn(lowerThird = true))
        val cleared = moved.withMovesCleared(lowerThird = false)
        assertFalse(cleared.movedOn(lowerThird = false))
        assertEquals(0, cleared.shiftX)
        val lt = BibleTranslationSettings(lowerThirdShiftY = 3).withReferenceShift(lowerThird = true, x = 1, y = 2)
        assertTrue(lt.movedOn(lowerThird = true))
        assertFalse(lt.withMovesCleared(lowerThird = true).movedOn(lowerThird = true))
    }

    @Test
    fun `Reset positions on the Bible page clears every translation and the All layer`() {
        val moved = BibleTranslationSettings(fileName = "kjv.spb").withReferenceShift(false, 0, 250)
        var draft = AppSettings(
            bibleSettings = BibleSettings(translations = listOf(moved), allTranslationStyle = moved),
        )
        val model = bibleAdjustModel(
            draft,
            OutputProfile(),
            Adjustable(CustomizeElement.BIBLE_TEXT) {},
            { t -> draft = t(draft) },
            Adjustable(ALL_TRANSLATIONS) {},
        )
        assertTrue(model.positions!!.moved)
        model.positions.onReset()
        assertFalse(draft.bibleSettings.translations.single().movedOn(false))
        assertFalse(draft.bibleSettings.allTranslationStyle!!.movedOn(false))
    }

    @Test
    fun `a dragged block's room stops at the frame it must stay in, and never goes below nothing`() {
        val bounds = AdjustFrame(0.dp, 0.dp, 100.dp, 100.dp)
        val room = DragRoom.within(AdjustFrame(10.dp, 20.dp, 60.dp, 50.dp), bounds, scale = 0.5f)
        assertEquals(DragRoom(-20f, 80f, -40f, 100f), room)
        // Already filling the frame across, it has no room sideways at all.
        val wide = DragRoom.within(AdjustFrame(0.dp, 20.dp, 100.dp, 50.dp), bounds, scale = 1f)
        assertEquals(0f, wide.left)
        assertEquals(0f, wide.right)
    }
}
