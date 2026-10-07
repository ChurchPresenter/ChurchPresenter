package org.churchpresenter.presentationengine.keynote

/**
 * Minimal dynamic protobuf wire-format reader. No protobuf-java, no codegen: field numbers are
 * looked up ad hoc (see [KnFields] for the vendored numbers with proto-source references), and
 * unknown fields are skipped — which is exactly what makes the Keynote parser tolerant of
 * schema drift across Keynote versions.
 */
internal class IwaMessage private constructor(
    private val fields: Map<Int, List<Value>>
) {

    internal sealed interface Value {
        data class Varint(val value: Long) : Value
        data class Fixed32(val bits: Int) : Value
        data class Fixed64(val bits: Long) : Value
        data class Bytes(val data: ByteArray) : Value
    }

    fun has(field: Int): Boolean = fields.containsKey(field)

    /** Present field numbers — diagnostic aid for schema-drift investigation (DumpKeynote). */
    fun fieldNumbers(): Set<Int> = fields.keys

    fun varint(field: Int): Long? = (fields[field]?.firstOrNull() as? Value.Varint)?.value

    fun varints(field: Int): List<Long> {
        val values = fields[field] ?: return emptyList()
        val result = mutableListOf<Long>()
        for (value in values) {
            when (value) {
                is Value.Varint -> result.add(value.value)
                // Packed repeated scalars arrive as one length-delimited blob.
                is Value.Bytes -> {
                    var pos = 0
                    while (pos < value.data.size) {
                        val (v, next) = readVarint(value.data, pos) ?: break
                        result.add(v)
                        pos = next
                    }
                }
                else -> {}
            }
        }
        return result
    }

    fun float(field: Int): Float? = (fields[field]?.firstOrNull() as? Value.Fixed32)
        ?.let { java.lang.Float.intBitsToFloat(it.bits) }

    fun double(field: Int): Double? {
        return when (val v = fields[field]?.firstOrNull()) {
            is Value.Fixed64 -> java.lang.Double.longBitsToDouble(v.bits)
            is Value.Fixed32 -> java.lang.Float.intBitsToFloat(v.bits).toDouble()
            else -> null
        }
    }

    fun string(field: Int): String? =
        (fields[field]?.firstOrNull() as? Value.Bytes)?.data?.toString(Charsets.UTF_8)

    fun strings(field: Int): List<String> =
        fields[field]?.filterIsInstance<Value.Bytes>()?.map { it.data.toString(Charsets.UTF_8) }
            ?: emptyList()

    fun bytes(field: Int): ByteArray? = (fields[field]?.firstOrNull() as? Value.Bytes)?.data

    fun messages(field: Int): List<IwaMessage> =
        fields[field]?.filterIsInstance<Value.Bytes>()?.mapNotNull { parse(it.data) } ?: emptyList()

    companion object {

        /** Parses [data]; returns null (never throws) on malformed input. */
        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): IwaMessage? {
            val fields = mutableMapOf<Int, MutableList<Value>>()
            var pos = offset
            val end = offset + length
            val complete = try {
                while (pos < end) {
                    val (fieldNumber, value, next) = readField(data, pos, end) ?: break
                    fields.getOrPut(fieldNumber) { mutableListOf() }.add(value)
                    pos = next
                }
                pos >= end
            } catch (_: Exception) {
                false
            }
            return if (complete) IwaMessage(fields) else null
        }

        /** The field at [pos] -- its number, its value and where the next begins -- or null when malformed. */
        private fun readField(data: ByteArray, pos: Int, end: Int): Triple<Int, Value, Int>? {
            val (tag, afterTag) = readVarint(data, pos) ?: return null
            val fieldNumber = (tag ushr TAG_FIELD_SHIFT).toInt()
            val read = if (fieldNumber == 0) null else readValue((tag and TAG_WIRE_MASK).toInt(), data, afterTag, end)
            return read?.let { (value, next) -> Triple(fieldNumber, value, next) }
        }

        /** One value of [wireType] starting at [pos], and where it ends; null when it runs past [end]. */
        private fun readValue(wireType: Int, data: ByteArray, pos: Int, end: Int): Pair<Value, Int>? =
            when (wireType) {
                WIRE_VARINT -> readVarint(data, pos)?.let { (v, next) -> Value.Varint(v) to next }
                WIRE_FIXED64 -> if (pos + FIXED64_BYTES > end) null
                    else Value.Fixed64(littleEndian(data, pos, FIXED64_BYTES)) to pos + FIXED64_BYTES
                WIRE_LENGTH_DELIMITED -> readVarint(data, pos)?.let { (len, start) ->
                    val size = len.toInt()
                    if (size < 0 || start + size > end) null
                    else Value.Bytes(data.copyOfRange(start, start + size)) to start + size
                }
                WIRE_FIXED32 -> if (pos + FIXED32_BYTES > end) null
                    else Value.Fixed32(littleEndian(data, pos, FIXED32_BYTES).toInt()) to pos + FIXED32_BYTES
                else -> null // group wire types are not used by iWork
            }

        /** [count] bytes at [pos] read as a little-endian integer. */
        private fun littleEndian(data: ByteArray, pos: Int, count: Int): Long {
            var bits = 0L
            for (i in count - 1 downTo 0) bits = (bits shl Byte.SIZE_BITS) or (data[pos + i].toLong() and BYTE_MASK)
            return bits
        }

        /** A field tag: the field number above three bits of wire type. */
        private const val TAG_FIELD_SHIFT = 3
        private const val TAG_WIRE_MASK = 0x7L

        /** The protobuf wire types iWork writes, and the widths of the fixed ones. */
        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_LENGTH_DELIMITED = 2
        private const val WIRE_FIXED32 = 5
        private const val FIXED64_BYTES = 8
        private const val FIXED32_BYTES = 4
        private const val BYTE_MASK = 0xFFL

        /** Protobuf base-128 varints: seven payload bits per byte, high bit = "another follows". */
        private const val VARINT_PAYLOAD_BITS = 7
        private const val VARINT_PAYLOAD_MASK = 0x7F
        private const val VARINT_CONTINUATION_BIT = 0x80

        /** A 64-bit value cannot need more shifting than this; past it the stream is corrupt. */
        private const val VARINT_MAX_SHIFT = 64

        /** Returns (value, nextOffset) or null on truncation. */
        fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var pos = offset
            while (pos < data.size && shift < VARINT_MAX_SHIFT) {
                val b = data[pos].toInt()
                result = result or ((b.toLong() and VARINT_PAYLOAD_MASK.toLong()) shl shift)
                pos++
                if (b and VARINT_CONTINUATION_BIT == 0) return result to pos
                shift += VARINT_PAYLOAD_BITS
            }
            return null
        }
    }
}

/** A varint field read as a flag: absent is null, zero is false, anything else true. */
internal fun IwaMessage.bool(field: Int): Boolean? = varint(field)?.let { it != 0L }

/** A length-delimited field parsed as a nested message; null when absent or malformed. */
internal fun IwaMessage.message(field: Int): IwaMessage? = bytes(field)?.let { IwaMessage.parse(it) }
