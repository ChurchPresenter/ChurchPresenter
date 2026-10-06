package org.churchpresenter.controlin

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.churchpresenter.showcontrol.Action

class ControlHubTest {

    private val walkIn = ControlMapping(
        id = "m1",
        name = "Walk in",
        trigger = Trigger(TriggerKinds.MIDI_NOTE, channel = 1, number = 60),
        actions = listOf(Action.RunMacro("walk in")),
    )
    private val clearOsc = ControlMapping(
        id = "m2",
        trigger = Trigger(TriggerKinds.OSC, address = "/cp/clear"),
        actions = listOf(Action.ClearAll),
    )

    /** Ports that record what is opened and hand the callbacks back, with no device or socket. */
    private class FakePorts(var failMidiIn: Boolean = false) {
        val midiBytes = CopyOnWriteArrayList<ByteArray>()
        val oscBytes = CopyOnWriteArrayList<ByteArray>()
        val closed = CopyOnWriteArrayList<String>()
        var onMidi: ((ByteArray) -> Unit)? = null
        var onOsc: ((ByteArray) -> Unit)? = null
        var oscInPort = 0
        var oscOutTarget = ""

        fun ports() = ControlPorts(
            openMidiIn = { name, onMessage ->
                if (failMidiIn) null else AutoCloseable { closed += "midiIn:$name" }.also { onMidi = onMessage }
            },
            openMidiOut = { null },
            openOscIn = { port, onPacket ->
                oscInPort = port
                onOsc = onPacket
                AutoCloseable { closed += "oscIn:$port" }
            },
            openOscOut = { host, port ->
                oscOutTarget = "$host:$port"
                null
            },
        )
    }

    private fun hub(fake: FakePorts, got: MutableList<ControlMapping>) = ControlHub({ got += it }, fake.ports())

    @Test
    fun `a note from the input runs the mapping that matches it`() {
        val fake = FakePorts()
        val got = mutableListOf<ControlMapping>()
        hub(fake, got).apply(ControlSettings(midiInput = "Pad", mappings = listOf(walkIn, clearOsc)))

        fake.onMidi!!(byteArrayOf(0x90.toByte(), 60, 100))
        fake.onMidi!!(byteArrayOf(0x90.toByte(), 61, 100))

        assertEquals(listOf(walkIn), got)
    }

    @Test
    fun `an OSC packet, bundle or not, runs the mappings for each message in it`() {
        val fake = FakePorts()
        val got = mutableListOf<ControlMapping>()
        hub(fake, got).apply(ControlSettings(oscInPort = 9000, mappings = listOf(clearOsc)))

        fake.onOsc!!(Osc.encode(OscMessage("/cp/clear")))
        fake.onOsc!!(Osc.encode(OscMessage("/cp/other")))
        fake.onOsc!!(byteArrayOf(9, 9))

        assertEquals(9000, fake.oscInPort)
        assertEquals(listOf(clearOsc), got)
    }

    @Test
    fun `learning takes the next event as a trigger and runs nothing, then mappings run again`() {
        val fake = FakePorts()
        val got = mutableListOf<ControlMapping>()
        val hub = hub(fake, got)
        hub.apply(ControlSettings(midiInput = "Pad", mappings = listOf(walkIn)))
        val learned = mutableListOf<Trigger>()

        hub.learn { learned += it }
        assertTrue(hub.isLearning.value)
        fake.onMidi!!(byteArrayOf(0x90.toByte(), 60, 100))

        assertEquals(listOf(Trigger(TriggerKinds.MIDI_NOTE, 1, 60)), learned)
        assertFalse(hub.isLearning.value)
        assertTrue(got.isEmpty())
        fake.onMidi!!(byteArrayOf(0x90.toByte(), 60, 100))
        assertEquals(listOf(walkIn), got)
    }

    @Test
    fun `cancelling a learn leaves mappings running and learns nothing`() {
        val fake = FakePorts()
        val got = mutableListOf<ControlMapping>()
        val hub = hub(fake, got)
        hub.apply(ControlSettings(midiInput = "Pad", mappings = listOf(walkIn)))
        val learned = mutableListOf<Trigger>()

        hub.learn { learned += it }
        hub.cancelLearn()
        fake.onMidi!!(byteArrayOf(0x90.toByte(), 60, 100))

        assertTrue(learned.isEmpty())
        assertFalse(hub.isLearning.value)
        assertEquals(listOf(walkIn), got)
    }

    @Test
    fun `status says which ports are off, open or failed`() {
        val fake = FakePorts(failMidiIn = true)
        val hub = hub(fake, mutableListOf())

        hub.apply(ControlSettings(midiInput = "Gone", oscInPort = 9000, oscOutHost = "10.0.0.2", oscOutPort = 8000))

        assertEquals(
            ControlStatus(
                midiInput = PortState.FAILED,
                oscInput = PortState.OPEN,
                midiOutput = PortState.OFF,
                oscOutput = PortState.FAILED,
            ),
            hub.status.value,
        )
        assertEquals("10.0.0.2:8000", fake.oscOutTarget)
    }

    @Test
    fun `applying again closes the old ports, and closing the hub closes them all`() {
        val fake = FakePorts()
        val hub = hub(fake, mutableListOf())
        hub.apply(ControlSettings(midiInput = "Pad", oscInPort = 9000))

        hub.apply(ControlSettings(midiInput = "Other"))
        assertEquals(listOf("midiIn:Pad", "oscIn:9000"), fake.closed.toList())

        hub.close()
        assertEquals("midiIn:Other", fake.closed.last())
        assertEquals(ControlStatus(), hub.status.value)
    }

    private class RecordingSink : MidiSink {
        val sent = CopyOnWriteArrayList<List<Int>>()
        override fun send(bytes: ByteArray) {
            sent += bytes.map { it.toInt() and 0xFF }
        }
        override fun close() = Unit
    }

    @Test
    fun `emitting sends the outputs saved for that event to the open MIDI and OSC ports and no others`() {
        val midi = RecordingSink()
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { desk ->
            desk.soTimeout = 10_000
            val ports = ControlPorts(
                openMidiIn = { _, _ -> null },
                openMidiOut = { midi },
                openOscIn = { _, _ -> null },
                openOscOut = { _, port -> OscSender.open("127.0.0.1", port) },
            )
            val hub = ControlHub({ }, ports)
            hub.apply(
                ControlSettings(
                    midiOutput = "Desk",
                    oscOutHost = "127.0.0.1",
                    oscOutPort = desk.localPort,
                    outputs = listOf(
                        ControlOutput("o1", OutputEvents.GO_LIVE, OutMessage(TriggerKinds.MIDI_NOTE, number = 36)),
                        ControlOutput(
                            "o2", OutputEvents.CLEAR,
                            OutMessage(TriggerKinds.OSC, address = "/cleared", argument = "2"),
                        ),
                    ),
                ),
            )

            hub.emit(OutputEvents.GO_LIVE)
            hub.emit(OutputEvents.TAKE)
            assertEquals(listOf(listOf(0x90, 36, 127), listOf(0x90, 36, 0)), midi.sent.toList())

            hub.emit(OutputEvents.CLEAR)
            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            desk.receive(packet)
            assertEquals(OscMessage("/cleared", listOf(2)), Osc.parse(buffer, packet.length).single())
            assertEquals(2, midi.sent.size)
            hub.close()
        }
    }

    @Test
    fun `settings with a kind from a newer build load, and the trigger just never matches`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val text = """{"mappings":[{"id":"x","trigger":{"kind":"midiPitchBend","number":1,"futureField":true},""" +
            """"actions":[{"type":"clearAll"}]}],"outputs":[{"id":"o","on":"someday"}],"newThing":1}"""

        val settings = json.decodeFromString(ControlSettings.serializer(), text)

        val mapping = settings.mappings.single()
        assertEquals("midiPitchBend", mapping.trigger.kind)
        assertFalse(mapping.trigger.matches(ControlEvent("midiPitchBend", number = 1)))
        assertEquals(listOf<Action>(Action.ClearAll), mapping.actions)
        assertEquals("someday", settings.outputs.single().on)
        // What the newer build wrote is kept when this one saves it back.
        val saved = json.encodeToString(ControlSettings.serializer(), settings)
        assertEquals(settings, json.decodeFromString(ControlSettings.serializer(), saved))
    }
}

class ControlSettingsTest {

    @Test
    fun `every field of a full settings round-trips through JSON`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val full = ControlSettings(
            midiInput = "Pad",
            midiOutput = "Desk",
            oscInPort = 9000,
            oscOutHost = "10.0.0.2",
            oscOutPort = 8000,
            mappings = listOf(
                ControlMapping(
                    "m1", "Walk in", Trigger(TriggerKinds.MIDI_CC, 3, 7, 64, "/a", "5"), listOf(Action.ClearAll),
                ),
            ),
            outputs = listOf(
                ControlOutput("o1", OutputEvents.TAKE, OutMessage(TriggerKinds.OSC, 2, 3, 4, "/x", "1.5")),
            ),
        )

        val text = json.encodeToString(ControlSettings.serializer(), full)

        assertEquals(full, json.decodeFromString(ControlSettings.serializer(), text))
        val mapping = full.mappings.single()
        assertEquals(listOf("m1", "Walk in"), listOf(mapping.id, mapping.name))
        assertEquals(listOf(3, 7, 64), listOf(mapping.trigger.channel, mapping.trigger.number, mapping.trigger.value))
        assertEquals(listOf("/a", "5"), listOf(mapping.trigger.address, mapping.trigger.cue))
        assertEquals("o1", full.outputs.single().id)
    }

    @Test
    fun `status reads back each port's state`() {
        val status = ControlStatus(PortState.OPEN, PortState.FAILED, PortState.OFF, PortState.OPEN)
        assertEquals(
            listOf(PortState.OPEN, PortState.FAILED, PortState.OFF, PortState.OPEN),
            listOf(status.midiInput, status.oscInput, status.midiOutput, status.oscOutput),
        )
    }

    @Test
    fun `a hub built with the system ports opens and closes with nothing configured`() {
        ControlHub({ }).use { it.apply(ControlSettings()) }
    }
}
