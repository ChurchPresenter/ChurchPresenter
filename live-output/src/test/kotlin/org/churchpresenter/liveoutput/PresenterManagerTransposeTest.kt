package org.churchpresenter.liveoutput

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each Browser Source output's musician transpose (issue #649): one offset per output, moved a
 * step at a time from the page's buttons or the desktop tile, and never more than an octave.
 */
class PresenterManagerTransposeTest {

    @Test
    fun `an output starts in the key the song is written in`() {
        assertEquals(emptyMap(), PresenterManager().browserSourceTranspose.value)
    }

    @Test
    fun `steps add up, per output, and never touch another output`() {
        val manager = PresenterManager()
        manager.stepBrowserSourceTranspose(1, 1)
        manager.stepBrowserSourceTranspose(1, 1)
        manager.stepBrowserSourceTranspose(0, -1)
        assertEquals(mapOf(1 to 2, 0 to -1), manager.browserSourceTranspose.value)
    }

    @Test
    fun `back to nothing leaves no entry behind`() {
        val manager = PresenterManager()
        manager.stepBrowserSourceTranspose(2, 1)
        manager.stepBrowserSourceTranspose(2, -1)
        assertEquals(emptyMap(), manager.browserSourceTranspose.value)

        manager.setBrowserSourceTranspose(2, 5)
        manager.setBrowserSourceTranspose(2, 0)
        assertEquals(emptyMap(), manager.browserSourceTranspose.value)
    }

    @Test
    fun `an offset stops at an octave either way`() {
        val manager = PresenterManager()
        repeat(20) { manager.stepBrowserSourceTranspose(0, 1) }
        assertEquals(11, manager.browserSourceTranspose.value[0])
        manager.setBrowserSourceTranspose(0, -40)
        assertEquals(-11, manager.browserSourceTranspose.value[0])
    }
}
