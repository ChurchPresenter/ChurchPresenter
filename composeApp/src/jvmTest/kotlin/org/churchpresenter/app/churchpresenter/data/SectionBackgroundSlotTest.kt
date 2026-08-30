package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.core.models.songs.SONG_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SONG_LOWER_THIRD_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lyrics-text side of per-section backgrounds: which sections the editor can pin one to, and
 * what writing one does to the lines.
 *
 * These are the two operations the background panel drives. They are pure list-of-strings
 * transforms on purpose — the panel is a popup inside a dialog, and none of the rules below need a
 * window to be true.
 */
class SectionBackgroundSlotTest {

    private val dusk = SongBackground(
        type = SongBackgroundType.GRADIENT, color = "#131a3a", colorEnd = "#3a2352", dim = 25, blur = 3,
    )
    private val band = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130", dim = 65)

    // ── Reading the sections ────────────────────────────────────────────────────

    @Test
    fun `every header is a section a background can be pinned to`() {
        val slots = sectionBackgroundSlots(listOf("[Verse 1]", "a", "{Chorus}", "b", "[Bridge]", "c"))

        assertEquals(listOf("Verse 1", "Chorus", "Bridge"), slots.map { it.label })
        assertEquals(listOf(0, 2, 4), slots.map { it.headerIndex })
        assertTrue(slots.none { it.isCustom }, "a song with no directives inherits everywhere")
    }

    @Test
    fun `words written before any header are a section too`() {
        // A song with no headers at all is one section, and it can have a background like any other.
        val slots = sectionBackgroundSlots(listOf("just a line", "and another"))

        assertEquals(1, slots.size)
        assertEquals("", slots.single().label)
        assertEquals(-1, slots.single().headerIndex)
    }

    @Test
    fun `a section reads back the background its directives write`() {
        val slots = sectionBackgroundSlots(
            listOf(
                "[Verse 1]", "a",
                "{Chorus}",
                "[background: gradient]",
                "[background-color: #131a3a]",
                "[background-color-end: #3a2352]",
                "[background-dim: 25]",
                "[background-blur: 3]",
                "b",
            ),
        )

        assertEquals(dusk, slots[1].background)
        assertFalse(slots[0].isCustom, "the verse said nothing, so it still inherits")
    }

    @Test
    fun `the two outputs are held apart`() {
        val slots = sectionBackgroundSlots(
            listOf("{Chorus}", "[lower-third-background: color]", "[lower-third-background-color: #2a1130]",
                "[lower-third-background-dim: 65]", "b"),
        )

        assertEquals(band, slots.single().lowerThirdBackground)
        assertFalse(slots.single().background.isCustom, "the full-screen one is untouched")
    }

    @Test
    fun `a section of nothing but directives is still a section`() {
        // Someone can style a section before writing its words; the panel must not lose it.
        val slots = sectionBackgroundSlots(listOf("[Verse 1]", "[background: color]", "[background-color: #101010]"))

        assertEquals(1, slots.size)
        assertTrue(slots.single().isCustom)
    }

    // ── Writing one ─────────────────────────────────────────────────────────────

    @Test
    fun `a background is written directly under its section's header`() {
        val lines = listOf("[Verse 1]", "a", "{Chorus}", "b")

        val out = withSectionBackground(lines, 1, SONG_BACKGROUND_PREFIX, band)

        assertEquals(
            listOf("[Verse 1]", "a", "{Chorus}", "[background: color]", "[background-color: #2a1130]",
                "[background-dim: 65]", "b"),
            out,
        )
    }

    @Test
    fun `writing one back reads the same background out`() {
        val out = withSectionBackground(listOf("{Chorus}", "b"), 0, SONG_BACKGROUND_PREFIX, dusk)

        assertEquals(dusk, sectionBackgroundSlots(out).single().background)
    }

    @Test
    fun `writing replaces what was there rather than piling up`() {
        val once = withSectionBackground(listOf("{Chorus}", "b"), 0, SONG_BACKGROUND_PREFIX, dusk)
        val twice = withSectionBackground(once, 0, SONG_BACKGROUND_PREFIX, band)

        assertEquals(band, sectionBackgroundSlots(twice).single().background)
        assertEquals(1, twice.count { it.startsWith("[background:") }, "one type line, not two")
    }

    @Test
    fun `going back to inheriting leaves the lyrics as they started`() {
        val lines = listOf("[Verse 1]", "a", "{Chorus}", "b")
        val styled = withSectionBackground(lines, 1, SONG_BACKGROUND_PREFIX, dusk)

        assertEquals(lines, withSectionBackground(styled, 1, SONG_BACKGROUND_PREFIX, SongBackground()))
    }

    @Test
    fun `writing one output leaves the other's directives alone`() {
        val full = withSectionBackground(listOf("{Chorus}", "b"), 0, SONG_BACKGROUND_PREFIX, dusk)
        val both = withSectionBackground(full, 0, SONG_LOWER_THIRD_BACKGROUND_PREFIX, band)

        val slot = sectionBackgroundSlots(both).single()
        assertEquals(dusk, slot.background)
        assertEquals(band, slot.lowerThirdBackground)
    }

    @Test
    fun `a background written on one section touches no other`() {
        val lines = listOf("[Verse 1]", "a", "{Chorus}", "b", "[Verse 2]", "c")

        val out = withSectionBackground(lines, 1, SONG_BACKGROUND_PREFIX, band)

        val slots = sectionBackgroundSlots(out)
        assertTrue(slots[0].isCustom.not() && slots[2].isCustom.not(), "the verses still inherit")
        assertEquals(listOf("a"), out.subList(1, 2))
        assertEquals("c", out.last())
    }

    @Test
    fun `a headerless section takes its directives at the top`() {
        val out = withSectionBackground(listOf("just a line"), 0, SONG_BACKGROUND_PREFIX, band)

        assertEquals("[background: color]", out.first())
        assertEquals(band, sectionBackgroundSlots(out).single().background)
    }

    @Test
    fun `a slot that is not there leaves the song alone`() {
        val lines = listOf("[Verse 1]", "a")

        assertEquals(lines, withSectionBackground(lines, 7, SONG_BACKGROUND_PREFIX, band))
    }
}
