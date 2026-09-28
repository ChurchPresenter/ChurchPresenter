package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLinkInputGateTest {

    @Test
    fun `a source naming a card that is not fitted is not found, whatever else is true`() {
        // The Sentry case: the driver loads, no card is fitted, and a saved source names index 1.
        assertEquals(CameraFailure.DECKLINK_NOT_FOUND, deckLinkInputBlocker(present = false, hasInput = false))
        assertEquals(CameraFailure.DECKLINK_NOT_FOUND, deckLinkInputBlocker(present = false, hasInput = true))
    }

    @Test
    fun `an output-only card is refused before it is asked`() {
        assertEquals(CameraFailure.DECKLINK_NO_INPUT, deckLinkInputBlocker(present = true, hasInput = false))
    }

    @Test
    fun `a present card with an input is tried`() {
        assertNull(deckLinkInputBlocker(present = true, hasInput = true))
    }

    @Test
    fun `a failed open blames the card's own output only when it is driving one`() {
        assertEquals(CameraFailure.DECKLINK_INPUT_IN_USE, deckLinkOpenFailure(outputActive = true))
        assertEquals(CameraFailure.DECKLINK_OPEN_FAILED, deckLinkOpenFailure(outputActive = false))
    }

    @Test
    fun `an open failure is reported once per card, not once per attempt`() {
        val reports = DeckLinkOpenReports()

        assertTrue(reports.claim(1))
        assertFalse(reports.claim(1), "a second attempt at the same card carries nothing new")
        assertTrue(reports.claim(2), "another card is its own report")
    }
}
