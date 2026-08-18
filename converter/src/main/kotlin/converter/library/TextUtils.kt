package converter.library

import java.io.File

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
