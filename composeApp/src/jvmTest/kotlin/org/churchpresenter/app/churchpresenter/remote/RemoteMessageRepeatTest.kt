package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.messageOnAir
import org.churchpresenter.server.LiveStateDto
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RemoteMessageRepeatTest {

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
    }

    private val follower = PresenterManager(showPresenterWindowInitially = false)
    private val everything = { _: Presenting -> true }

    private fun withMessage(text: String?) = LiveStateDto(
        contentType = Presenting.MESSAGE.name,
        liveSlide = Presenting.NONE.name,
        overlays = emptyList(),
        message = text,
    )

    @Test
    fun `the same message sent again is left up rather than put up afresh`() {
        followMessage(withMessage("Nursery #4"), follower, everything)
        val first = follower.messageOnAir
        followMessage(withMessage("Nursery #4"), follower, everything)
        assertSame(first, follower.messageOnAir)
    }

    @Test
    fun `a new message from the primary replaces the one up here`() {
        followMessage(withMessage("Nursery #4"), follower, everything)
        followMessage(withMessage("Car lights on"), follower, everything)
        assertEquals("Car lights on", follower.messageOnAir?.text)
    }

    @Test
    fun `a state naming no slide still puts its overlay up`() {
        val state = LiveStateDto(
            contentType = Presenting.LOWER_THIRD.name,
            liveSlide = null,
            overlays = listOf(Presenting.LOWER_THIRD.name),
        )
        followAir(state, Presenting.LOWER_THIRD, follower, everything)
        assertEquals(setOf(Presenting.LOWER_THIRD), follower.overlays.value)
    }
}
