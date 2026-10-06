package org.churchpresenter.liveoutput

import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongInfoTest {

    // ── The song info line ──────────────────────────────────────────────────────

    private fun info(section: LyricSection) =
        songInfoOf(section, keyLabel = "Key", capoLabel = "Capo", playLabel = "Play", bpmLabel = "BPM")

    @Test
    fun `the key is read off the chart`() {
        assertEquals("Key G", info(LyricSection(chordLines = listOf("[G]word [C]word"))))
    }

    @Test
    fun `a capo also says what to actually play`() {
        // Key G with a capo at 2 means F shapes — the shapes are what the player reads.
        assertEquals(
            "Key G  ·  Capo 2  ·  Play F",
            info(LyricSection(chordLines = listOf("[G]word"), capo = 2)),
        )
    }

    @Test
    fun `no capo means no shapes to mention`() {
        val line = info(LyricSection(chordLines = listOf("[G]word"), capo = 0))
        assertEquals("Key G", line)
    }

    @Test
    fun `a tempo is reported with or without chords`() {
        assertEquals("72 BPM", info(LyricSection(bpm = 72)))
        assertEquals("Key G  ·  72 BPM", info(LyricSection(chordLines = listOf("[G]word"), bpm = 72)))
    }

    @Test
    fun `a section with nothing to report says nothing at all`() {
        assertNull(info(LyricSection(lines = listOf("just words"))))
    }
}
