package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How much of a song one slide holds. Four stored fields behind one control: the ordinary slide and
 * the look-ahead slide each keep their own, per output shape.
 */
class SongElementChunkTest {

    private val verse = Constants.SONG_DISPLAY_MODE_VERSE
    private val line = Constants.SONG_DISPLAY_MODE_LINE

    private val full = SongStyleTarget.FULL_SCREEN
    private val band = SongStyleTarget.LOWER_THIRD

    private val ordinary = listOf(SongStyleElement.NUMBER, SongStyleElement.TITLE, SongStyleElement.LYRICS)
    private val lookAhead = listOf(SongStyleElement.LOOK_AHEAD, SongStyleElement.NEXT_SECTION)

    private val tuned = SongSettings(
        fullscreenDisplayMode = verse,
        lowerThirdDisplayMode = line,
        lookAheadDisplayMode = line,
        lowerThirdLookAheadDisplayMode = verse,
    )

    // ── Which field each element reads ────────────────────────────────────────

    @Test
    fun `an ordinary element on a full screen reads the full screen's chunk`() {
        for (element in ordinary) {
            assertEquals(verse, tuned.chunkFor(element, full), "$element")
        }
    }

    @Test
    fun `an ordinary element on a band reads the band's chunk`() {
        for (element in ordinary) {
            assertEquals(line, tuned.chunkFor(element, band), "$element")
        }
    }

    @Test
    fun `a look-ahead element on a full screen reads the look-ahead slide's chunk`() {
        for (element in lookAhead) {
            assertEquals(line, tuned.chunkFor(element, full), "$element")
        }
    }

    @Test
    fun `a look-ahead element on a band reads the band's look-ahead chunk`() {
        for (element in lookAhead) {
            assertEquals(verse, tuned.chunkFor(element, band), "$element")
        }
    }

    // ── Which field each element writes ───────────────────────────────────────

    @Test
    fun `an ordinary element writes the full screen's chunk alone`() {
        val out = tuned.withChunk(SongStyleElement.LYRICS, full, line)
        assertEquals(line, out.fullscreenDisplayMode)
        assertEquals(line, out.lowerThirdDisplayMode, "the band's own is untouched")
        assertEquals(line, out.lookAheadDisplayMode, "and both look-ahead fields with it")
        assertEquals(verse, out.lowerThirdLookAheadDisplayMode)
    }

    @Test
    fun `an ordinary element writes the band's chunk alone`() {
        val out = tuned.withChunk(SongStyleElement.TITLE, band, verse)
        assertEquals(verse, out.lowerThirdDisplayMode)
        assertEquals(verse, out.fullscreenDisplayMode, "the full screen's own is untouched")
        assertEquals(line, out.lookAheadDisplayMode)
    }

    @Test
    fun `a look-ahead element writes the look-ahead slide's chunk alone`() {
        val out = tuned.withChunk(SongStyleElement.LOOK_AHEAD, full, verse)
        assertEquals(verse, out.lookAheadDisplayMode)
        assertEquals(verse, out.fullscreenDisplayMode, "the ordinary slide's own is untouched")
        assertEquals(verse, out.lowerThirdLookAheadDisplayMode)
    }

    @Test
    fun `the next section writes the same field the look-ahead line does`() {
        val out = tuned.withChunk(SongStyleElement.NEXT_SECTION, band, line)
        assertEquals(line, out.lowerThirdLookAheadDisplayMode)
        assertEquals(line, out.lowerThirdDisplayMode, "the band's ordinary slide is untouched")
    }

    // ── The two together ──────────────────────────────────────────────────────

    @Test
    fun `every element and output round-trips through its own field`() {
        for (element in SongStyleElement.entries) {
            for (target in SongStyleTarget.entries) {
                for (mode in listOf(verse, line)) {
                    assertEquals(
                        mode,
                        tuned.withChunk(element, target, mode).chunkFor(element, target),
                        "$element on $target must read back $mode",
                    )
                }
            }
        }
    }

    @Test
    fun `writing one combination moves exactly one of the four fields`() {
        val fields: (SongSettings) -> List<String> = {
            listOf(
                it.fullscreenDisplayMode,
                it.lowerThirdDisplayMode,
                it.lookAheadDisplayMode,
                it.lowerThirdLookAheadDisplayMode,
            )
        }
        for (element in SongStyleElement.entries) {
            for (target in SongStyleTarget.entries) {
                val other = if (tuned.chunkFor(element, target) == verse) line else verse
                val after = fields(tuned.withChunk(element, target, other))
                val moved = fields(tuned).zip(after).count { it.first != it.second }
                assertEquals(1, moved, "$element on $target must move one field, not $moved")
            }
        }
    }

    @Test
    fun `the two slide kinds ship disagreeing on a band`() {
        val defaults = SongSettings()
        assertEquals(line, defaults.chunkFor(SongStyleElement.LYRICS, band))
        assertEquals(verse, defaults.chunkFor(SongStyleElement.LYRICS, full))
    }
}
