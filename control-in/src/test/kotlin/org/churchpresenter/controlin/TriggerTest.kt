package org.churchpresenter.controlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerTest {

    private val note = ControlEvent(TriggerKinds.MIDI_NOTE, channel = 2, number = 60, value = 100)
    private val cc = ControlEvent(TriggerKinds.MIDI_CC, channel = 1, number = 7, value = 64)

    @Test
    fun `a note matches its channel and number, and channel 0 matches any channel`() {
        assertTrue(Trigger(TriggerKinds.MIDI_NOTE, channel = 2, number = 60).matches(note))
        assertTrue(Trigger(TriggerKinds.MIDI_NOTE, channel = 0, number = 60).matches(note))
        assertFalse(Trigger(TriggerKinds.MIDI_NOTE, channel = 1, number = 60).matches(note))
        assertFalse(Trigger(TriggerKinds.MIDI_NOTE, channel = 2, number = 61).matches(note))
    }

    @Test
    fun `a control change matches any value unless one is named`() {
        assertTrue(Trigger(TriggerKinds.MIDI_CC, number = 7).matches(cc))
        assertTrue(Trigger(TriggerKinds.MIDI_CC, number = 7, value = 64).matches(cc))
        assertFalse(Trigger(TriggerKinds.MIDI_CC, number = 7, value = 65).matches(cc))
    }

    @Test
    fun `an MSC command matches by name in any case, and by cue only when one is named`() {
        val go = ControlEvent(TriggerKinds.MSC, address = "GO", cue = "12")
        assertTrue(Trigger(TriggerKinds.MSC, address = "go").matches(go))
        assertTrue(Trigger(TriggerKinds.MSC, address = "GO", cue = "12").matches(go))
        assertFalse(Trigger(TriggerKinds.MSC, address = "GO", cue = "13").matches(go))
        assertFalse(Trigger(TriggerKinds.MSC, address = "STOP").matches(go))
    }

    @Test
    fun `an OSC message matches by address, and by first number only when one is named`() {
        val event = ControlEvent(TriggerKinds.OSC, address = "/cp/macro/walk-in", value = 1)
        assertTrue(Trigger(TriggerKinds.OSC, address = "/cp/macro/walk-in").matches(event))
        assertTrue(Trigger(TriggerKinds.OSC, address = "/cp/macro/walk-in", value = 1).matches(event))
        assertFalse(Trigger(TriggerKinds.OSC, address = "/cp/macro/walk-in", value = 0).matches(event))
        assertFalse(Trigger(TriggerKinds.OSC, address = "/cp/other").matches(event))
    }

    @Test
    fun `a kind from a newer build never matches, and a different kind does not match`() {
        assertFalse(Trigger("midiPitchBend").matches(ControlEvent("midiPitchBend")))
        assertFalse(Trigger(TriggerKinds.MIDI_NOTE, number = 60).matches(cc.copy(number = 60)))
    }

    @Test
    fun `learning a note, a fader, a command or an address forgets the value`() {
        assertEquals(Trigger(TriggerKinds.MIDI_NOTE, 2, 60), note.toTrigger())
        assertEquals(Trigger(TriggerKinds.MIDI_CC, 1, 7), cc.toTrigger())
        assertEquals(
            Trigger(TriggerKinds.MSC, address = "GO", cue = "3"),
            ControlEvent(TriggerKinds.MSC, address = "GO", cue = "3").toTrigger(),
        )
        assertEquals(
            Trigger(TriggerKinds.OSC, address = "/a"),
            ControlEvent(TriggerKinds.OSC, address = "/a", value = 5).toTrigger(),
        )
    }
}
