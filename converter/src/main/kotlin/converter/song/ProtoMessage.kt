package converter.song

/**
 * Just enough of the protocol-buffer wire format to walk a ProPresenter 7 document.
 *
 * The wire format carries field *numbers*, not names, so a reader only needs the numbers along the
 * one path it cares about — which is why this is a few dozen lines instead of a code-generated
 * library and a hundred `.proto` files. Everything off that path is skipped without being
 * understood, and an unknown field is not an error: ProPresenter adds fields between releases, and
 * a reader that insisted on knowing all of them would break on every update.
 *
 * The numbers themselves are in `ProPresenterConverter`, next to the path they describe.
 */
internal class ProtoMessage private constructor(private val entries: Map<Int, List<Entry>>) {

    private sealed interface Entry {
        /** Wire type 2 — a length-delimited run of bytes: a string, bytes, or a nested message. */
        data class Bytes(val value: ByteArray) : Entry

        /** Wire types 0, 1 and 5 — a number. Only varints are ever read back here. */
        data class Number(val value: Long) : Entry
    }

    /** The length-delimited values of [field], in the order they appeared. */
    fun bytes(field: Int): List<ByteArray> =
        entries[field].orEmpty().filterIsInstance<Entry.Bytes>().map { it.value }

    /** [field] read as a nested message, for every occurrence — a `repeated` field gives many. */
    fun messages(field: Int): List<ProtoMessage> = bytes(field).mapNotNull { parseOrNull(it) }

    /** The first occurrence of [field] as a nested message, or null when it is absent. */
    fun message(field: Int): ProtoMessage? = messages(field).firstOrNull()

    /** The first occurrence of [field] as a UTF-8 string, or null. */
    fun string(field: Int): String? = bytes(field).firstOrNull()?.toString(Charsets.UTF_8)

    /** The first occurrence of [field] as a varint, or null. */
    fun number(field: Int): Long? =
        entries[field].orEmpty().filterIsInstance<Entry.Number>().firstOrNull()?.value

    companion object {
        private const val VARINT = 0
        private const val FIXED_64 = 1
        private const val LENGTH_DELIMITED = 2
        private const val START_GROUP = 3
        private const val END_GROUP = 4
        private const val FIXED_32 = 5

        private const val CONTINUATION_BIT = 0x80
        private const val PAYLOAD_MASK = 0x7f
        private const val PAYLOAD_BITS = 7
        private const val WIRE_TYPE_MASK = 7
        private const val FIELD_NUMBER_SHIFT = 3

        /** Parses [bytes] as a message, or returns null when it is not one. */
        fun parseOrNull(bytes: ByteArray): ProtoMessage? = runCatching { parse(bytes) }.getOrNull()

        fun parse(bytes: ByteArray): ProtoMessage {
            val entries = HashMap<Int, MutableList<Entry>>()
            var index = 0
            while (index < bytes.size) {
                val (key, afterKey) = varint(bytes, index)
                index = afterKey
                val field = (key ushr FIELD_NUMBER_SHIFT).toInt()
                require(field != 0) { "Field number 0 is not valid" }
                when ((key and WIRE_TYPE_MASK.toLong()).toInt()) {
                    VARINT -> {
                        val (value, next) = varint(bytes, index)
                        index = next
                        entries.getOrPut(field) { mutableListOf() }.add(Entry.Number(value))
                    }
                    FIXED_64 -> index = within(index + Long.SIZE_BYTES, bytes.size)
                    LENGTH_DELIMITED -> {
                        val (length, afterLength) = varint(bytes, index)
                        val end = within(afterLength + length.toInt(), bytes.size)
                        entries.getOrPut(field) { mutableListOf() }
                            .add(Entry.Bytes(bytes.copyOfRange(afterLength, end)))
                        index = end
                    }
                    FIXED_32 -> index = within(index + Int.SIZE_BYTES, bytes.size)
                    // Groups were removed in proto3 and ProPresenter does not emit them.
                    START_GROUP, END_GROUP -> throw IllegalArgumentException("Groups are not supported")
                    else -> throw IllegalArgumentException("Unknown wire type")
                }
            }
            return ProtoMessage(entries)
        }

        /** [end], once it is known to be inside a message of [size] bytes. */
        private fun within(end: Int, size: Int): Int {
            require(end in 0..size) { "Truncated message" }
            return end
        }

        /** A base-128 varint and the index just past it. */
        private fun varint(bytes: ByteArray, start: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var index = start
            while (index < bytes.size) {
                val byte = bytes[index].toInt()
                index++
                result = result or ((byte and PAYLOAD_MASK).toLong() shl shift)
                if (byte and CONTINUATION_BIT == 0) return result to index
                shift += PAYLOAD_BITS
                require(shift < Long.SIZE_BITS) { "Varint too long" }
            }
            throw IllegalArgumentException("Truncated varint")
        }
    }
}
