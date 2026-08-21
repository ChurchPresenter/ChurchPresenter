package org.churchpresenter.converter.library

import java.io.File
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
 * Cyrillic ANSI file rather than a UTF-8 one.
 */
internal fun decodeUtf8OrCyrillic(bytes: ByteArray): String {
    val hasMark = bytes.size >= UTF8_BYTE_ORDER_MARK.size &&
        UTF8_BYTE_ORDER_MARK.indices.all { bytes[it] == UTF8_BYTE_ORDER_MARK[it] }
    val content = if (hasMark) {
        String(bytes, UTF8_BYTE_ORDER_MARK.size, bytes.size - UTF8_BYTE_ORDER_MARK.size, Charsets.UTF_8)
    } else {
        String(bytes, Charsets.UTF_8)
    }
    return if (content.contains('\uFFFD')) String(bytes, CYRILLIC_ANSI) else content
}

object TextUtils {
    /**
     * Sanitize lyrics text by replacing control characters and trimming whitespace.
     * Fixes vertical tabs (used instead of newlines in some SPS sources) and null bytes.
     */
    fun sanitizeLyricText(text: String): String {
        return text
            .replace('\u000B', '\n')           // vertical tab → newline
            .replace("\u0000", "")              // strip null bytes
            .lines()
            .joinToString("\n") { it.trimEnd() } // trim trailing whitespace per line
    }

    /** Find .song files that contain null bytes or vertical tabs. */
    fun findFilesWithControlChars(directory: File): List<File> {
        return directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("song", ignoreCase = true) }
            .filter { file ->
                val bytes = file.readBytes()
                bytes.any { it == 0x00.toByte() || it == 0x0B.toByte() }
            }
            .toList()
    }

    /** Sanitize a .song file in-place. Returns true if the file was modified. */
    fun sanitizeFile(file: File): Boolean {
        val original = file.readText(Charsets.UTF_8)
        val sanitized = sanitizeLyricText(original)
        if (sanitized == original) return false
        file.writeText(sanitized, Charsets.UTF_8)
        return true
    }
}
