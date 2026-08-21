package org.churchpresenter.app.churchpresenter.data.settings

import org.churchpresenter.app.churchpresenter.models.songs.SongTuning
import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTuningTest {

    private val songId = "Hymnal::0001"

    @Test
    fun `a song nobody has tuned reads as untuned`() {
        assertEquals(SongTuning(bpm = 0, capo = 0), AppSettings().tuningFor(songId))
    }

    @Test
    fun `a stored tempo and capo are read back together`() {
        val settings = AppSettings(songBpm = mapOf(songId to 72), songCapo = mapOf(songId to 2))

        assertEquals(SongTuning(bpm = 72, capo = 2), settings.tuningFor(songId))
    }

    @Test
    fun `a tempo with no capo still reads back`() {
        val settings = AppSettings(songBpm = mapOf(songId to 72))

        assertEquals(SongTuning(bpm = 72, capo = 0), settings.tuningFor(songId))
    }

    @Test
    fun `a capo with no tempo still reads back`() {
        val settings = AppSettings(songCapo = mapOf(songId to 3))

        assertEquals(SongTuning(bpm = 0, capo = 3), settings.tuningFor(songId))
    }

    @Test
    fun `writing a tuning stores both halves against the song`() {
        val settings = AppSettings().withTuning(songId, SongTuning(bpm = 96, capo = 4))

        assertEquals(96, settings.songBpm[songId])
        assertEquals(4, settings.songCapo[songId])
    }

    @Test
    fun `writing one song's tuning leaves another song's alone`() {
        val other = "Hymnal::0002"
        val settings = AppSettings(songBpm = mapOf(other to 60), songCapo = mapOf(other to 1))
            .withTuning(songId, SongTuning(bpm = 96, capo = 4))

        assertEquals(SongTuning(bpm = 60, capo = 1), settings.tuningFor(other))
        assertEquals(SongTuning(bpm = 96, capo = 4), settings.tuningFor(songId))
    }

    @Test
    fun `writing a tuning back over an old one replaces it`() {
        val settings = AppSettings(songBpm = mapOf(songId to 60), songCapo = mapOf(songId to 1))
            .withTuning(songId, SongTuning(bpm = 0, capo = 0))

        assertEquals(SongTuning(), settings.tuningFor(songId))
    }
}
