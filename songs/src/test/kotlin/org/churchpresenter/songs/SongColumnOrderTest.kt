package org.churchpresenter.songs

import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongColumnOrderTest {

    private val cols = listOf("a", "b", "c", "d")

    @Test
    fun `one songbook with an add-action shows the action but no songbook column`() {
        val available = availableSongColumns(1, hasAddToSchedule = true)
        assertTrue(SongColumnId.ADD_TO_SCHEDULE in available)
        assertFalse(SongColumnId.SONGBOOK in available)
    }

    @Test
    fun `two songbooks without an add-action show the songbook but no action`() {
        val available = availableSongColumns(2, hasAddToSchedule = false)
        assertTrue(SongColumnId.SONGBOOK in available)
        assertFalse(SongColumnId.ADD_TO_SCHEDULE in available)
        assertEquals(SongColumnId.FAVORITES, available.last())
    }

    @Test
    fun `a saved order of only vanished columns falls back to what is available`() {
        assertEquals(cols, mergeColumnOrder(listOf("x", "y"), cols))
    }

    @Test
    fun `a column moves to the end`() {
        assertEquals(listOf("b", "c", "d", "a"), moveColumn(cols, "a", "d"))
    }

    @Test
    fun `a missing target leaves the order alone`() {
        assertTrue(moveColumn(cols, "a", "zz") === cols)
    }

    @Test
    fun `a large leftward drag clamps at the first column`() {
        assertEquals(0, draggedColumnIndex("d", -10_000f, cols, { 100f }, 4f))
    }

    @Test
    fun `a drag of exactly half a column moves it`() {
        assertEquals(2, draggedColumnIndex("b", 52f, cols, { 100f }, 4f))
    }

    @Test
    fun `a drag left from the first column stays put`() {
        assertEquals(0, draggedColumnIndex("a", -500f, cols, { 100f }, 4f))
    }

    @Test
    fun `a drag right from the last column stays put`() {
        assertEquals(3, draggedColumnIndex("d", 500f, cols, { 100f }, 4f))
    }

    @Test
    fun `columns of different widths are crossed one at a time`() {
        val widths = mapOf("b" to 20f, "c" to 400f)
        assertEquals(1, draggedColumnIndex("a", 60f, cols, { widths[it] ?: 100f }, 0f))
    }

    @Test
    fun `every surface in line mode is line mode`() {
        val line = Constants.SONG_DISPLAY_MODE_LINE
        assertTrue(
            isSongLineMode(
                SongSettings(
                    fullscreenDisplayMode = line,
                    lowerThirdDisplayMode = line,
                    lookAheadDisplayMode = line,
                    lowerThirdLookAheadDisplayMode = line,
                ),
            ),
        )
    }

    @Test
    fun `a mode the app does not know reads as line mode`() {
        val verse = Constants.SONG_DISPLAY_MODE_VERSE
        assertTrue(
            isSongLineMode(
                SongSettings(
                    fullscreenDisplayMode = "unknown",
                    lowerThirdDisplayMode = verse,
                    lookAheadDisplayMode = verse,
                    lowerThirdLookAheadDisplayMode = verse,
                ),
            ),
        )
    }
}
