package org.churchpresenter.signage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A message for a screen on the wire: what it carries, whether it takes a touch page's touches, and when
 * it stops showing.
 */
class MessageWireTest {
    @Test
    fun aMessageGoesWithTheScreenAndLeavesOutWhatIsUnset() {
        val message = ScreenMessage("Parking lot B is full", fullScreen = true, untilMs = 99)
        val screen = ScreenSettings("Lobby", message = message)
        val text = SignageJson.encodeToString(ScreenSettings.serializer(), screen)
        assertTrue(""""message":{"text":"Parking lot B is full","fullScreen":true,"untilMs":99}""" in text, text)
        assertEquals(screen, SignageJson.decodeFromString(ScreenSettings.serializer(), text))
        val plain = SignageJson.encodeToString(
            ScreenSettings.serializer(),
            ScreenSettings("Lobby", message = ScreenMessage("Hi")),
        )
        assertTrue(""""message":{"text":"Hi"}""" in plain, plain)
        assertFalse("message" in SignageJson.encodeToString(ScreenSettings.serializer(), ScreenSettings("Lobby")))
    }

    @Test
    fun aMessageShowsUntilItRunsOutAndNeverWithoutWords() {
        assertTrue(ScreenMessage("Welcome").showsAt(Long.MAX_VALUE))
        val timed = ScreenMessage("Doors open", untilMs = 1_000)
        assertTrue(timed.showsAt(999))
        assertFalse(timed.showsAt(1_000))
        assertFalse(ScreenMessage("  ").showsAt(0))
    }

    @Test
    fun aBandThatBlocksTouchSaysSoAndOneThatDoesNotLeavesItOut() {
        val blocking = ScreenMessage("Doors open", blockTouch = true)
        val text = SignageJson.encodeToString(ScreenMessage.serializer(), blocking)
        assertTrue("\"blockTouch\":true" in text, text)
        assertEquals(blocking, SignageJson.decodeFromString(ScreenMessage.serializer(), text))
        val plain = SignageJson.encodeToString(ScreenMessage.serializer(), ScreenMessage("Doors open"))
        assertFalse("blockTouch" in plain, plain)
    }

    @Test
    fun aFullScreenMessageAlwaysBlocksTouchAndABandOnlyWhenAsked() {
        assertTrue(ScreenMessage("x", fullScreen = true).blocksTouch)
        assertTrue(ScreenMessage("x", blockTouch = true).blocksTouch)
        assertFalse(ScreenMessage("x").blocksTouch)
    }
}
