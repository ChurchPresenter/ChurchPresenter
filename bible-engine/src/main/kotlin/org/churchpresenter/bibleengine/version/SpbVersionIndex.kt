package org.churchpresenter.bibleengine.version

import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.bible.SpbLoader
import org.churchpresenter.bibleengine.engine.AgreementScorer
import java.io.File
import java.io.RandomAccessFile
import org.churchpresenter.bibleengine.bible.EngineTranslation

/**
 * A seek index over one `.spb` file: where each verse's text starts and how many bytes long it is.
 *
 * Version detection needs ONE verse at a time from every bible, a few times a minute — never the
 * whole text. Holding parsed [EngineTranslation]s for the whole folder would cost
 * 20-30 MB of heap each (300-500 MB for a large folder, inside a Compose desktop app); two IntArrays
 * cost ~250 KB, and a page-cached seek is microseconds.
 *
 * Offsets are BYTE offsets, taken from a byte-level scan — the Cyrillic modules are multibyte, so a
 * character-based scan would land mid-word.
 */
class SpbVersionIndex private constructor(
    val id: String,
    val label: String,
    val script: Script,
    val fileName: String,
    private val file: File,
    private val fileLength: Long,
    private val lastModified: Long,
    /** Packed book/chapter/verse keys, ascending — binary-searched by [text]. */
    private val codes: IntArray,
    private val textOffsets: LongArray,
    private val textLengths: IntArray,
) {

    /** Verse text for a packed code, or null when absent — or when the file changed underneath us. */
    fun text(packedCode: Int): String? {
        val i = codes.binarySearch(packedCode)
        if (i < 0) return null
        // The index is only valid for the bytes it was built from. Rather than serve garbage from a
        // file that has been re-exported or replaced since startup, refuse.
        if (file.length() != fileLength || file.lastModified() != lastModified) return null
        val len = textLengths[i]
        if (len <= 0) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(textOffsets[i])
                val buf = ByteArray(len)
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /** Token fingerprint over a fixed sample of verses — the basis for near-duplicate collapse. */
    fun sampleTokens(sample: IntArray): Set<String> {
        val out = HashSet<String>()
        for (c in sample) text(c)?.let { out += AgreementScorer.tokens(it) }
        return out
    }

    /** Every packed code this file holds, ascending. */
    fun codes(): IntArray = codes

    companion object {

        /** `BbbbCcccVvvv` -> a sortable int. Null when the code isn't that shape. */
        fun packCode(code: String): Int? {
            if (code.length < 12 || code[0] != 'B') return null
            val b = code.substring(1, 4).toIntOrNull() ?: return null
            val c = code.substring(5, 8).toIntOrNull() ?: return null
            val v = code.substring(9, 12).toIntOrNull() ?: return null
            return b * 1_000_000 + c * 1_000 + v
        }

        /**
         * Scans [file] byte-wise and builds its index, or returns null when it isn't a usable module
         * (no `##Abbreviation:` header, or too few verses to be a bible).
         */
        fun build(file: File, seenIds: MutableMap<String, Int>): SpbVersionIndex? {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            var abbreviation = ""
            var pastSeparator = false
            val codes = ArrayList<Int>(32_000)
            val offsets = ArrayList<Long>(32_000)
            val lengths = ArrayList<Int>(32_000)
            var latin = 0
            var cyrillic = 0

            var lineStart = 0
            var i = 0
            while (i <= bytes.size) {
                if (i < bytes.size && bytes[i] != '\n'.code.toByte()) { i++; continue }
                var lineEnd = i
                if (lineEnd > lineStart && bytes[lineEnd - 1] == '\r'.code.toByte()) lineEnd--

                if (!pastSeparator) {
                    val line = String(bytes, lineStart, lineEnd - lineStart, Charsets.UTF_8)
                    when {
                        line.startsWith("##Abbreviation:") ->
                            abbreviation = line.removePrefix("##Abbreviation:").trim()
                        line.trimEnd() == "-----" -> pastSeparator = true
                    }
                } else if (lineEnd > lineStart && bytes[lineStart] == 'B'.code.toByte()) {
                    // Verse row: code \t book \t chapter \t verse \t text. Only the code and the byte
                    // span of field 5 are kept; the text itself stays on disk.
                    var tabs = 0
                    var p = lineStart
                    var textStart = -1
                    while (p < lineEnd) {
                        if (bytes[p] == '\t'.code.toByte()) {
                            tabs++
                            if (tabs == 4) { textStart = p + 1; break }
                        }
                        p++
                    }
                    if (textStart in 1 until lineEnd) {
                        val code = String(bytes, lineStart, 12.coerceAtMost(lineEnd - lineStart), Charsets.UTF_8)
                        val packed = packCode(code)
                        // verse == 0 rows are section headers, not verses — packed % 1000 == 0.
                        if (packed != null && packed % 1_000 != 0) {
                            codes.add(packed)
                            offsets.add(textStart.toLong())
                            lengths.add(lineEnd - textStart)
                            if (cyrillic + latin < 20_000) {
                                for (q in textStart until lineEnd) {
                                    // Byte-level script sniff: ASCII letters vs the UTF-8 lead bytes
                                    // of the Cyrillic block (0xD0/0xD1). Mask to unsigned — a Kotlin
                                    // Byte is signed, so 0xD0 reads back as -48.
                                    val ch = bytes[q].toInt() and 0xFF
                                    if ((ch in 65..90) || (ch in 97..122)) latin++
                                    else if (ch == 0xD0 || ch == 0xD1) cyrillic++
                                }
                            }
                        }
                    }
                }
                i++
                lineStart = i
            }

            if (abbreviation.isBlank() || codes.size < 10) return null
            // Rows arrive in file order, which is canonical order in every module seen — but the
            // binary search in `text` requires it, so make it true rather than assume it.
            val order = codes.indices.sortedBy { codes[it] }
            val script = when {
                cyrillic > latin -> Script.CYRILLIC
                latin > 0 -> Script.LATIN
                else -> Script.OTHER
            }
            return SpbVersionIndex(
                id = SpbLoader.deriveId(file.name, abbreviation, seenIds),
                label = abbreviation,
                script = script,
                fileName = file.name,
                file = file,
                fileLength = file.length(),
                lastModified = file.lastModified(),
                codes = IntArray(order.size) { codes[order[it]] },
                textOffsets = LongArray(order.size) { offsets[order[it]] },
                textLengths = IntArray(order.size) { lengths[order[it]] },
            )
        }
    }
}
