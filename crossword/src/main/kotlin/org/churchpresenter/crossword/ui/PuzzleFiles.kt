package org.churchpresenter.crossword.ui

import java.io.File
import org.churchpresenter.crossword.data.ClueEntry
import org.churchpresenter.crossword.data.CrosswordEngine
import org.churchpresenter.crossword.data.decode
import org.churchpresenter.crossword.data.encode
import org.churchpresenter.crossword.data.fromPlaintext
import org.churchpresenter.crossword.data.toPlaintext

// Reading the puzzle folder, and the two batch operations that run over everything in it.

/** Where the tool reads and writes plaintext puzzles, and where it puts the encoded ones. */
internal val PUZZLES_DIR = File("puzzles")
internal val ENCODED_DIR = File("encoded")

/** The section that pins each answer to a cell, so the presenter lays the grid out as previewed. */
internal const val LAYOUT_MARKER = "LAYOUT:"

/** The encoded file name the tool writes and reads back. */
private val ENCODED_NAME = Regex("""level(\d+)\.xwp""")

internal fun detectLevels(): List<Int> =
    PUZZLES_DIR.listFiles { f -> f.name.matches(Regex("level(\\d+)\\.txt")) }
        ?.mapNotNull { Regex("level(\\d+)\\.txt").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        ?.sorted() ?: emptyList()

internal fun nextNewLevel(levels: List<Int>): Int =
    (levels.maxOrNull()?.plus(1)) ?: 0

private fun txtNewerThanXwp(level: Int): Boolean {
    val txt = File(PUZZLES_DIR, "level$level.txt")
    val xwp = File(ENCODED_DIR, "level$level.xwp")
    if (!txt.exists()) return false
    if (!xwp.exists()) return true
    // A newer timestamp on its own means nothing — a save that changed no text still moves it — so
    // the warning is only raised when the two really differ.
    return txt.lastModified() > xwp.lastModified() && contentDiffers(txt, xwp)
}

private fun contentDiffers(txt: File, xwp: File): Boolean = try {
    txt.readText(Charsets.UTF_8).trim() != decode(xwp.readText(Charsets.UTF_8)).trim()
} catch (_: Exception) {
    // Unreadable or undecodable: treat as differing, so it is flagged rather than passed over.
    true
}

internal fun scanUnexported(levels: List<Int>): Set<Int> =
    levels.filter { txtNewerThanXwp(it) }.toSet()

internal fun scanTemplateLevels(levels: List<Int>): Set<Int> =
    levels.filter { level ->
        val file = File(PUZZLES_DIR, "level$level.txt")
        file.exists() && try { isUnedited(file.readText(Charsets.UTF_8)) } catch (_: Exception) { false }
    }.toSet()

/** Encodes every plaintext level found, and reports how many went out and how many were skipped. */
internal fun exportAllLevels(): String {
    val all = detectLevels()
    if (all.isEmpty()) return "No plaintext levels found in puzzles/"
    ENCODED_DIR.mkdirs()
    val exported = all.count { exportOne(it) }
    val skipped = all.size - exported
    return "Exported $exported level(s)" + if (skipped > 0) ", $skipped skipped (invalid/unplaceable)" else ""
}

private fun exportOne(level: Int): Boolean {
    val file = File(PUZZLES_DIR, "level$level.txt")
    val text = file.readText(Charsets.UTF_8)
    val parsed = fromPlaintext(text)
    if (parsed == null || isUnedited(text)) return false
    val out = injectedLayout(file, text, parsed.second, parsed.third) ?: return false
    File(ENCODED_DIR, "level$level.xwp").writeText(encode(out), Charsets.UTF_8)
    return true
}

private fun injectedLayout(file: File, text: String, title: String, clues: List<ClueEntry>): String? {
    if (text.contains(LAYOUT_MARKER, ignoreCase = true)) return text
    val puzzle = CrosswordEngine.build(clues) ?: return null
    val enriched = toPlaintext(title, clues, puzzle.placedPositions, puzzle.placedDirections)
    file.writeText(enriched, Charsets.UTF_8)
    return enriched
}

/** Decodes every `.xwp` back to plaintext, and reports how many came back. */
internal fun decodeAllLevels(): String {
    val files = ENCODED_DIR.listFiles { f -> f.name.matches(ENCODED_NAME) }.orEmpty()
    if (files.isEmpty()) return "No .xwp files found in encoded/"
    PUZZLES_DIR.mkdirs()
    val decoded = files.count { file ->
        runCatching {
            val level = ENCODED_NAME.matchEntire(file.name)!!.groupValues[1]
            File(PUZZLES_DIR, "level$level.txt")
                .writeText(decode(file.readText(Charsets.UTF_8)), Charsets.UTF_8)
        }.isSuccess
    }
    val failed = files.size - decoded
    return "Decoded $decoded level(s)" + if (failed > 0) ", $failed failed" else ""
}
