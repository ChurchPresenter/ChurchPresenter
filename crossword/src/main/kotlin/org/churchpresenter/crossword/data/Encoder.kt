package org.churchpresenter.crossword.data

import java.util.Base64

private const val XOR_KEY = "CHURCHPRESENTER"

fun encode(text: String): String {
    val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
    val input = text.toByteArray(Charsets.UTF_8)
    val xored = ByteArray(input.size) { i -> (input[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
    return Base64.getEncoder().encodeToString(xored)
}

fun decode(encoded: String): String {
    val keyBytes = XOR_KEY.toByteArray(Charsets.UTF_8)
    val xored = Base64.getDecoder().decode(encoded)
    val output = ByteArray(xored.size) { i -> (xored[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
    return String(output, Charsets.UTF_8)
}

fun toPlaintext(
    title: String,
    clues: List<ClueEntry>,
    layout: Map<Int, Pair<Int, Int>>? = null,          // clue number → normalized (row, col)
    placedDirections: Map<Int, Direction>? = null       // actual placed direction (may differ from clue.direction)
): String {
    val sb = StringBuilder()
    sb.appendLine("# $title")
    val across = clues.filter { it.direction == Direction.ACROSS }
    val down = clues.filter { it.direction == Direction.DOWN }
    if (across.isNotEmpty()) {
        sb.appendLine("ACROSS:")
        across.forEach { sb.appendLine("${it.number}. ${it.clue} | ${it.answer}") }
    }
    if (down.isNotEmpty()) {
        sb.appendLine("DOWN:")
        down.forEach { sb.appendLine("${it.number}. ${it.clue} | ${it.answer}") }
    }
    if (layout != null) {
        sb.appendLine("LAYOUT:")
        clues.forEach { clue ->
            val pos = layout[clue.number] ?: return@forEach
            val dir = placedDirections?.get(clue.number) ?: clue.direction
            sb.appendLine("${clue.number} ${dir.name} ${pos.first} ${pos.second}")
        }
    }
    return sb.toString().trimEnd()
}

/** Parses the simplified format: bare "clue text | ANSWER" lines, no header, no sections, no numbers. */
fun fromPlaintextSimple(text: String): List<ClueEntry>? {
    val clueRegex = Regex("""^(.+?)\s*\|\s*(\S+)$""")
    val clues = text.lines()
        .map { it.trim() }
        .filter {
            it.isNotEmpty() && !it.startsWith("#") &&
                !it.equals("ACROSS:", ignoreCase = true) && !it.equals("DOWN:", ignoreCase = true)
        }
        .filter { !it.matches(Regex("""^\d+\..+\|.+""")) }
        .mapIndexedNotNull { idx, line ->
            val m = clueRegex.matchEntire(line) ?: return@mapIndexedNotNull null
            ClueEntry(
                number = idx + 1,
                direction = Direction.ACROSS,
                clue = m.groupValues[1].trim(),
                answer = m.groupValues[2].trim().uppercase(),
            )
        }
    return if (clues.isEmpty()) null else clues
}

fun fromPlaintext(text: String): Triple<Int, String, List<ClueEntry>>? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    val header = lines.first()
    if (!header.startsWith("#")) return null
    val level = 1
    val title = header.removePrefix("#").trim()
    val clues = mutableListOf<ClueEntry>()
    var currentDir = Direction.ACROSS
    for (line in lines.drop(1)) {
        when {
            line.equals("ACROSS:", ignoreCase = true) -> currentDir = Direction.ACROSS
            line.equals("DOWN:", ignoreCase = true) -> currentDir = Direction.DOWN
            else -> {
                val m = Regex("""^(\d+)\.\s*(.+?)\s*\|\s*(\S+)$""").matchEntire(line) ?: continue
                clues.add(ClueEntry(
                    number = m.groupValues[1].toInt(),
                    direction = currentDir,
                    clue = m.groupValues[2].trim(),
                    answer = m.groupValues[3].trim().uppercase()
                ))
            }
        }
    }
    return Triple(level, title, clues)
}
