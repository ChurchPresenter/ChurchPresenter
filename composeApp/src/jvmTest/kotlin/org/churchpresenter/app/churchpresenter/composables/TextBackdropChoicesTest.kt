package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four-way mode the dialog collapses two booleans into, and the presets row it builds.
 *
 * Taken as functions rather than through the dialog: what a preset does to the *other* half of a
 * look is the difference between picking an outline and losing the plate under it, and that is one
 * assertion here against a rendered row and a click there.
 */
class TextBackdropChoicesTest {

    private val plate = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#112233",
        lineBackgroundOpacity = 61,
        lineBackgroundHeight = 9,
        lineBackgroundOffset = 4,
        border = true,
        borderColor = "#445566",
        borderOpacity = 77,
        borderWidth = 5,
        borderPadding = 15,
        borderRadius = 8,
    )

    // ── Reading the mode ──────────────────────────────────────────────────────

    @Test
    fun `neither half on reads as Off`() {
        assertEquals(TextBackdropMode.OFF, TextBackdrop().mode)
    }

    @Test
    fun `the line background alone reads as Fill`() {
        assertEquals(TextBackdropMode.FILL, TextBackdrop(lineBackground = true).mode)
    }

    @Test
    fun `the border alone reads as Border`() {
        assertEquals(TextBackdropMode.BORDER, TextBackdrop(border = true).mode)
    }

    @Test
    fun `both halves read as Both`() {
        assertEquals(TextBackdropMode.BOTH, plate.mode)
    }

    @Test
    fun `only Fill and Both draw a fill`() {
        assertEquals(
            listOf(false, true, false, true),
            TextBackdropMode.entries.map { it.drawsFill },
        )
    }

    @Test
    fun `only Border and Both draw a border`() {
        assertEquals(
            listOf(false, false, true, true),
            TextBackdropMode.entries.map { it.drawsBorder },
        )
    }

    @Test
    fun `Off is the first choice offered`() {
        assertEquals(TextBackdropMode.OFF, TextBackdropMode.entries.first())
    }

    // ── Writing the mode back ─────────────────────────────────────────────────

    @Test
    fun `switching to Fill turns the border off`() {
        val filled = plate.withMode(TextBackdropMode.FILL)
        assertTrue(filled.lineBackground)
        assertFalse(filled.border)
    }

    @Test
    fun `switching to Border turns the fill off`() {
        val bordered = plate.withMode(TextBackdropMode.BORDER)
        assertTrue(bordered.border)
        assertFalse(bordered.lineBackground)
    }

    @Test
    fun `switching to Both turns everything on`() {
        val both = TextBackdrop().withMode(TextBackdropMode.BOTH)
        assertTrue(both.lineBackground && both.border)
    }

    @Test
    fun `switching to Off turns everything off`() {
        val off = plate.withMode(TextBackdropMode.OFF)
        assertFalse(off.lineBackground || off.border)
        assertTrue(off.isEmpty)
    }

    @Test
    fun `a mode switch changes nothing but the two flags`() {
        assertEquals(
            plate,
            plate.withMode(TextBackdropMode.OFF).withMode(TextBackdropMode.BOTH),
            "a round trip through Off must come back to exactly the same look",
        )
    }

    @Test
    fun `every mode round-trips through its own reading`() {
        for (mode in TextBackdropMode.entries) {
            assertEquals(mode, plate.withMode(mode).mode, "$mode must read back as itself")
        }
    }

    // ── The built-in presets ──────────────────────────────────────────────────

    @Test
    fun `four built-ins ship with the app`() {
        assertEquals(4, TEXT_BACKDROP_PRESETS.size)
    }

    @Test
    fun `a preset's swatch shows the preset on its own, not the current look`() {
        val blackBar = TEXT_BACKDROP_PRESETS[0].preview
        assertEquals("#000000", blackBar.lineBackgroundColor)
        assertEquals(24, blackBar.lineBackgroundHeight)
        assertFalse(blackBar.border, "the black bar is a fill and nothing else")
    }

    @Test
    fun `a fill preset replaces the fill and leaves the border alone`() {
        val shaded = TEXT_BACKDROP_PRESETS[1].applyTo(plate)
        assertEquals(55, shaded.lineBackgroundOpacity, "the preset's own half is replaced")
        assertEquals("#445566", shaded.borderColor, "the other half is untouched")
        assertEquals(5, shaded.borderWidth)
        assertFalse(shaded.border, "and the mode it asks for wins")
    }

    @Test
    fun `a border preset replaces the border and leaves the fill alone`() {
        val outlined = TEXT_BACKDROP_PRESETS[2].applyTo(plate)
        assertEquals(3, outlined.borderWidth)
        assertEquals("#FFFFFF", outlined.borderColor)
        assertEquals(61, outlined.lineBackgroundOpacity, "the plate under it is kept")
    }

    @Test
    fun `the plate preset writes both halves`() {
        val applied = TEXT_BACKDROP_PRESETS[3].applyTo(TextBackdrop())
        assertTrue(applied.lineBackground && applied.border)
        assertEquals(0, applied.lineBackgroundHeight, "a plate asks for no extra band height")
        assertEquals(14, applied.borderRadius)
    }

    // ── The row the dialog draws ──────────────────────────────────────────────

    @Test
    fun `with nothing saved the row is the built-ins in order`() {
        val choices = backdropChoices(emptyList())
        assertEquals(4, choices.size)
        assertEquals(TEXT_BACKDROP_PRESETS.map { it.label }, choices.map { it.label })
    }

    @Test
    fun `a saved look comes first`() {
        val mine = TextBackdrop(lineBackground = true, lineBackgroundOpacity = 42)
        val choices = backdropChoices(listOf(mine))
        assertEquals(mine, choices.first().preview)
        assertNull(choices.first().label, "an operator's own look has no built-in name")
    }

    @Test
    fun `a saved look is applied whole, mode included`() {
        val mine = TextBackdrop(border = true, borderWidth = 9)
        val applied = backdropChoices(listOf(mine)).first().apply(plate)
        assertEquals(mine, applied, "a finished look replaces what was there")
    }

    @Test
    fun `a built-in that matches a saved look is not drawn twice`() {
        val choices = backdropChoices(listOf(TEXT_BACKDROP_PRESETS[0].preview))
        assertEquals(4, choices.size, "the saved copy takes the built-in's place")
        assertEquals(1, choices.count { it.label == null }, "and it is the saved one that is kept")
    }

    @Test
    fun `the built-ins fall off the end as the saved list grows`() {
        val saved = (1..6).map { TextBackdrop(lineBackground = true, lineBackgroundOpacity = it) }
        val choices = backdropChoices(saved, limit = 8)
        assertEquals(8, choices.size)
        assertEquals(6, choices.count { it.label == null })
        assertEquals(2, choices.count { it.label != null }, "only what still fits")
    }

    @Test
    fun `a full row of saved looks leaves no room for a built-in`() {
        val saved = (1..8).map { TextBackdrop(lineBackground = true, lineBackgroundOpacity = it) }
        assertTrue(backdropChoices(saved).none { it.label != null })
    }

    @Test
    fun `the limit is what bounds the row`() {
        assertEquals(2, backdropChoices(emptyList(), limit = 2).size)
    }

    @Test
    fun `every built-in choice carries a name to show in its tooltip`() {
        assertTrue(backdropChoices(emptyList()).all { assertNotNull(it.label) != null })
    }
}
