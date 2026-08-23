package org.churchpresenter.ui

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
    fun `families are sorted case-insensitively`() {
        // Every picker shows this list as-is, so the order is the menu's order. Five of the call
        // sites used to skip the sort and show AWT's own order instead.
        val families = SystemFonts.families()
        assertEquals(families.sortedBy { it.lowercase() }, families)
    }
}
