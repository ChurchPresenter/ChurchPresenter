package org.churchpresenter.sharedui.utils

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FontCatalogTest {

    @AfterTest
    fun forgetTheScan() = FontCatalog.reset()

    @Test
    fun `system faces whose names start with a dot or a hash are hidden`() {
        assertTrue(FontCatalog.isHidden(".SF NS Text"))
        assertTrue(FontCatalog.isHidden("#GungSeo"))
    }

    @Test
    fun `icon and dingbat families are hidden whatever their case`() {
        assertTrue(FontCatalog.isHidden("Wingdings"))
        assertTrue(FontCatalog.isHidden("SEGOE MDL2 ASSETS"))
        assertTrue(FontCatalog.isHidden("Apple Color Emoji"))
    }

    @Test
    fun `an ordinary family is not hidden`() {
        assertFalse(FontCatalog.isHidden("Arial"))
        assertFalse(FontCatalog.isHidden("Georgia"))
    }

    @Test
    fun `the projection favourites are recommended regardless of case`() {
        assertTrue(FontCatalog.isRecommended("Helvetica Neue"))
        assertTrue(FontCatalog.isRecommended("ROBOTO"))
        assertFalse(FontCatalog.isRecommended("Zapfino"))
    }

    @Test
    fun `a measured monospaced family is mono whatever its name`() {
        assertEquals(FontCategory.MONO, FontCatalog.categoryOf("Plain Grotesk", monospaced = true))
    }

    @Test
    fun `a name hinting at code or a typewriter is mono`() {
        assertEquals(FontCategory.MONO, FontCatalog.categoryOf("Fira Code", monospaced = null))
        assertEquals(FontCategory.MONO, FontCatalog.categoryOf("Courier New", monospaced = false))
    }

    @Test
    fun `a decorative name is display`() {
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Brush Script MT", monospaced = false))
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Impact", monospaced = null))
    }

    @Test
    fun `a name hinting at serifs is serif`() {
        assertEquals(FontCategory.SERIF, FontCatalog.categoryOf("Times New Roman", monospaced = false))
        assertEquals(FontCategory.SERIF, FontCatalog.categoryOf("Georgia", monospaced = null))
    }

    @Test
    fun `display wins over serif when a name hints at both`() {
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Engraved Serif", monospaced = false))
    }

    @Test
    fun `anything else is sans`() {
        assertEquals(FontCategory.SANS, FontCatalog.categoryOf("Arial", monospaced = false))
    }

    @Test
    fun `the unmeasured snapshot leaves hidden families out and counts them`() {
        val snapshot = FontCatalog.unmeasuredSnapshot(listOf("Arial", "Wingdings", ".Hidden", "Georgia"))
        assertEquals(listOf("Arial", "Georgia"), snapshot.faces.map { it.name })
        assertEquals(2, snapshot.hiddenCount)
        assertFalse(snapshot.measured)
    }

    @Test
    fun `a hidden family in use is still offered`() {
        val snapshot = FontCatalog.unmeasuredSnapshot(listOf("Arial", "Wingdings"), keep = "wingdings")
        assertEquals(listOf("Arial", "Wingdings"), snapshot.faces.map { it.name })
        assertEquals(0, snapshot.hiddenCount)
    }

    @Test
    fun `the unmeasured description covers no script but knows shape and recommendation`() {
        val face = FontCatalog.unmeasuredSnapshot(listOf("Georgia")).faces.single()
        assertEquals(FontCategory.SERIF, face.category)
        assertTrue(face.recommended)
        assertFalse(face.cyrillic)
        assertFalse(face.hebrew)
    }

    @Test
    fun `the measured snapshot says it was measured and keeps the family order`() {
        val families = SystemFonts.families().take(3)
        val snapshot = FontCatalog.snapshot(families)
        assertTrue(snapshot.measured)
        assertEquals(families.filterNot(FontCatalog::isHidden), snapshot.faces.map { it.name })
    }

    @Test
    fun `a family nothing can be made of is still described by its name`() {
        val snapshot = FontCatalog.snapshot(listOf("No Such Family Courier 9000"))
        val face = snapshot.faces.single()
        assertEquals("No Such Family Courier 9000", face.name)
        assertEquals(FontCategory.MONO, face.category)
    }

    @Test
    fun `the catalog a picker remembers is measured once the scan lands`() = runComposeUiTest {
        val families = SystemFonts.families().take(2)
        var snapshot: FontCatalogSnapshot? = null
        setContent { snapshot = rememberFontCatalog(families) }
        waitUntil(timeoutMillis = 10_000) { snapshot?.measured == true }
        assertEquals(families.filterNot(FontCatalog::isHidden), snapshot?.faces?.map { it.name })
    }

    @Test
    fun `an empty set of families is never scanned`() = runComposeUiTest {
        var snapshot: FontCatalogSnapshot? = null
        setContent { snapshot = rememberFontCatalog(emptyList()) }
        waitForIdle()
        assertEquals(emptyList(), snapshot?.faces)
        assertFalse(snapshot?.measured ?: true)
    }
}
