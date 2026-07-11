package presentation.engine.keynote

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

    fun bool(field: Int): Boolean? = varint(field)?.let { it != 0L }

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

    fun message(field: Int): IwaMessage? = bytes(field)?.let { parse(it) }

    fun messages(field: Int): List<IwaMessage> =
        fields[field]?.filterIsInstance<Value.Bytes>()?.mapNotNull { parse(it.data) } ?: emptyList()

    companion object {

        /** Parses [data]; returns null (never throws) on malformed input. */
        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): IwaMessage? {
            val fields = mutableMapOf<Int, MutableList<Value>>()
            var pos = offset
            val end = offset + length
            try {
                while (pos < end) {
                    val (tag, afterTag) = readVarint(data, pos) ?: return null
                    pos = afterTag
                    val fieldNumber = (tag ushr 3).toInt()
                    if (fieldNumber == 0) return null
                    val value: Value = when ((tag and 0x7).toInt()) {
                        0 -> {
                            val (v, next) = readVarint(data, pos) ?: return null
                            pos = next
                            Value.Varint(v)
                        }
                        1 -> {
                            if (pos + 8 > end) return null
                            var bits = 0L
                            for (i in 7 downTo 0) bits = (bits shl 8) or (data[pos + i].toLong() and 0xFF)
                            pos += 8
                            Value.Fixed64(bits)
                        }
                        2 -> {
                            val (len, next) = readVarint(data, pos) ?: return null
                            pos = next
                            val size = len.toInt()
                            if (size < 0 || pos + size > end) return null
                            Value.Bytes(data.copyOfRange(pos, pos + size))
                                .also { pos += size }
                        }
                        5 -> {
                            if (pos + 4 > end) return null
                            var bits = 0
                            for (i in 3 downTo 0) bits = (bits shl 8) or (data[pos + i].toInt() and 0xFF)
                            pos += 4
                            Value.Fixed32(bits)
                        }
                        else -> return null // group wire types are not used by iWork
                    }
                    fields.getOrPut(fieldNumber) { mutableListOf() }.add(value)
                }
            } catch (_: Exception) {
                return null
            }
            return IwaMessage(fields)
        }

        /** Returns (value, nextOffset) or null on truncation. */
        fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var pos = offset
            while (pos < data.size && shift < 64) {
                val b = data[pos].toInt()
                result = result or ((b.toLong() and 0x7F) shl shift)
                pos++
                if (b and 0x80 == 0) return result to pos
                shift += 7
            }
            return null
        }
    }
}
