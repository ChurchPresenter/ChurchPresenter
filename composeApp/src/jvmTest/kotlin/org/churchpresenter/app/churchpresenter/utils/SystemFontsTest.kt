package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The one font-family snapshot every picker in the app shares.
 *
 * The point of it is that the enumeration happens once: nine composables used to call
 * `GraphicsEnvironment.availableFontFamilyNames` inline, so each settings dialog open walked the
 * machine's font directories again on the UI thread.
 */
class SystemFontsTest {

    @Test
    fun `the machine's font families are listed`() {
        val families = SystemFonts.families()
        assertTrue(families.isNotEmpty(), "a JVM always has at least its logical font families")
    }

    @Test
    fun `the snapshot is taken once and handed out again`() {
        val first = SystemFonts.families()
        assertSame(first, SystemFonts.families(), "a second caller must not re-enumerate")
        assertSame(first, SystemFonts.cached(), "cached() is the same snapshot, readable without blocking")
        assertSame(first, Utils.getAvailableSystemFonts(), "the old accessor is the same list")
    }

    @Test
    fun `a font stack that will not load leaves no fonts rather than an error`() {
        // AWT's native font manager failing to load raises ExceptionInInitializerError — an Error,
        // which `catch (Exception)` does not see. It reached Sentry as a fatal crash from the
        // startup warm-up thread of a bundle whose runtime linked libfontmanager against a copy of
        // harfbuzz the machine did not have. Every picker already renders an empty list.
        assertEquals(
            emptyList(),
            SystemFonts.enumerate { throw ExceptionInInitializerError("no font manager") },
        )
    }

    @Test
    fun `the enumerated families are sorted before anyone sees them`() {
        assertEquals(
            listOf("Andale Mono", "arial", "Zapfino"),
            SystemFonts.enumerate { arrayOf("Zapfino", "arial", "Andale Mono") },
        )
    }

    @Test
    fun `families are sorted case-insensitively`() {
        // Every picker shows this list as-is, so the order is the menu's order. Five of the call
        // sites used to skip the sort and show AWT's own order instead.
        val families = SystemFonts.families()
        assertEquals(families.sortedBy { it.lowercase() }, families)
    }
}
