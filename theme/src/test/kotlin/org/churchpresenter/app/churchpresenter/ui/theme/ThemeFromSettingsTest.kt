package org.churchpresenter.app.churchpresenter.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Turning the saved theme string back into a [ThemeMode] at startup.
 *
 * This replaced a hand-written `when` in `main.kt` that listed **nine of the ten** modes. The one it
 * omitted was [ThemeMode.STUDIO] — which is offered in the top bar, the theme switcher *and* the
 * setup wizard, and has its own colour scheme — so picking Studio and restarting silently dropped the
 * user back on System. Nothing threw and nothing was logged; the theme just quietly reverted.
 *
 * `every mode survives a save and reload` is the test that would have caught it, and is the one to
 * keep: it fails automatically the next time a mode is added to the enum and not to the parser.
 * Matching on [ThemeMode.entries] means that can no longer happen, but the test outlives the
 * implementation.
 */
class ThemeFromSettingsTest {

    /** How the app stores it — see the `appSettings.copy(theme = it.toString())` call sites. */
    private fun saved(mode: ThemeMode) = mode.toString()

    @Test
    fun `every mode survives a save and reload`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, themeFromSettings(saved(mode)), "$mode did not survive a restart")
        }
    }

    @Test
    fun `the studio theme is restored rather than falling back to system`() {
        // The regression this fixes: STUDIO was missing from the parser and fell through to SYSTEM.
        assertEquals(ThemeMode.STUDIO, themeFromSettings("STUDIO"))
    }

    @Test
    fun `an unrecognised theme falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, themeFromSettings("NEON"))
        assertEquals(ThemeMode.SYSTEM, themeFromSettings(""))
        assertEquals(ThemeMode.SYSTEM, themeFromSettings("   "))
    }

    @Test
    fun `an older lower-case settings value is still understood`() {
        assertEquals(ThemeMode.DARK, themeFromSettings("dark"))
        assertEquals(ThemeMode.MIDNIGHT, themeFromSettings("Midnight"))
        assertEquals(ThemeMode.STUDIO, themeFromSettings("studio"))
    }

    @Test
    fun `system itself round-trips rather than only being the fallback`() {
        assertEquals(
            ThemeMode.SYSTEM, themeFromSettings("SYSTEM"),
            "SYSTEM must be reachable by name, not only by failing to match",
        )
    }

    @Test
    fun `no two saved values map to the same mode`() {
        val parsed = ThemeMode.entries.map { themeFromSettings(saved(it)) }

        assertEquals(parsed.size, parsed.toSet().size, "two modes collapsed onto one: $parsed")
    }
}
