package org.churchpresenter.app.churchpresenter

import org.churchpresenter.theme.UI_SCALES
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/** The main window's room at each interface size, and the cap where no screen can be asked. */
class UiScaleWiringTest {

    @Test
    fun `at the default size the main window keeps its own minimum`() {
        assertEquals(Dimension(MIN_MAIN_WINDOW_WIDTH, MIN_MAIN_WINDOW_HEIGHT), scaledMainWindowMinimum(1f, 2f))
    }

    @Test
    fun `the minimum grows with the interface, so its layout still fits`() {
        assertEquals(Dimension(1200, 900), scaledMainWindowMinimum(1.5f, 2f))
    }

    @Test
    fun `the minimum never asks for more than the screen allows`() {
        assertEquals(Dimension(1000, 750), scaledMainWindowMinimum(2f, 1.25f), "a size saved on a bigger screen")
    }

    @Test
    fun `with no screen to ask, every step is allowed`() {
        // The suite runs headless: there is no screen, so nothing can be measured.
        assertEquals(UI_SCALES.last(), screenMaxUiScale(null))
    }
}
