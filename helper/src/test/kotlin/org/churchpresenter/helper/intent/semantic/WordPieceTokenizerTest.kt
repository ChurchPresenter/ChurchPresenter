package org.churchpresenter.helper.intent.semantic

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WordPieceTokenizerTest {

    private val vocab = listOf(
        "[PAD]", "[UNK]", "[CLS]", "[SEP]", "song", "##s", "play", "!", "-", "(", ")", "日", "cafe", "x",
    )
    private val tokenizer = WordPieceTokenizer(vocab, maxTokens = 6)
    private fun id(piece: String) = vocab.indexOf(piece)

    @Test
    fun `words are cut into the longest pieces the vocabulary knows`() {
        assertEquals(listOf("[CLS]", "song", "##s", "[SEP]").map(::id), tokenizer.encode("Songs").toList())
    }

    @Test
    fun `an unknown word, or one far too long, is unk`() {
        assertEquals(id("[UNK]"), tokenizer.encode("zebra")[1])
        assertEquals(id("[UNK]"), tokenizer.encode("x".repeat(101))[1])
    }

    @Test
    fun `the token count is capped, ending in sep`() {
        val tokens = tokenizer.encode("play play play play play play play")
        assertEquals(6, tokens.size)
        assertEquals(id("[SEP]"), tokens.last())
    }

    @Test
    fun `the normalizer drops controls, spaces out cjk, strips accents and lowercases`() {
        assertEquals("cafe  日 ", bertNormalize("CAFÉ\u0000\u0007� 日"))
        assertEquals(" 㐀 ", bertNormalize("㐀"))
        assertEquals(" 𠀀 ", bertNormalize("𠀀"))
        assertEquals(" 𪜀 ", bertNormalize("𪜀"))
        assertEquals("a b c", bertNormalize("a\tb\tc"))
        assertEquals("ab", bertNormalize("a\u200B\uE000\u0378\uD800b"))
        listOf(0x2B740, 0x2B820, 0x2F800, 0xF900).forEach { c ->
            val cjk = String(Character.toChars(c))
            assertEquals(" ${Normalizer.normalize(cjk, Normalizer.Form.NFD)} ", bertNormalize(cjk))
        }
        listOf("가", "😀", "\uD880\uDC00", "ꀀ").forEach { assertFalse(bertNormalize(it).startsWith(" "), it) }
    }

    @Test
    fun `punctuation of every kind is a word of its own`() {
        assertEquals(
            listOf("a", "!", "b", "-", "c", "(", "d", ")", "e", "«", "f", "»", "g", "_", "h", "—", "i"),
            words("a!b-c(d)e«f»g_h—i"),
        )
        assertEquals(listOf("play", "!"), words("play!\r\n"))
        assertEquals(listOf("[CLS]", "play", "!", "[SEP]").map(::id), tokenizer.encode("play!").toList())
    }
}
