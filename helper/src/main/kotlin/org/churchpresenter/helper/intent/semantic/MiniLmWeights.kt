package org.churchpresenter.helper.intent.semantic

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A weight matrix stored a row at a time as int8, each row with its own scale: `row r, column c` is
 * `data[offset + r * cols + c] * scales[r]`. It stays in the file's bytes; nothing is expanded to floats.
 */
internal class Int8Rows(
    val rows: Int,
    val cols: Int,
    val scales: FloatArray,
    private val data: ByteArray,
    private val offset: Int,
) {
    /** Row [row] as floats, into [out] from [outOffset] — an embedding lookup, or a row a layer reuses. */
    fun copyRow(row: Int, out: FloatArray, outOffset: Int) {
        val scale = scales[row]
        var at = offset + row * cols
        for (c in 0 until cols) {
            out[outOffset + c] = data[at++] * scale
        }
    }
}

/** The tensors of `wick/minilm-l6.bin`, by name — see `helper/tools/export_minilm.py` for the format. */
internal class MiniLmWeights private constructor(
    private val floats: Map<String, FloatArray>,
    private val matrices: Map<String, Int8Rows>,
) {
    fun floats(name: String): FloatArray = floats[name] ?: error("model has no fp32 tensor $name")
    fun rows(name: String): Int8Rows = matrices[name] ?: error("model has no int8 tensor $name")

    companion object {
        private const val MAGIC = "WICKML01"
        private const val KIND_FP32 = 0
        private const val KIND_INT8_ROWS = 1

        fun read(stream: InputStream): MiniLmWeights = parse(stream.use { it.readBytes() })

        fun parse(bytes: ByteArray): MiniLmWeights {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(MAGIC.length).also { buffer.get(it) }
            require(String(magic) == MAGIC) { "not a Wick model file" }
            val floats = HashMap<String, FloatArray>()
            val matrices = HashMap<String, Int8Rows>()
            repeat(buffer.int) {
                val name = ByteArray(buffer.short.toInt()).also { buffer.get(it) }.decodeToString()
                val kind = buffer.get().toInt()
                val dims = IntArray(buffer.get().toInt()) { buffer.int }
                val count = dims.fold(1) { a, b -> a * b }
                when (kind) {
                    KIND_FP32 -> floats[name] = FloatArray(count).also { buffer.asFloatBuffer().get(it) }
                        .also { buffer.position(buffer.position() + count * Float.SIZE_BYTES) }
                    KIND_INT8_ROWS -> {
                        val scales = FloatArray(dims[0]).also { buffer.asFloatBuffer().get(it) }
                        buffer.position(buffer.position() + dims[0] * Float.SIZE_BYTES)
                        matrices[name] = Int8Rows(dims[0], dims[1], scales, bytes, buffer.position())
                        buffer.position(buffer.position() + count)
                    }
                    else -> error("unknown tensor kind $kind in $name")
                }
            }
            return MiniLmWeights(floats, matrices)
        }
    }
}
