package org.churchpresenter.converter.song

import java.io.ByteArrayOutputStream

internal object ProtoWriter {

    fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var rest = value
        while (true) {
            if (rest and 0x7fL.inv() == 0L) {
                out.write(rest.toInt())
                return out.toByteArray()
            }
            out.write(((rest and 0x7f) or 0x80).toInt())
            rest = rest ushr 7
        }
    }

    fun key(field: Int, wireType: Int): ByteArray = varint((field.toLong() shl 3) or wireType.toLong())

    fun number(field: Int, value: Long): ByteArray = key(field, 0) + varint(value)

    fun bytes(field: Int, value: ByteArray): ByteArray = key(field, 2) + varint(value.size.toLong()) + value

    fun string(field: Int, value: String): ByteArray = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, vararg parts: ByteArray): ByteArray = bytes(field, concat(*parts))

    fun concat(vararg parts: ByteArray): ByteArray = parts.fold(ByteArray(0)) { acc, part -> acc + part }
}
