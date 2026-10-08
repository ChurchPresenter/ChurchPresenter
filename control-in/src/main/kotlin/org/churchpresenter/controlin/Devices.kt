package org.churchpresenter.controlin

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.MidiUnavailableException
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer

/** Listens for OSC packets on a UDP port and hands each one's bytes to [onPacket]. */
class OscServer private constructor(
    private val socket: DatagramSocket,
    private val onPacket: (ByteArray) -> Unit,
) : AutoCloseable {

    /** The port actually bound -- what [open] picked when asked for port 0. */
    val port: Int get() = socket.localPort

    private val thread = Thread(::receiveLoop, "osc-in-${socket.localPort}").apply {
        isDaemon = true
        start()
    }

    private fun receiveLoop() {
        val buffer = ByteArray(MAX_PACKET)
        val packet = DatagramPacket(buffer, buffer.size)
        while (!socket.isClosed) {
            try {
                socket.receive(packet)
                onPacket(buffer.copyOf(packet.length))
            } catch (_: SocketException) {
                return
            } catch (_: IOException) {
                return
            }
        }
    }

    override fun close() {
        socket.close()
        thread.join(JOIN_MILLIS)
    }

    companion object {
        /** Binds [port] on every interface; null when it is taken or not allowed. */
        fun open(port: Int, onPacket: (ByteArray) -> Unit): OscServer? =
            try {
                OscServer(DatagramSocket(port), onPacket)
            } catch (_: SocketException) {
                null
            }

        private const val MAX_PACKET = 65_507
        private const val JOIN_MILLIS = 500L
    }
}

/** Sends OSC packets to one host and port over UDP. */
class OscSender private constructor(
    private val socket: DatagramSocket,
    private val address: InetAddress,
    private val port: Int,
) : AutoCloseable {

    /** Sends [packet]; a send that fails is dropped -- the desk may simply be off. */
    fun send(packet: ByteArray) {
        try {
            socket.send(DatagramPacket(packet, packet.size, address, port))
        } catch (_: IOException) {
            // Nothing to do: the next event tries again.
        }
    }

    override fun close() = socket.close()

    companion object {
        /** A sender to [host]:[port]; null when the host does not resolve. */
        fun open(host: String, port: Int): OscSender? =
            try {
                OscSender(DatagramSocket(), InetAddress.getByName(host), port)
            } catch (_: IOException) {
                null
            }
    }
}

/** The MIDI devices [all] lists, and the calls that name and open them. */
class MidiDevices(private val all: () -> List<MidiDevice>) {

    /** The names of the devices that send MIDI in -- keyboards, pads, desks; not software sequencers. */
    fun inputNames(): List<String> = names { it.maxTransmitters != 0 }

    /** The names of the devices MIDI can be sent to -- not the software synthesizer. */
    fun outputNames(): List<String> = names { it.maxReceivers != 0 }

    /** Opens the input called [name] and hands each message's bytes to [onMessage]; null when it will not open. */
    fun openInput(name: String, onMessage: (ByteArray) -> Unit): AutoCloseable? =
        open(name, { it.maxTransmitters != 0 }) { device ->
            device.transmitter.receiver = object : Receiver {
                override fun send(message: MidiMessage, timeStamp: Long) = onMessage(message.message)
                override fun close() = Unit
            }
        }

    /** Opens the output called [name]; null when it will not open. */
    fun openOutput(name: String): MidiSink? {
        var out: MidiSink? = null
        open(name, { it.maxReceivers != 0 }) { device -> out = MidiOut(device, device.receiver) }
        return out
    }

    private fun open(name: String, usable: (MidiDevice) -> Boolean, attach: (MidiDevice) -> Unit): AutoCloseable? =
        try {
            all().firstOrNull { it.deviceInfo.name == name && usable(it) && it.isHardware() }?.let { device ->
                device.open()
                attach(device)
                AutoCloseable { device.close() }
            }
        } catch (_: MidiUnavailableException) {
            null
        }

    private fun names(usable: (MidiDevice) -> Boolean): List<String> =
        all().filter { usable(it) && it.isHardware() }.map { it.deviceInfo.name }.distinct()

    private fun MidiDevice.isHardware() = this !is Sequencer && this !is Synthesizer
}

/** The MIDI devices of this machine. */
object MidiPorts {
    private val system = MidiDevices {
        MidiSystem.getMidiDeviceInfo().mapNotNull { runCatching { MidiSystem.getMidiDevice(it) }.getOrNull() }
    }

    /** The names of the devices that send MIDI in. */
    fun inputNames(): List<String> = system.inputNames()

    /** The names of the devices MIDI can be sent to. */
    fun outputNames(): List<String> = system.outputNames()

    /** Opens the input called [name]; null when there is none or it will not open. */
    fun openInput(name: String, onMessage: (ByteArray) -> Unit): AutoCloseable? = system.openInput(name, onMessage)

    /** Opens the output called [name]; null when there is none or it will not open. */
    fun openOutput(name: String): MidiSink? = system.openOutput(name)
}

/** Somewhere MIDI can be sent: [send] writes one complete short message to it. */
interface MidiSink : AutoCloseable {
    /** Sends [bytes] -- one short message -- now. */
    fun send(bytes: ByteArray)
}

/** An open MIDI output device. */
class MidiOut internal constructor(private val device: MidiDevice, private val receiver: Receiver) : MidiSink {

    override fun send(bytes: ByteArray) {
        runCatching {
            val message = ShortMessage(
                bytes[0].toInt() and BYTE, bytes[1].toInt() and BYTE, bytes.getOrElse(2) { 0 }.toInt() and BYTE,
            )
            receiver.send(message, -1)
        }
    }

    override fun close() = device.close()

    private companion object {
        const val BYTE = 0xFF
    }
}
