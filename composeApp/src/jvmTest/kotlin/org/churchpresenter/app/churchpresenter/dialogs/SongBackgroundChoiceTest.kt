package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the Background panel offers and what the presenter is willing to draw — the two decisions
 * either side of the picker, tested away from the composables that host them.
 */
class SongBackgroundChoiceTest {

    // ── What the Colors grid offers ─────────────────────────────────────────────

    @Test
    fun `the grid is the design's four solids, four gradients and the custom tile`() {
        assertEquals(9, SONG_BACKGROUND_COLORS.size)
        assertEquals(4, SONG_BACKGROUND_COLORS.count { it.gradient })
        assertEquals(1, SONG_BACKGROUND_COLORS.count { it.own })
    }

    @Test
    fun `picking a solid keeps the dim and blur already set`() {
        val solid = SONG_BACKGROUND_COLORS.first { !it.gradient && !it.own }

        val next = solid.applyTo(SongBackground(dim = 45, blur = 6))

        assertEquals(SongBackgroundType.COLOR, next.type)
        assertEquals(solid.color, next.color)
        assertEquals(45, next.dim)
        assertEquals(6, next.blur)
    }

    @Test
    fun `picking a gradient sets both of its ends`() {
        val gradient = SONG_BACKGROUND_COLORS.first { it.gradient }

        val next = gradient.applyTo(SongBackground())

        assertEquals(SongBackgroundType.GRADIENT, next.type)
        assertEquals(gradient.color, next.color)
        assertEquals(gradient.colorEnd, next.colorEnd)
    }

    @Test
    fun `the custom tile keeps whatever colour the hex field holds`() {
        val own = SONG_BACKGROUND_COLORS.first { it.own }

        val next = own.applyTo(SongBackground(color = "#abcdef"))

        assertEquals(SongBackgroundType.COLOR, next.type)
        assertEquals("#abcdef", next.color, "the tile switches type, it does not choose the colour")
    }

    @Test
    fun `a named colour selects its own tile and not the custom one`() {
        val navy = SONG_BACKGROUND_COLORS.first { !it.gradient && !it.own && it.color == "#0d1b2a" }
        val background = SongBackground(type = SongBackgroundType.COLOR, color = "#0d1b2a")

        assertTrue(navy.selects(background, SONG_BACKGROUND_NAMED_COLORS))
        assertFalse(SONG_BACKGROUND_COLORS.first { it.own }.selects(background, SONG_BACKGROUND_NAMED_COLORS))
    }

    @Test
    fun `a colour of the user's own selects the custom tile`() {
        val background = SongBackground(type = SongBackgroundType.COLOR, color = "#abcdef")

        assertTrue(SONG_BACKGROUND_COLORS.first { it.own }.selects(background, SONG_BACKGROUND_NAMED_COLORS))
        assertTrue(SONG_BACKGROUND_COLORS.none { !it.own && it.selects(background, SONG_BACKGROUND_NAMED_COLORS) })
    }

    @Test
    fun `a gradient tile selects only when both ends match`() {
        val dusk = SONG_BACKGROUND_COLORS.first { it.gradient }
        val exact = dusk.applyTo(SongBackground())
        val halfway = exact.copy(colorEnd = "#000000")

        assertTrue(dusk.selects(exact, SONG_BACKGROUND_NAMED_COLORS))
        assertFalse(dusk.selects(halfway, SONG_BACKGROUND_NAMED_COLORS))
    }

    @Test
    fun `a picture selects no colour tile`() {
        val picture = SongBackground(type = SongBackgroundType.IMAGE, image = "/pics/dawn.jpg")

        assertTrue(SONG_BACKGROUND_COLORS.none { it.selects(picture, SONG_BACKGROUND_NAMED_COLORS) })
    }

    @Test
    fun `the design's four looks are offered, starting from none`() {
        assertEquals(4, SONG_BACKGROUND_LOOKS.size)
        assertEquals(0, SONG_BACKGROUND_LOOKS.first().dim)
        assertEquals(0, SONG_BACKGROUND_LOOKS.first().blur)
        assertTrue(SONG_BACKGROUND_LOOKS.zipWithNext().all { (a, b) -> b.dim > a.dim && b.blur >= a.blur })
    }

    // ── The line the preview sits behind ────────────────────────────────────────

    @Test
    fun `the sample line is the first line the audience would actually read`() {
        val lyrics = """
            [Verse 1]
            [G]Amazing grace how sweet the sound
            That saved a wretch like me
        """.trimIndent()

        assertEquals("Amazing grace how sweet the sound", firstLyricLine(lyrics))
    }

    @Test
    fun `section markers of both shapes are skipped`() {
        assertEquals("a chorus line", firstLyricLine("{Chorus}\na chorus line"))
        assertEquals("a verse line", firstLyricLine("[Verse 1]\na verse line"))
    }

    @Test
    fun `a song with nothing but markers has no sample line`() {
        assertEquals("", firstLyricLine("[Verse 1]\n\n[Chorus]\n"))
        assertEquals("", firstLyricLine(""))
    }
}
