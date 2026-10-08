@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A message going up alone over everything, and what takes it down: its own clock, a clear, or
 * slide content going live.
 */
class PresenterManagerMessageTest {

    private fun manager() = PresenterManager(showPresenterWindowInitially = false)
    private val nursery = Cue.Message("Parent of child #42", "Nursery")

    @Test
    fun `a message clears every other layer and goes up alone`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.showMessage(nursery)
        assertEquals(nursery, pm.messageOnAir)
        assertEquals(setOf(Presenting.MESSAGE), pm.liveContent.value)
        assertEquals(Presenting.NONE, pm.slideContent.value)
        assertEquals(Presenting.MESSAGE, pm.lastLive.value)
        assertEquals(setOf(Layer.MESSAGES), pm.program.value.keys)
    }

    @Test
    fun `slide content going live takes the message down, and an overlay does not`() {
        val pm = manager()
        pm.showMessage(nursery)
        pm.setPresentingMode(Presenting.STT)
        assertTrue(pm.isLive(Presenting.MESSAGE), "captions go up over it")
        pm.setPresentingMode(Presenting.BIBLE)
        assertNull(pm.messageOnAir)
        assertEquals(Presenting.BIBLE, pm.slideContent.value)
    }

    @Test
    fun `clearing the display or the message takes it down`() {
        val pm = manager()
        pm.showMessage(nursery)
        pm.setPresentingMode(Presenting.NONE)
        assertFalse(pm.anythingLive)
        pm.showMessage(nursery)
        pm.clearMessage()
        assertNull(pm.messageOnAir)
    }

    @Test
    fun `clearing with no message up tells nobody`() {
        val pm = manager()
        var told = 0
        pm.onLiveStateChanged = { _, _ -> told++ }
        pm.clearMessage()
        assertEquals(0, told)
    }

    @Test
    fun `a message runs out after its duration`() = runComposeUiTest {
        val pm = manager()
        mainClock.autoAdvance = false
        setContent { MessageExpiry(pm) }
        pm.showMessage(nursery.copy(durationSeconds = 2))
        mainClock.advanceTimeBy(1_500)
        assertTrue(pm.isLive(Presenting.MESSAGE), "not yet")
        mainClock.advanceTimeBy(1_000)
        assertNull(pm.messageOnAir)
    }

    @Test
    fun `a message with no duration, or replaced in time, is left up`() = runComposeUiTest {
        val pm = manager()
        mainClock.autoAdvance = false
        setContent { MessageExpiry(pm) }
        pm.showMessage(nursery)
        mainClock.advanceTimeBy(3_000)
        assertEquals(nursery, pm.messageOnAir)
        pm.showMessage(Cue.Message("First", durationSeconds = 2))
        mainClock.advanceTimeBy(1_000)
        val second = Cue.Message("Second")
        pm.showMessage(second)
        mainClock.advanceTimeBy(2_000)
        assertEquals(second, pm.messageOnAir, "the first one's clock does not take the second down")
    }

    @Test
    fun `the same message sent again while it is up starts its time over`() = runComposeUiTest {
        val pm = manager()
        mainClock.autoAdvance = false
        setContent { MessageExpiry(pm) }
        val timed = nursery.copy(durationSeconds = 2)
        pm.showMessage(timed)
        mainClock.advanceTimeBy(1_500)
        pm.showMessage(timed)
        mainClock.advanceTimeBy(1_000)
        assertEquals(timed, pm.messageOnAir, "resent at 1.5s, so it has 2s from there")
        mainClock.advanceTimeBy(1_500)
        assertNull(pm.messageOnAir)
    }
}
