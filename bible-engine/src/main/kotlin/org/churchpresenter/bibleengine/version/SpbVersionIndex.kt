package org.churchpresenter.bibleengine.version

import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.bible.SpbLoader
import org.churchpresenter.bibleengine.engine.AgreementScorer
import java.io.File
import java.io.RandomAccessFile
import org.churchpresenter.bibleengine.bible.EngineTranslation

/** `BbbbCcccVvvv`: the length of a verse code, and where its book, chapter and verse fields sit. */
private const val CODE_LENGTH = 12

/** A packed code is `book * BOOK_STEP + chapter * CHAPTER_STEP + verse`. */
private const val BOOK_STEP = 1_000_000
private const val CHAPTER_STEP = 1_000

/** Verse rows are `code \t book \t chapter \t verse \t text`: the text follows the fourth tab. */
private const val TEXT_FIELD_TABS = 4

/** The script sniff stops counting letters once it has seen this many. */
private const val SCRIPT_SNIFF_LETTERS = 20_000

/** UTF-8 lead bytes of the Cyrillic block. */
private const val CYRILLIC_LEAD_LOW = 0xD0
private const val CYRILLIC_LEAD_HIGH = 0xD1
private const val UNSIGNED_BYTE_MASK = 0xFF

/** Fewer verses than this is not a bible. */
private const val MIN_VERSES = 10

private const val INITIAL_CAPACITY = 32_000
private val NEWLINE = '\n'.code.toByte()
private val CARRIAGE_RETURN = '\r'.code.toByte()
private val TAB = '\t'.code.toByte()
private val CODE_START = 'B'.code.toByte()

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
    private val stamp: FileStamp,
    private val spans: VerseSpans,
) {

    /** The file the index was built from, as it was then. */
    private class FileStamp(val file: File, val length: Long, val lastModified: Long) {
        /** The index is only valid for the bytes it was built from. */
        fun unchanged(): Boolean = file.length() == length && file.lastModified() == lastModified
    }

    /** Packed book/chapter/verse keys, ascending — binary-searched by [text] — and each verse's byte span. */
    private class VerseSpans(val codes: IntArray, val textOffsets: LongArray, val textLengths: IntArray)

    /** Verse text for a packed code, or null when absent — or when the file changed underneath us. */
    fun text(packedCode: Int): String? {
        val i = spans.codes.binarySearch(packedCode)
        // Rather than serve garbage from a file that has been re-exported or replaced since startup,
        // refuse.
        val readable = i >= 0 && stamp.unchanged() && spans.textLengths[i] > 0
        return if (readable) read(i) else null
    }

    private fun read(i: Int): String? = runCatching {
        RandomAccessFile(stamp.file, "r").use { raf ->
            raf.seek(spans.textOffsets[i])
            val buf = ByteArray(spans.textLengths[i])
            raf.readFully(buf)
            String(buf, Charsets.UTF_8)
        }
    }.getOrNull()

    /** Token fingerprint over a fixed sample of verses — the basis for near-duplicate collapse. */
    fun sampleTokens(sample: IntArray): Set<String> {
        val out = HashSet<String>()
        for (c in sample) text(c)?.let { out += AgreementScorer.tokens(it) }
        return out
    }

    /** Every packed code this file holds, ascending. */
    fun codes(): IntArray = spans.codes

    /** What a byte-wise scan of one file collects, line by line. */
    private class IndexScan {
        var abbreviation = ""
        private var pastSeparator = false
        val codes = ArrayList<Int>(INITIAL_CAPACITY)
        val offsets = ArrayList<Long>(INITIAL_CAPACITY)
        val lengths = ArrayList<Int>(INITIAL_CAPACITY)
        private var latin = 0
        private var cyrillic = 0

        val script: Script
            get() = when {
                cyrillic > latin -> Script.CYRILLIC
                latin > 0 -> Script.LATIN
                else -> Script.OTHER
            }

        fun line(bytes: ByteArray, lineStart: Int, lineEnd: Int) {
            if (!pastSeparator) {
                val line = String(bytes, lineStart, lineEnd - lineStart, Charsets.UTF_8)
                when {
                    line.startsWith("##Abbreviation:") ->
                        abbreviation = line.removePrefix("##Abbreviation:").trim()
                    line.trimEnd() == "-----" -> pastSeparator = true
                }
            } else if (lineEnd > lineStart && bytes[lineStart] == CODE_START) {
                verseRow(bytes, lineStart, lineEnd)
            }
        }

        // Verse row: code \t book \t chapter \t verse \t text. Only the code and the byte span of
        // field 5 are kept; the text itself stays on disk.
        private fun verseRow(bytes: ByteArray, lineStart: Int, lineEnd: Int) {
            val textStart = textFieldStart(bytes, lineStart, lineEnd)
            if (textStart !in 1 until lineEnd) return
            val code = String(bytes, lineStart, CODE_LENGTH.coerceAtMost(lineEnd - lineStart), Charsets.UTF_8)
            val packed = packCode(code)
            // verse == 0 rows are section headers, not verses — packed % 1000 == 0.
            if (packed == null || packed % CHAPTER_STEP == 0) return
            codes.add(packed)
            offsets.add(textStart.toLong())
            lengths.add(lineEnd - textStart)
            if (cyrillic + latin < SCRIPT_SNIFF_LETTERS) sniffScript(bytes, textStart, lineEnd)
        }

        private fun sniffScript(bytes: ByteArray, from: Int, to: Int) {
            for (q in from until to) {
                // Byte-level script sniff: ASCII letters vs the UTF-8 lead bytes of the Cyrillic block
                // (0xD0/0xD1). Mask to unsigned — a Kotlin Byte is signed, so 0xD0 reads back as -48.
                val ch = bytes[q].toInt() and UNSIGNED_BYTE_MASK
                if ((ch in 'A'.code..'Z'.code) || (ch in 'a'.code..'z'.code)) latin++
                else if (ch == CYRILLIC_LEAD_LOW || ch == CYRILLIC_LEAD_HIGH) cyrillic++
            }
        }
    }

    companion object {

        /** `BbbbCcccVvvv` -> a sortable int. Null when the code isn't that shape. */
        fun packCode(code: String): Int? {
            if (code.length < CODE_LENGTH || code[0] != 'B') return null
            val b = code.substring(1, 4).toIntOrNull() ?: return null
            val c = code.substring(5, 8).toIntOrNull() ?: return null
            val v = code.substring(9, CODE_LENGTH).toIntOrNull() ?: return null
            return b * BOOK_STEP + c * CHAPTER_STEP + v
        }

        /**
         * Scans [file] byte-wise and builds its index, or returns null when it isn't a usable module
         * (no `##Abbreviation:` header, or too few verses to be a bible).
         */
        fun build(file: File, seenIds: MutableMap<String, Int>): SpbVersionIndex? {
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            val scan = IndexScan()
            forEachLine(bytes) { lineStart, lineEnd -> scan.line(bytes, lineStart, lineEnd) }

            if (scan.abbreviation.isBlank() || scan.codes.size < MIN_VERSES) return null
            // Rows arrive in file order, which is canonical order in every module seen — but the
            // binary search in `text` requires it, so make it true rather than assume it.
            val order = scan.codes.indices.sortedBy { scan.codes[it] }
            return SpbVersionIndex(
                id = SpbLoader.deriveId(file.name, scan.abbreviation, seenIds),
                label = scan.abbreviation,
                script = scan.script,
                fileName = file.name,
                stamp = FileStamp(file, file.length(), file.lastModified()),
                spans = VerseSpans(
                    codes = IntArray(order.size) { scan.codes[order[it]] },
                    textOffsets = LongArray(order.size) { scan.offsets[order[it]] },
                    textLengths = IntArray(order.size) { scan.lengths[order[it]] },
                ),
            )
        }

        /** Each line of [bytes] as its start and its end, a trailing `\r` excluded, the last unterminated one too. */
        private inline fun forEachLine(bytes: ByteArray, action: (lineStart: Int, lineEnd: Int) -> Unit) {
            var lineStart = 0
            for (i in 0..bytes.size) {
                if (i == bytes.size || bytes[i] == NEWLINE) {
                    var lineEnd = i
                    if (lineEnd > lineStart && bytes[lineEnd - 1] == CARRIAGE_RETURN) lineEnd--
                    action(lineStart, lineEnd)
                    lineStart = i + 1
                }
            }
        }

        /** Where the text field of the row in `bytes[from, to)` starts, or -1 when it has too few fields. */
        private fun textFieldStart(bytes: ByteArray, from: Int, to: Int): Int {
            var tabs = 0
            for (p in from until to) {
                if (bytes[p] == TAB) {
                    tabs++
                    if (tabs == TEXT_FIELD_TABS) return p + 1
                }
            }
            return -1
        }
    }
}
