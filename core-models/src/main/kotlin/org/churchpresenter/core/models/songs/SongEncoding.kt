package org.churchpresenter.core.models.songs

import java.nio.charset.Charset

/** The bytes a UTF-8 byte-order mark is written as. */
private val UTF8_BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/** The code page Russian song files are written in when they are not UTF-8. */
private val CYRILLIC_ANSI: Charset = Charset.forName("windows-1251")

/**
 * Decodes [bytes] as UTF-8 without its byte-order mark, or as Windows-1251 when that is not what
 * they are.
 *
 * Neither format says which encoding it uses, so the decode is tried and then checked: a byte the
 * UTF-8 decoder could not make sense of arrives as U+FFFD, and one of those means the file is a
 * Cyrillic ANSI file rather than a UTF-8 one. The app's song library and the Converter read song
 * files through this one decode, so a song one of them opens the other does too.
 */
fun decodeUtf8OrCyrillic(bytes: ByteArray): String {
    val hasMark = bytes.size >= UTF8_BYTE_ORDER_MARK.size &&
        UTF8_BYTE_ORDER_MARK.indices.all { bytes[it] == UTF8_BYTE_ORDER_MARK[it] }
    val content = if (hasMark) {
        String(bytes, UTF8_BYTE_ORDER_MARK.size, bytes.size - UTF8_BYTE_ORDER_MARK.size, Charsets.UTF_8)
    } else {
        String(bytes, Charsets.UTF_8)
    }
    return if (content.contains('�')) String(bytes, CYRILLIC_ANSI) else content
}
