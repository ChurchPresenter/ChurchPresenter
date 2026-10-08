package org.churchpresenter.liveoutput

import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Props staying up while the content changes under them, and what takes them down. */
class PresenterManagerPropsTest {

    private fun manager() = PresenterManager(showPresenterWindowInitially = false)

    @Test
    fun `props go up and down one by one, and are no slide content`() {
        val pm = manager()
        pm.setPropOn("logo", true)
        pm.toggleProp("clock")
        assertEquals(setOf("logo", "clock"), pm.propsOnAir)
        assertEquals(Cue.Props(setOf("logo", "clock")), pm.program.value[Layer.PROPS])
        assertTrue(pm.isLive(Presenting.PROPS))
        assertEquals(Presenting.NONE, pm.slideContent.value)
        pm.toggleProp("logo")
        pm.setPropOn("clock", false)
        assertEquals(emptySet(), pm.propsOnAir)
        assertFalse(Layer.PROPS in pm.program.value)
    }

    @Test
    fun `props stay up through slide content and overlays`() {
        val pm = manager()
        pm.setPropOn("logo", true)
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.setPresentingMode(Presenting.BIBLE)
        assertEquals(setOf("logo"), pm.propsOnAir)
    }

    @Test
    fun `clearing the display or a message going up takes them all down`() {
        val pm = manager()
        pm.setPropOn("logo", true)
        pm.setPresentingMode(Presenting.NONE)
        assertEquals(emptySet(), pm.propsOnAir)
        pm.setPropOn("logo", true)
        pm.showMessage(Cue.Message("Nursery"))
        assertEquals(emptySet(), pm.propsOnAir)
    }

    @Test
    fun `matching a set of props tells the outputs only when something changed`() {
        val pm = manager()
        var told = 0
        pm.onLiveStateChanged = { _, _ -> told++ }
        pm.setPropsOn(setOf("a", "b"))
        pm.setPropsOn(setOf("a", "b"))
        pm.setPropOn("a", true)
        assertEquals(1, told)
        pm.setPropsOn(emptySet())
        assertEquals(emptySet(), pm.propsOnAir)
        assertEquals(2, told)
    }
}
