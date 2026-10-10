@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StageLabelsAndListMovesTest {

    @Test
    fun `every stage layout, content type, zone and metronome spot has a label of its own`() {
        var layouts = emptyList<String>()
        var types = emptyList<String>()
        var zones = emptyList<String>()
        var spots = emptyList<String>()
        runComposeUiTest {
            setContent {
                layouts = StageMonitorLayout.entries.map { layoutLabel(it) }
                types = StageMonitorContentType.entries.map { contentTypeLabel(it) }
                zones = StageMonitorZone.entries.map { zoneLabel(it) }
                spots = MetronomePosition.entries.map { metronomePositionLabel(it) }
            }
            waitForIdle()
        }
        listOf(layouts, types, zones, spots).forEach { labels ->
            assertTrue(labels.all { it.isNotBlank() })
            assertEquals(labels.size, labels.distinct().size, labels.toString())
        }
    }

    @Test
    fun `swapping out of range leaves the list alone`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("c", "b", "a"), swapped(list, 0, 2))
        assertEquals(list, swapped(list, -1, 1))
        assertEquals(list, swapped(list, 1, 3))
    }

    @Test
    fun `moving to the same place or out of range is a no-op`() {
        val list = listOf(1, 2, 3, 4)
        assertEquals(listOf(2, 3, 1, 4), list.moved(0, 2))
        assertEquals(list, list.moved(1, 1))
        assertEquals(list, list.moved(-1, 2))
        assertEquals(list, list.moved(0, 4))
    }

    @Test
    fun `the song languages shown drop what the song lacks, and nothing stored means all`() {
        val on = OutputProfile(songMode = Constants.SONG_LANG_BOTH)
        assertEquals(emptyList(), shownSongPositions(on.copy(songMode = Constants.SONG_LANG_OFF), 3))
        assertEquals(listOf(0, 1, 2), shownSongPositions(on, 3))
        assertEquals(listOf(2, 0), shownSongPositions(on.copy(songTranslations = listOf(2, 5, 0, 2)), 3))
        assertEquals(listOf(0, 1), shownSongPositions(on.copy(songTranslations = listOf(7)), 2))
    }

    @Test
    fun `writing the song languages stores every slot in order as all, and none as off`() {
        val profile = OutputProfile(songMode = Constants.SONG_LANG_PRIMARY, songLookAhead = true)
        val off = withSongPositions(profile, emptyList(), 3)
        assertEquals(Constants.SONG_LANG_OFF, off.songMode)
        assertEquals(false, off.songLookAhead)
        assertEquals(emptyList(), withSongPositions(profile, listOf(0, 1, 2), 3).songTranslations)
        val some = withSongPositions(profile, listOf(1, 0), 3)
        assertEquals(listOf(1, 0), some.songTranslations)
        assertEquals(Constants.SONG_LANG_BOTH, some.songMode)
    }

    @Test
    fun `a setting's path reads as words, with the translation it belongs to`() {
        assertEquals("Text font size · KJV", settingPathLabel("bibleSettings.translations[kjv.spb].textFontSize"))
        assertEquals("Margin top", settingPathLabel("songSettings.marginTop"))
        assertEquals("Bold", settingPathLabel("bold"))
    }
}
