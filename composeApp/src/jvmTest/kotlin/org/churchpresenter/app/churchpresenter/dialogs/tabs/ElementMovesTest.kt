package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.songShiftKey
import androidx.compose.ui.unit.dp
import org.churchpresenter.presenter.movedOn
import org.churchpresenter.presenter.withReferenceShift
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
