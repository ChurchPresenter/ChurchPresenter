@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the lyrics panel makes of a song's structure: its section chips, its chords, and the optional
 * title slide that can lead it.
 */
class SongsTabChartAndTitleSlideTest {

    /** Upper-cased on screen: the title slide wears the same section chip a verse does. */
    private val TITLE_SLIDE = "SONG TITLE SLIDE"

    private val chordedSong = listOf(
        SongFixture(
            number = "1",
            title = "Amazing Grace",
            lyrics = listOf("[Intro]", "[Cm] [Bb]", "[Verse 1]", "[G]one two", "{Chorus}", "[C]three four"),
        ),
    )

    // ── Section chips ───────────────────────────────────────────────────────────

    @Test
    fun `every section is named by its own chip`() = songsTab(songs = chordedSong) { _, _ ->
        assertTrue(showsContaining("VERSE 1"), rendered().toString())
        assertTrue(showsContaining("CHORUS"), rendered().toString())
    }

    @Test
    fun `a chords-only intro is not listed as a section of its own`() =
        songsTab(songs = chordedSong) { vm, _ ->
            assertFalse(showsContaining("INTRO"), rendered().toString())
            assertEquals(listOf("[Verse 1]", "{Chorus}"), vm.getLyricSections().map { it.header })
        }

    @Test
    fun `the words listed carry no chord markers`() = songsTab(songs = chordedSong) { _, _ ->
        assertTrue(showsContaining("one two"), rendered().toString())
        assertFalse(showsContaining("[G]one"), rendered().toString())
    }

    // ── The title slide ─────────────────────────────────────────────────────────

    @Test
    fun `the title slide is offered when it is turned on`() =
        songsTab(songs = chordedSong, songSettings = SongSettings(titleSlideEnabled = true)) { _, _ ->
            assertTrue(showsContaining(TITLE_SLIDE), rendered().toString())
        }

    @Test
    fun `the title slide is absent when it is turned off`() =
        songsTab(songs = chordedSong, songSettings = SongSettings(titleSlideEnabled = false)) { _, _ ->
            assertFalse(showsContaining(TITLE_SLIDE), rendered().toString())
        }

    @Test
    fun `choosing the title slide sends it out ahead of the song`() =
        songsTab(songs = chordedSong, songSettings = SongSettings(titleSlideEnabled = true)) { _, reports ->
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()

            val sent = reports.selectedSection
            assertNotNull(sent)
            assertEquals("title_slide", sent.type)
            assertEquals(0, reports.sectionIndex, "it is prepended, so it is the first of what goes out")
        }

    @Test
    fun `the title slide's own sections lead the song's`() =
        songsTab(songs = chordedSong, songSettings = SongSettings(titleSlideEnabled = true)) { _, reports ->
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()

            val pushed = reports.allSections.last()
            assertEquals("title_slide", pushed.first().type)
            assertTrue(pushed.size > 1, "the song's own sections follow it")
        }

    @Test
    fun `going live from the title slide reports the song as presenting`() =
        songsTab(
            songs = chordedSong,
            songSettings = SongSettings(titleSlideEnabled = true),
            isPresenting = true,
        ) { _, reports ->
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()

            assertNotNull(reports.selectedSection)
        }

    // ── Tempo and capo reach the section that goes out ──────────────────────────

    @Test
    fun `the tempo set against a song is stamped on what goes live`() {
        var songId = ""
        songsTab(songs = chordedSong) { vm, _ -> songId = vm.filteredSongItems.value[0].songId }

        songsTab(songs = chordedSong, songBpm = mapOf(songId to 96), stageMonitor = true) { _, reports ->
            onNodeWithText("one two").performClick()
            waitForIdle()

            assertEquals(96, reports.selectedSection?.bpm)
        }
    }

    @Test
    fun `a song with no tempo goes out with the metronome off`() =
        songsTab(songs = chordedSong, stageMonitor = true) { _, reports ->
            onNodeWithText("one two").performClick()
            waitForIdle()

            assertEquals(0, reports.selectedSection?.bpm, "0 is off")
        }

    @Test
    fun `the section that goes live carries its chart for the band`() =
        songsTab(songs = chordedSong) { _, reports ->
            onNodeWithText("one two").performClick()
            waitForIdle()

            assertEquals(
                listOf("[Intro]", "[Cm] [Bb]", "[G]one two"),
                reports.selectedSection?.chordLines,
                "the folded intro leads the verse's chart",
            )
        }
}
