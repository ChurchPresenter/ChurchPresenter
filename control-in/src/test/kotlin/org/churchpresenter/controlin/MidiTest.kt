package org.churchpresenter.controlin

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MidiTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `a note on is a note, on the channel it was sent`() {
        assertEquals(
            ControlEvent(TriggerKinds.MIDI_NOTE, channel = 1, number = 60, value = 100),
            Midi.parse(bytes(0x90, 60, 100)),
        )
        assertEquals(3, Midi.parse(bytes(0x92, 1, 1))?.channel)
    }

    @Test
    fun `a note on with velocity 0 is a note off and not a trigger`() {
        assertNull(Midi.parse(bytes(0x90, 60, 0)))
        assertNull(Midi.parse(bytes(0x80, 60, 64)))
    }

    @Test
    fun `a control change carries its controller and value`() {
        assertEquals(
            ControlEvent(TriggerKinds.MIDI_CC, channel = 16, number = 7, value = 127),
            Midi.parse(bytes(0xBF, 7, 127)),
        )
    }

    @Test
    fun `other messages and short ones are not triggers`() {
        assertNull(Midi.parse(ByteArray(0)))
        assertNull(Midi.parse(bytes(0x90, 60)))
        assertNull(Midi.parse(bytes(0xB0, 7)))
        assertNull(Midi.parse(bytes(0xF8)))
        assertNull(Midi.parse(bytes(0xE0, 0, 64)))
    }

    @Test
    fun `an MSC go names its command and its cue`() {
        // F0 7F <device> 02 <lighting> <GO> "12" 00 F7
        val event = Midi.parse(bytes(0xF0, 0x7F, 0x01, 0x02, 0x01, 0x01, '1'.code, '2'.code, 0x00, 0xF7))
        assertEquals(ControlEvent(TriggerKinds.MSC, address = "GO", cue = "12"), event)
    }

    @Test
    fun `an MSC command with no cue has a blank one, and every command has a name`() {
        assertEquals("", Midi.parse(bytes(0xF0, 0x7F, 0x7F, 0x02, 0x01, 0x02, 0xF7))?.cue)
        val names = (1..11).map { Midi.parse(bytes(0xF0, 0x7F, 0x01, 0x02, 0x01, it, 0xF7))?.address }
        assertEquals(
            listOf("GO", "STOP", "RESUME", "TIMED_GO", "LOAD", "SET", "FIRE", "ALL_OFF", "RESTORE", "RESET", "GO_OFF"),
            names,
        )
    }

    @Test
    fun `other SysEx is not a trigger`() {
        assertNull(Midi.parse(bytes(0xF0, 0x7E, 0x01, 0x02, 0x01, 0x01, 0xF7)))
        assertNull(Midi.parse(bytes(0xF0, 0x7F, 0x01, 0x03, 0x01, 0x01, 0xF7)))
        assertNull(Midi.parse(bytes(0xF0, 0x7F, 0x01, 0x02, 0x01, 0x63, 0xF7)))
        assertNull(Midi.parse(bytes(0xF0, 0x7F, 0x01, 0xF7)))
    }

    @Test
    fun `a note goes out as a note on then its off, on the channel asked for`() {
        val sent = Midi.encode(OutMessage(TriggerKinds.MIDI_NOTE, channel = 3, number = 36, value = 90))
        assertEquals(2, sent.size)
        assertContentEquals(bytes(0x92, 36, 90), sent[0])
        assertContentEquals(bytes(0x92, 36, 0), sent[1])
    }

    @Test
    fun `a control change goes out once, and what MIDI cannot hold is clamped`() {
        val sent = Midi.encode(OutMessage(TriggerKinds.MIDI_CC, channel = 99, number = 300, value = -4))
        assertEquals(1, sent.size)
        assertContentEquals(bytes(0xBF, 127, 0), sent[0])
    }

    @Test
    fun `an OSC message is not MIDI`() {
        assertTrue(Midi.encode(OutMessage(TriggerKinds.OSC, address = "/a")).isEmpty())
    }
}
