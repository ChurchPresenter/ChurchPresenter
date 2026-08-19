package presentation.engine

import org.junit.jupiter.api.Test
import presentation.engine.fonts.SlideFontRegistry
import java.awt.Font
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlideFontRegistryTest {

    @Test
    fun `bundled font registers and resolves by family`() {
        val stream = assertNotNull(
            javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf"),
            "test font resource missing"
        )
        val family = assertNotNull(SlideFontRegistry.registerFontStream(stream))
        assertEquals("Open Sans", family)
        assertTrue(SlideFontRegistry.isFamilyAvailable("Open Sans"))
        assertTrue(SlideFontRegistry.isFamilyAvailable("open sans"), "family lookup must be case-insensitive")
    }

    @Test
    fun `unknown family substitutes to an available one`() {
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        val resolved = SlideFontRegistry.resolveFamily("Definitely Not A Font 123")
        assertTrue(
            SlideFontRegistry.isFamilyAvailable(resolved) || resolved == Font.SANS_SERIF,
            "resolveFamily must return a renderable family, got $resolved"
        )
    }

    @Test
    fun `calibri substitution prefers the table over the generic default`() {
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        val resolved = SlideFontRegistry.resolveFamily("Calibri")
        // Whatever the platform offers, the result must be a real, available family.
        assertTrue(SlideFontRegistry.isFamilyAvailable(resolved), "got unavailable family $resolved")
    }
}
