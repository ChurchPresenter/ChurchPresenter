package org.churchpresenter.helper.intent.semantic

import java.text.Normalizer

/**
 * BERT's uncased WordPiece tokenizer, as `all-MiniLM-L6-v2` was trained with: control characters
 * dropped, CJK characters split apart, accents stripped, lower case, split at whitespace and
 * punctuation, then each word cut into the longest pieces [vocab] knows (`##` marks a continuation).
 * A word with no such cut becomes `[UNK]`. The result is wrapped in `[CLS]` … `[SEP]` and capped at
 * [maxTokens], the same as the reference tokenizer the model's fixtures were made with.
 */
internal class WordPieceTokenizer(vocab: List<String>, private val maxTokens: Int = MAX_TOKENS) {
    private val ids: Map<String, Int> = vocab.withIndex().associate { (index, piece) -> piece to index }
    private val unk = ids.getValue("[UNK]")
    private val cls = ids.getValue("[CLS]")
    private val sep = ids.getValue("[SEP]")

    fun encode(text: String): IntArray {
        val pieces = ArrayList<Int>()
        pieces += cls
        for (word in words(bertNormalize(text))) {
            pieces += wordPieces(word)
            if (pieces.size >= maxTokens - 1) break
        }
        val body = pieces.take(maxTokens - 1)
        return (body + sep).toIntArray()
    }

    private fun wordPieces(word: String): List<Int> {
        if (word.codePointCount(0, word.length) > MAX_WORD_CHARS) return listOf(unk)
        val out = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                found = ids[piece]
                if (found != null) break
                end = word.offsetByCodePoints(end, -1)
            }
            if (found == null) return listOf(unk)
            out += found
            start = end
        }
        return out
    }

    private companion object {
        const val MAX_TOKENS = 128
        const val MAX_WORD_CHARS = 100
    }
}

/** BERT's normalizer: clean text, space around CJK, strip accents, lower case. */
internal fun bertNormalize(text: String): String {
    val cleaned = StringBuilder(text.length)
    text.codePoints().forEach { c ->
        when {
            c == 0 || c == REPLACEMENT_CHAR || isControl(c) -> Unit
            isWhitespace(c) -> cleaned.append(' ')
            isCjk(c) -> cleaned.append(' ').appendCodePoint(c).append(' ')
            else -> cleaned.appendCodePoint(c)
        }
    }
    val decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
    val stripped = StringBuilder(decomposed.length)
    decomposed.codePoints().forEach { c ->
        if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) stripped.appendCodePoint(c)
    }
    return stripped.toString().lowercase()
}

/** BERT's pre-tokenizer: words split at whitespace, and every punctuation character a word of its own. */
internal fun words(text: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotEmpty()) out += current.toString()
        current.setLength(0)
    }
    text.codePoints().forEach { c ->
        when {
            isWhitespace(c) -> flush()
            isPunctuation(c) -> {
                flush()
                out += String(Character.toChars(c))
            }
            else -> current.appendCodePoint(c)
        }
    }
    flush()
    return out
}

private const val REPLACEMENT_CHAR = 0xFFFD

private fun isWhitespace(c: Int): Boolean =
    c == ' '.code || c == '\t'.code || c == '\n'.code || c == '\r'.code ||
        Character.getType(c) == Character.SPACE_SEPARATOR.toInt()

private fun isControl(c: Int): Boolean {
    if (c == '\t'.code || c == '\n'.code || c == '\r'.code) return false
    val type = Character.getType(c)
    return type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
        type == Character.PRIVATE_USE.toInt() || type == Character.SURROGATE.toInt() ||
        type == Character.UNASSIGNED.toInt()
}

/** Every printable ASCII character that is not a letter, digit or space — BERT splits on all of them. */
private val ASCII_PUNCTUATION: Set<Int> = ("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~").map { it.code }.toSet()

private fun isPunctuation(c: Int): Boolean {
    if (c in ASCII_PUNCTUATION) return true
    return when (Character.getType(c)) {
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt() -> true
        else -> false
    }
}

@Suppress("MagicNumber")
private fun isCjk(c: Int): Boolean =
    c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0x20000..0x2A6DF || c in 0x2A700..0x2B73F ||
        c in 0x2B740..0x2B81F || c in 0x2B820..0x2CEAF || c in 0xF900..0xFAFF || c in 0x2F800..0x2FA1F
