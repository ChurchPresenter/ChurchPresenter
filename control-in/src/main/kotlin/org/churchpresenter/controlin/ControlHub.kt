package org.churchpresenter.controlin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether one port is switched off, open, or could not be opened. */
enum class PortState { OFF, OPEN, FAILED }

/** How each of the hub's four ports stands. */
data class ControlStatus(
    val midiInput: PortState = PortState.OFF,
    val oscInput: PortState = PortState.OFF,
    val midiOutput: PortState = PortState.OFF,
    val oscOutput: PortState = PortState.OFF,
)

/**
 * What the hub opens its ports with -- the real devices and sockets by default, a fake in tests.
 * Each call is the one thin step; everything else is in [ControlHub] and the codecs.
 */
class ControlPorts(
    val openMidiIn: (name: String, onMessage: (ByteArray) -> Unit) -> AutoCloseable? = MidiPorts::openInput,
    val openMidiOut: (name: String) -> MidiSink? = MidiPorts::openOutput,
    val openOscIn: (port: Int, onPacket: (ByteArray) -> Unit) -> AutoCloseable? = OscServer::open,
    val openOscOut: (host: String, port: Int) -> OscSender? = OscSender::open,
)

/**
 * Opens the ports a [ControlSettings] names, turns what arrives on them into the mappings it
 * matches ([onMapping]) -- or, while [learn] is waiting, into the trigger to save -- and sends the
 * outputs when the show does ([emit]). Safe to call from the device threads.
 */
class ControlHub(
    private val onMapping: (ControlMapping) -> Unit,
    private val ports: ControlPorts = ControlPorts(),
) : AutoCloseable {

    private val lock = Any()
    private var settings = ControlSettings()
    private var midiIn: AutoCloseable? = null
    private var oscIn: AutoCloseable? = null
    private var midiOut: MidiSink? = null
    private var oscOut: OscSender? = null
    private var learning: ((Trigger) -> Unit)? = null

    private val _status = MutableStateFlow(ControlStatus())

    /** How each port stands, after the last [apply]. */
    val status: StateFlow<ControlStatus> = _status.asStateFlow()

    private val _isLearning = MutableStateFlow(false)

    /** Whether [learn] is waiting for the next thing to arrive. */
    val isLearning: StateFlow<Boolean> = _isLearning.asStateFlow()

    /**
     * Moves to [next]: the tables take effect at once, and only a port whose own setting changed is
     * closed and opened again -- saving a mapping does not drop the MIDI device or the OSC socket.
     * A port is closed outside the lock, so a device thread waiting in [dispatch] is never what its
     * close waits on.
     */
    fun apply(next: ControlSettings) {
        val stale = mutableListOf<AutoCloseable>()
        synchronized(lock) {
            val before = settings
            settings = next
            if (next.midiInput != before.midiInput) {
                midiIn?.let(stale::add)
                midiIn = openOrNull(next.midiInput.isNotBlank()) {
                    ports.openMidiIn(next.midiInput) { bytes -> Midi.parse(bytes)?.let(::dispatch) }
                }
            }
            if (next.oscInPort != before.oscInPort) {
                oscIn?.let(stale::add)
                oscIn = openOrNull(next.oscInPort > 0) {
                    ports.openOscIn(next.oscInPort) { packet -> Osc.parse(packet).forEach { dispatch(it.toEvent()) } }
                }
            }
            if (next.midiOutput != before.midiOutput) {
                midiOut?.let(stale::add)
                midiOut = openOrNull(next.midiOutput.isNotBlank()) { ports.openMidiOut(next.midiOutput) }
            }
            if (next.oscOutHost != before.oscOutHost || next.oscOutPort != before.oscOutPort) {
                oscOut?.let(stale::add)
                oscOut = openOrNull(next.oscOutHost.isNotBlank() && next.oscOutPort > 0) {
                    ports.openOscOut(next.oscOutHost, next.oscOutPort)
                }
            }
            _status.value = ControlStatus(
                midiInput = stateOf(next.midiInput.isNotBlank(), midiIn),
                oscInput = stateOf(next.oscInPort > 0, oscIn),
                midiOutput = stateOf(next.midiOutput.isNotBlank(), midiOut),
                oscOutput = stateOf(next.oscOutHost.isNotBlank() && next.oscOutPort > 0, oscOut),
            )
        }
        stale.forEach { it.close() }
    }

    /** Takes the next thing that arrives as a trigger for [onTrigger] instead of running any mapping. */
    fun learn(onTrigger: (Trigger) -> Unit) = synchronized(lock) {
        learning = onTrigger
        _isLearning.value = true
    }

    /** Stops waiting for a trigger to learn. */
    fun cancelLearn() = synchronized(lock) {
        learning = null
        _isLearning.value = false
    }

    /** Handles [event] as if it had arrived on a port: learned if [learn] is waiting, else matched. */
    fun dispatch(event: ControlEvent) {
        val matched = synchronized(lock) {
            val waiting = learning
            if (waiting != null) {
                learning = null
                _isLearning.value = false
                waiting(event.toTrigger())
                emptyList()
            } else {
                settings.mappings.filter { it.trigger.matches(event) }
            }
        }
        matched.forEach(onMapping)
    }

    /** Sends every output saved for [event], on the ports that are open. */
    fun emit(event: String) {
        val messages = synchronized(lock) { settings.outputs.filter { it.on == event }.map { it.message } }
        messages.forEach { send(it) }
    }

    override fun close() {
        val open = synchronized(lock) {
            val all = listOfNotNull(midiIn, oscIn, midiOut, oscOut)
            midiIn = null
            oscIn = null
            midiOut = null
            oscOut = null
            settings = ControlSettings()
            _status.value = ControlStatus()
            all
        }
        open.forEach { it.close() }
    }

    private fun send(message: OutMessage) {
        if (message.kind == TriggerKinds.OSC) {
            val osc = synchronized(lock) { oscOut }
            val args = listOfNotNull(Osc.argumentOf(message.argument))
            osc?.send(Osc.encode(OscMessage(message.address, args)))
        } else {
            val midi = synchronized(lock) { midiOut }
            Midi.encode(message).forEach { midi?.send(it) }
        }
    }

    private fun <T> openOrNull(wanted: Boolean, open: () -> T?): T? = if (wanted) open() else null

    private fun stateOf(wanted: Boolean, port: Any?): PortState = when {
        !wanted -> PortState.OFF
        port == null -> PortState.FAILED
        else -> PortState.OPEN
    }
}
