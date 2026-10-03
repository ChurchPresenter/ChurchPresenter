package org.churchpresenter.songs

import androidx.compose.ui.unit.Density
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.SongColumnId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongsTabStateCoverageTest {

    private val song = SongItem(number = "1", title = "Grace", songbook = "Hymnal")

    @Test
    fun `no dialog is asked for at first`() {
        val dialogs = SongDialogRequests()
        assertNull(dialogs.editing)
        assertNull(dialogs.deleting)
        assertFalse(dialogs.creatingNew)
    }

    @Test
    fun `editing a song opens and closes the editor`() {
        val dialogs = SongDialogRequests()
        dialogs.edit(song)
        assertEquals(song, dialogs.editing)
        dialogs.closeEditor()
        assertNull(dialogs.editing)
    }

    @Test
    fun `deleting a song asks and can be dismissed`() {
        val dialogs = SongDialogRequests()
        dialogs.delete(song)
        assertEquals(song, dialogs.deleting)
        dialogs.closeDelete()
        assertNull(dialogs.deleting)
    }

    @Test
    fun `a new song opens and closes its own editor`() {
        val dialogs = SongDialogRequests()
        dialogs.createNew()
        assertTrue(dialogs.creatingNew)
        dialogs.closeNew()
        assertFalse(dialogs.creatingNew)
    }

    @Test
    fun `nothing is live at first`() {
        val live = SongLiveState()
        assertNull(live.songId)
        assertEquals(0, live.sectionIndex)
        assertEquals(0, live.lineIndex)
        assertFalse(live.titleSlideSelected)
        assertNull(live.wentLiveSongId)
    }

    @Test
    fun `going live records the song, section and line but not the counted song`() {
        val live = SongLiveState()
        live.live("Hymnal::1", 2, 3)
        assertEquals("Hymnal::1", live.songId)
        assertEquals(2, live.sectionIndex)
        assertEquals(3, live.lineIndex)
        assertNull(live.wentLiveSongId)
    }

    private fun columns(density: Float = 1f) = SongTableColumns(
        density = Density(density),
        initialWidths = mapOf(SongColumnId.TITLE to 200f),
        initialOrder = listOf(SongColumnId.NUMBER, SongColumnId.TITLE, SongColumnId.AUTHOR),
        initialHidden = setOf(SongColumnId.AUTHOR),
    )

    @Test
    fun `a stored width is the column's width`() {
        assertEquals(200f, columns().widthOf(SongColumnId.TITLE))
    }

    @Test
    fun `a column with no stored width is an action column's width`() {
        assertEquals(30f, columns().widthOf(SongColumnId.FAVORITES))
    }

    @Test
    fun `a width is never set below the column's floor`() {
        val table = columns()
        table.setWidth(SongColumnId.TITLE, 10f)
        assertEquals(60f, table.widthOf(SongColumnId.TITLE))
    }

    @Test
    fun `an action column's width cannot be set`() {
        val table = columns()
        table.setWidth(SongColumnId.ADD_TO_SCHEDULE, 500f)
        assertEquals(30f, table.widthOf(SongColumnId.ADD_TO_SCHEDULE))
        assertFalse(SongColumnId.ADD_TO_SCHEDULE in table.widthsInDp())
    }

    @Test
    fun `hidden columns are not visible`() {
        assertEquals(listOf(SongColumnId.NUMBER, SongColumnId.TITLE), columns().visible)
    }

    @Test
    fun `widths are written back as whole dp`() {
        assertEquals(mapOf(SongColumnId.TITLE to 100), columns(density = 2f).widthsInDp())
    }

    @Test
    fun `an editor request offers sensible defaults`() {
        val request = SongEditorRequest(
            isVisible = true, song = song, songbooks = listOf("Hymnal"), existingSongs = emptyList(),
            onChordsVisibleChange = {}, languageNames = emptyList(), onLanguageNamesChange = {},
            onDismiss = {}, onSave = { _, _ -> },
        )
        assertFalse(request.isNewSong)
        assertEquals(SongTuning(), request.tuning)
        assertTrue(request.chordsVisible)
        assertNull(request.typicalSeconds)
        assertNull(request.onApplyBackgroundToSongbook)
        assertEquals(request, request.copy())
    }

    @Test
    fun `play counts can be given as a lambda`() {
        val counts = SongPlayCounts { id -> if (id == "a") 4 else 0 }
        assertEquals(4, counts.getSongPlayCount("a"))
        assertEquals(0, counts.getSongPlayCount("b"))
    }
}
