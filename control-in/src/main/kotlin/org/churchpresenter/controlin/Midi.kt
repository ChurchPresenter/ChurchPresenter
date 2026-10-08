package org.churchpresenter.controlin

/** Reads and writes the MIDI messages a trigger or an output means, as plain functions of bytes. */
object Midi {

    /**
     * The event [bytes] -- one complete MIDI message -- stands for: a note on, a control change or a
     * MIDI Show Control command; null for anything else (a note off, a clock tick, other SysEx).
     */
    fun parse(bytes: ByteArray): ControlEvent? {
        if (bytes.isEmpty()) return null
        val status = bytes[0].toInt() and STATUS_MASK
        val channel = (bytes[0].toInt() and CHANNEL_MASK) + 1
        return when {
            status == NOTE_ON && bytes.size >= SHORT_MESSAGE && data(bytes, 2) > 0 ->
                ControlEvent(TriggerKinds.MIDI_NOTE, channel, data(bytes, 1), data(bytes, 2))
            status == CONTROL_CHANGE && bytes.size >= SHORT_MESSAGE ->
                ControlEvent(TriggerKinds.MIDI_CC, channel, data(bytes, 1), data(bytes, 2))
            bytes[0] == SYSEX -> parseShowControl(bytes)
            else -> null
        }
    }

    /**
     * The messages [message] sends, as raw bytes: a note on followed by its note off, a lone control
     * change, or none for an OSC message. MIDI channel and data bytes are clamped to what MIDI allows.
     */
    fun encode(message: OutMessage): List<ByteArray> {
        val channel = (message.channel.coerceIn(1, CHANNELS) - 1)
        val number = message.number.coerceIn(0, DATA_MAX)
        return when (message.kind) {
            TriggerKinds.MIDI_NOTE -> {
                val on = message.value.coerceIn(1, DATA_MAX)
                listOf(shortMessage(NOTE_ON or channel, number, on), shortMessage(NOTE_ON or channel, number, 0))
            }
            TriggerKinds.MIDI_CC ->
                listOf(shortMessage(CONTROL_CHANGE or channel, number, message.value.coerceIn(0, DATA_MAX)))
            else -> emptyList()
        }
    }

    /** MIDI Show Control: `F0 7F <device> 02 <format> <command> [cue 00 ...] F7`. */
    private fun parseShowControl(bytes: ByteArray): ControlEvent? {
        val isShowControl = bytes.size >= MSC_MIN && bytes[1] == UNIVERSAL_REALTIME && bytes[3] == MSC_SUB_ID
        val command = if (isShowControl) MSC_COMMANDS[bytes[5].toInt()] else null
        if (command == null) return null
        val cue = bytes.drop(MSC_DATA).takeWhile { it != 0.toByte() && it != SYSEX_END }
            .joinToString("") { it.toInt().toChar().toString() }
        return ControlEvent(TriggerKinds.MSC, address = command, cue = cue)
    }

    private fun shortMessage(status: Int, data1: Int, data2: Int) =
        byteArrayOf(status.toByte(), data1.toByte(), data2.toByte())

    private fun data(bytes: ByteArray, index: Int) = bytes[index].toInt() and DATA_MAX

    private val MSC_COMMANDS = mapOf(
        1 to "GO", 2 to "STOP", 3 to "RESUME", 4 to "TIMED_GO", 5 to "LOAD", 6 to "SET",
        7 to "FIRE", 8 to "ALL_OFF", 9 to "RESTORE", 10 to "RESET", 11 to "GO_OFF",
    )

    private const val STATUS_MASK = 0xF0
    private const val CHANNEL_MASK = 0x0F
    private const val NOTE_ON = 0x90
    private const val CONTROL_CHANGE = 0xB0
    private const val DATA_MAX = 0x7F
    private const val SHORT_MESSAGE = 3
    private const val CHANNELS = 16
    private const val SYSEX: Byte = 0xF0.toByte()
    private const val SYSEX_END: Byte = 0xF7.toByte()
    private const val UNIVERSAL_REALTIME: Byte = 0x7F
    private const val MSC_SUB_ID: Byte = 0x02
    private const val MSC_MIN = 7
    private const val MSC_DATA = 6
}
