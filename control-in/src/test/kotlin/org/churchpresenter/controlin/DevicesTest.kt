package org.churchpresenter.controlin

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiUnavailableException
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.midi.Transmitter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicesTest {

    private val waitSeconds = 10L

    @Test
    fun `an OSC server hands over every datagram it receives`() {
        val got = mutableListOf<ByteArray>()
        val arrived = CountDownLatch(1)
        val server = assertNotNull(
            OscServer.open(0) { packet ->
                synchronized(got) { got += packet }
                arrived.countDown()
            },
        )
        server.use {
            DatagramSocket().use { socket ->
                val packet = Osc.encode(OscMessage("/hello", listOf(1)))
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getLoopbackAddress(), server.port))
            }
            assertTrue(arrived.await(waitSeconds, TimeUnit.SECONDS))
        }
        assertEquals("/hello", Osc.parse(got.single()).single().address)
    }

    @Test
    fun `a port that is taken is not opened`() {
        val first = assertNotNull(OscServer.open(0) { })
        first.use { assertNull(OscServer.open(first.port) { }) }
    }

    @Test
    fun `an OSC sender delivers its packet to the host and port`() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
            receiver.soTimeout = (waitSeconds * 1000).toInt()
            val sender = assertNotNull(OscSender.open("127.0.0.1", receiver.localPort))
            sender.use { it.send(Osc.encode(OscMessage("/out", listOf("x")))) }
            val buffer = ByteArray(512)
            val packet = DatagramPacket(buffer, buffer.size)
            receiver.receive(packet)
            assertEquals("/out", Osc.parse(buffer, packet.length).single().address)
        }
    }

    @Test
    fun `a host that does not resolve gives no sender, and a send to nowhere is dropped`() {
        assertNull(OscSender.open("no such host.invalid", 9000))
        val sender = assertNotNull(OscSender.open("127.0.0.1", 9))
        sender.use { it.send(ByteArray(8)) }
    }

    @Test
    fun `an unknown MIDI device does not open, and listing devices never throws`() {
        assertNull(MidiPorts.openInput("no such device") { })
        assertNull(MidiPorts.openOutput("no such device"))
        MidiPorts.inputNames()
        MidiPorts.outputNames()
    }
}

class MidiDevicesTest {

    /** A MIDI device with no hardware: records what is sent to it, and lets a test play a message in. */
    private class FakeDevice(
        name: String,
        private val transmitters: Int = 1,
        private val receivers: Int = 1,
        private val failToOpen: Boolean = false,
    ) : MidiDevice {
        val received = mutableListOf<List<Int>>()
        var open = false
        var attached: Receiver? = null
        private val info = object : MidiDevice.Info(name, "", "", "") {}

        override fun getDeviceInfo() = info
        override fun open() {
            if (failToOpen) throw MidiUnavailableException("busy")
            open = true
        }
        override fun close() {
            open = false
        }
        override fun isOpen() = open
        override fun getMicrosecondPosition() = -1L
        override fun getMaxReceivers() = receivers
        override fun getMaxTransmitters() = transmitters
        override fun getReceiver(): Receiver = object : Receiver {
            override fun send(message: MidiMessage, timeStamp: Long) {
                received += message.message.map { it.toInt() and 0xFF }
            }
            override fun close() = Unit
        }
        override fun getReceivers() = emptyList<Receiver>()
        override fun getTransmitter(): Transmitter = object : Transmitter {
            override fun setReceiver(receiver: Receiver?) {
                attached = receiver
            }
            override fun getReceiver() = attached
            override fun close() = Unit
        }
        override fun getTransmitters() = emptyList<Transmitter>()
    }

    private val pad = FakeDevice("Pad", transmitters = 1, receivers = 0)
    private val desk = FakeDevice("Desk", transmitters = 0, receivers = 1)
    private val devices = MidiDevices { listOf(pad, desk) }

    @Test
    fun `inputs are the devices that send, outputs the devices that receive`() {
        assertEquals(listOf("Pad"), devices.inputNames())
        assertEquals(listOf("Desk"), devices.outputNames())
    }

    @Test
    fun `a pad that is played hands its message's bytes over, and closing it closes the device`() {
        val got = mutableListOf<List<Int>>()
        val port = assertNotNull(devices.openInput("Pad") { got += it.map { b -> b.toInt() and 0xFF } })
        assertTrue(pad.open)

        pad.attached!!.send(ShortMessage(0x90, 60, 100), -1)

        assertEquals(listOf(listOf(0x90, 60, 100)), got)
        port.close()
        assertFalse(pad.open)
    }

    @Test
    fun `a desk is sent short messages, and closing it closes the device`() {
        val out = assertNotNull(devices.openOutput("Desk"))
        out.send(byteArrayOf(0x90.toByte(), 36, 127))
        out.send(byteArrayOf(0xC0.toByte(), 5))
        out.send(ByteArray(0))
        assertEquals(listOf(listOf(0x90, 36, 127), listOf(0xC0, 5)), desk.received)
        out.close()
        assertFalse(desk.open)
    }

    @Test
    fun `a device is not opened as the wrong way round, or when it is missing, or when it is busy`() {
        assertNull(devices.openInput("Desk") { })
        assertNull(devices.openOutput("Pad"))
        assertNull(devices.openInput("Nothing") { })
        assertNull(MidiDevices { listOf(FakeDevice("Busy", failToOpen = true)) }.openInput("Busy") { })
    }
}
