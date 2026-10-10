package org.churchpresenter.controlin

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * One OSC 1.0 message: its address and its arguments (Int, Long, Float, Double, String, Boolean,
 * ByteArray or null).
 */
data class OscMessage(val address: String, val args: List<Any?> = emptyList())

/** Reads and writes OSC 1.0 packets -- messages and bundles -- as plain functions of bytes. */
object Osc {

    /** The messages in [bytes], bundles unpacked; empty for a packet that is not OSC. Never throws. */
    fun parse(bytes: ByteArray, length: Int = bytes.size): List<OscMessage> =
        runCatching { OscReader.parsePacket(ByteBuffer.wrap(bytes, 0, length)) }.getOrDefault(emptyList())

    /** [message] as an OSC packet. */
    fun encode(message: OscMessage): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeString(message.address)
        out.writeString("," + message.args.joinToString("") { typeTag(it).toString() })
        message.args.forEach { out.writeArgument(it) }
        return out.toByteArray()
    }

    /** [text] as the argument it reads as: an Int, else a Float, else the text itself; null when blank. */
    fun argumentOf(text: String): Any? = text.trim().let {
        when {
            it.isEmpty() -> null
            else -> it.toIntOrNull() ?: it.toFloatOrNull() ?: it
        }
    }

    /** The first number among [args] as an Int -- what a control change's value is to a mapping -- or [ANY]. */
    fun firstNumber(args: List<Any?>): Int = when (val first = args.firstOrNull { it is Number }) {
        is Float -> first.toInt()
        is Double -> first.toInt()
        is Number -> first.toInt()
        else -> ANY
    }

    private fun typeTag(arg: Any?): Char = when (arg) {
        is Int -> 'i'
        is Float -> 'f'
        is Long -> 'h'
        is Double -> 'd'
        is Boolean -> if (arg) 'T' else 'F'
        is ByteArray -> 'b'
        null -> 'N'
        else -> 's'
    }

    private fun ByteArrayOutputStream.writeString(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        write(bytes)
        repeat(padding(bytes.size + 1) + 1) { write(0) }
    }

    private fun ByteArrayOutputStream.writeArgument(arg: Any?) {
        when (arg) {
            is Int -> write(ByteBuffer.allocate(INT_BYTES).putInt(arg).array())
            is Float -> write(ByteBuffer.allocate(INT_BYTES).putFloat(arg).array())
            is Long -> write(ByteBuffer.allocate(LONG_BYTES).putLong(arg).array())
            is Double -> write(ByteBuffer.allocate(LONG_BYTES).putDouble(arg).array())
            is ByteArray -> {
                write(ByteBuffer.allocate(INT_BYTES).putInt(arg.size).array())
                write(arg)
                repeat(padding(arg.size)) { write(0) }
            }
            is Boolean, null -> Unit
            else -> writeString(arg.toString())
        }
    }
}

/** The reading half of [Osc]: packets, bundles, messages and their arguments. */
internal object OscReader {
    fun parsePacket(buf: ByteBuffer): List<OscMessage> {
        if (!buf.hasRemaining()) return emptyList()
        return if (buf.get(buf.position()) == '#'.code.toByte()) parseBundle(buf) else listOfNotNull(parseMessage(buf))
    }

    private fun parseBundle(buf: ByteBuffer): List<OscMessage> {
        if (readString(buf) != BUNDLE) return emptyList()
        buf.position(buf.position() + TIME_TAG_BYTES)
        val messages = mutableListOf<OscMessage>()
        while (buf.remaining() >= INT_BYTES) {
            val size = buf.getInt()
            if (size < 0 || size > buf.remaining()) break
            val element = buf.slice().also { it.limit(size) }
            messages += parsePacket(element)
            buf.position(buf.position() + size)
        }
        return messages
    }

    private fun parseMessage(buf: ByteBuffer): OscMessage? {
        val address = readString(buf)
        if (!address.startsWith("/")) return null
        // A message with no type tags is legal in old OSC; it has no arguments.
        val tags = if (buf.hasRemaining()) readString(buf) else ","
        if (!tags.startsWith(",")) return OscMessage(address)
        val args = mutableListOf<Any?>()
        for (tag in tags.drop(1)) {
            when (tag) {
                'T' -> args += true
                'F' -> args += false
                'N', 'I' -> args += null
                else -> args += readArgument(buf, tag) ?: break
            }
        }
        return OscMessage(address, args)
    }

    private fun readArgument(buf: ByteBuffer, tag: Char): Any? = when (tag) {
        'i' -> buf.getInt()
        'f' -> buf.getFloat()
        'h' -> buf.getLong()
        'd' -> buf.getDouble()
        's' -> readString(buf)
        'b' -> ByteArray(buf.getInt()).also { buf.get(it); buf.position(buf.position() + padding(it.size)) }
        else -> null
    }

    private fun readString(buf: ByteBuffer): String {
        val start = buf.position()
        var end = start
        while (buf.get(end) != 0.toByte()) end++
        val text = String(buf.array(), buf.arrayOffset() + start, end - start, Charsets.UTF_8)
        buf.position(start + (end - start) + 1 + padding(end - start + 1))
        return text
    }
}

/** How many zero bytes pad [length] bytes up to a multiple of four. */
internal fun padding(length: Int): Int = (INT_BYTES - length % INT_BYTES) % INT_BYTES

private const val BUNDLE = "#bundle"
private const val INT_BYTES = 4
private const val LONG_BYTES = 8
private const val TIME_TAG_BYTES = 8

/** This message as the event a mapping matches: its address and first number argument. */
fun OscMessage.toEvent(): ControlEvent =
    ControlEvent(TriggerKinds.OSC, address = address, value = Osc.firstNumber(args))
