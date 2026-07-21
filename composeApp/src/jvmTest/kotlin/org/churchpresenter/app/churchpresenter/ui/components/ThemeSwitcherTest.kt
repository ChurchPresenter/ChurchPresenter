package org.churchpresenter.app.churchpresenter.ui.components

import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The glyph the theme picker shows against each theme.
 *
 * `ThemeSwitcher` itself is a composable that needs a composition, but the icon table it draws from
 * is ordinary data. The compiler guarantees the table is complete — its `when` is exhaustive over
 * [ThemeMode], so an eleventh theme will not compile until it has a glyph — but it cannot notice
 * two themes sharing one, which is what turns the menu into a list of identical rows.
 *
 * The table is a private top-level function, so it is reached by reflection on its file class.
 */
class ThemeSwitcherTest {

    private fun themeIcon(mode: ThemeMode): String =
        Class.forName("org.churchpresenter.app.churchpresenter.ui.components.ThemeSwitcherKt")
            .getDeclaredMethod("themeIcon", ThemeMode::class.java)
            .apply { isAccessible = true }
            .invoke(null, mode) as String

    @Test
    fun `every theme has a glyph`() {
        ThemeMode.entries.forEach { mode ->
            assertTrue(themeIcon(mode).isNotBlank(), "$mode has no icon, so its row reads as a blank line")
        }
    }

    @Test
    fun `no two themes share a glyph`() {
        val icons = ThemeMode.entries.associateWith { themeIcon(it) }

        assertEquals(
            ThemeMode.entries.size,
            icons.values.toSet().size,
            "themes sharing an icon cannot be told apart in the menu: $icons",
        )
    }

    /**
     * NOTE: this pins the current emoji, which AGENT.md's "never use text/emoji as icons" rule says
     * should eventually become real icon assets. When that migration happens this test should be
     * deleted rather than updated — the two above it (every theme has one, no two share one) are
     * the ones worth keeping, and they hold for `painterResource` icons just as well.
     */
    @Test
    fun `the light and dark themes keep their conventional glyphs`() {
        // These two are the ones an operator looks for by shape rather than by reading the label.
        assertEquals("☀", themeIcon(ThemeMode.LIGHT))
        assertEquals("🌙", themeIcon(ThemeMode.DARK))
    }
}
