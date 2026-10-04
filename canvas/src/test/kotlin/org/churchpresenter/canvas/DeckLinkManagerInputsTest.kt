package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLinkManagerInputsTest {

    @Test
    fun `without the native library every input answers as no card`() {
        assertFalse(DeckLinkManager.isAvailable())

        assertNull(DeckLinkManagerInputs.findDevice(0))
        assertTrue(DeckLinkManagerInputs.inputModes(0).isEmpty())
        assertTrue(DeckLinkManagerInputs.videoConnections(0).isEmpty())
        assertFalse(DeckLinkManagerInputs.openInput(0, "", 0))
        assertFalse(DeckLinkManagerInputs.isOutputActive(0))
        assertNull(DeckLinkManagerInputs.inputFrame(0))
        runBlocking { DeckLinkManagerInputs.pause(0) }
    }
}
