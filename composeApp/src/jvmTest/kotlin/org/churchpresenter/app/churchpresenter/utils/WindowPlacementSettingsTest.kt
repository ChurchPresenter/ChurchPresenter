package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.withWindowGeometry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the main window's placement and geometry survive a restart.
 *
 * These were two hand-written `when` blocks in `main.kt` pointing in opposite directions — enum to
 * string on close, string to enum on launch — with nothing tying them together. That is the shape
 * that produced the Studio theme bug: a pair where one side is compiler-checked and the other is not.
 * `every placement survives a save and reload` is the test that ties them, and it fails the moment
 * someone adds a case to one side only.
 *
 * The geometry rules the other half of that pairing rests on — what a maximized window does and
 * does not overwrite — are `WindowGeometryTest` in :settings, which owns `withWindowGeometry`.
 */
class WindowPlacementSettingsTest {

    // ── The round trip ──────────────────────────────────────────────────────────

    @Test
    fun `every placement survives a save and reload`() {
        listOf(WindowPlacement.Floating, WindowPlacement.Fullscreen, WindowPlacement.Maximized)
            .forEach { placement ->
                assertEquals(
                    placement,
                    windowPlacementFromSettings(windowPlacementToSettings(placement)),
                    "$placement did not survive a restart",
                )
            }
    }

    @Test
    fun `no two placements share a stored value`() {
        val stored = listOf(WindowPlacement.Floating, WindowPlacement.Fullscreen, WindowPlacement.Maximized)
            .map { windowPlacementToSettings(it) }

        assertEquals(stored.size, stored.toSet().size, "two placements collapsed onto one: $stored")
    }

    @Test
    fun `an unrecognised value opens maximized rather than floating`() {
        // Floating would pair with saved coordinates that may be on a display no longer attached.
        assertEquals(WindowPlacement.Maximized, windowPlacementFromSettings("tiled"))
        assertEquals(WindowPlacement.Maximized, windowPlacementFromSettings(""))
    }

    @Test
    fun `the shipped default is understood`() {
        assertEquals(WindowPlacement.Maximized, windowPlacementFromSettings(AppSettings().windowPlacement))
    }

    // ── What gets persisted on close ────────────────────────────────────────────
    // The geometry rules themselves are WindowGeometryTest in :settings; what is left here is the
    // pairing of those with the placement mapping, which lives in this module.

    @Test
    fun `a floating window round-trips its geometry through settings`() {
        val saved = AppSettings().withWindowGeometry(
            placement = windowPlacementToSettings(WindowPlacement.Floating),
            isFloating = true, width = 1024, height = 768, x = 33, y = 44,
        )

        assertEquals(WindowPlacement.Floating, windowPlacementFromSettings(saved.windowPlacement))
        assertEquals(1024, saved.windowWidth)
        assertEquals(33, saved.windowX)
    }
}
