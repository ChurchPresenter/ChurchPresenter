package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the main window persists on close. The placement string's own round trip is
 * `WindowPlacementSettingsTest` in the app, which owns the enum mapping; this covers the half that
 * decides *whether* the measured bounds are worth storing at all.
 */
class WindowGeometryTest {

    @Test
    fun `a floating window remembers where and how big it was`() {
        val saved = AppSettings().withWindowGeometry(
            placement = "floating", isFloating = true, width = 1280, height = 800, x = 120, y = 64,
        )

        assertEquals("floating", saved.windowPlacement)
        assertEquals(1280, saved.windowWidth)
        assertEquals(800, saved.windowHeight)
        assertEquals(120, saved.windowX)
        assertEquals(64, saved.windowY)
    }

    @Test
    fun `a maximized window keeps the floating size it should return to`() {
        val previous = AppSettings().copy(windowWidth = 1280, windowHeight = 800)

        val saved = previous.withWindowGeometry(
            placement = "maximized", isFloating = false, width = 3840, height = 2160, x = 0, y = 0,
        )

        assertEquals(1280, saved.windowWidth, "storing the screen size loses the user's layout for good")
        assertEquals(800, saved.windowHeight)
        assertEquals("maximized", saved.windowPlacement)
    }

    @Test
    fun `a non-floating window clears its position rather than storing the screen origin`() {
        val saved = AppSettings().copy(windowX = 120, windowY = 64).withWindowGeometry(
            placement = "fullscreen", isFloating = false, width = 3840, height = 2160, x = 0, y = 0,
        )

        assertEquals(NO_SAVED_POSITION, saved.windowX, "0,0 may be a screen that is no longer attached")
        assertEquals(NO_SAVED_POSITION, saved.windowY)
    }

    @Test
    fun `the cleared position is what the launch path treats as absent`() {
        val saved = AppSettings().withWindowGeometry(
            placement = "maximized", isFloating = false, width = 100, height = 100, x = 5, y = 5,
        )

        // main.kt restores a saved position only when windowX >= 0.
        assertEquals(true, saved.windowX < 0, "the sentinel must fail the launch path's own check")
    }

    @Test
    fun `nothing outside the window fields is disturbed`() {
        val before = AppSettings().copy(language = "de", theme = "STUDIO", setupWizardShown = true)

        val after = before.withWindowGeometry(
            placement = "floating", isFloating = true, width = 800, height = 600, x = 10, y = 10,
        )

        assertEquals("de", after.language)
        assertEquals("STUDIO", after.theme)
        assertEquals(true, after.setupWizardShown)
    }
}
