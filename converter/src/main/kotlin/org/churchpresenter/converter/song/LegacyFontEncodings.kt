package org.churchpresenter.converter.song

/**
 * Fonts that draw a language's letters in place of ASCII punctuation, so text typed for them only
 * reads right in that font. Text in one of these fonts is rewritten into the real Unicode letters,
 * after which any font that covers the language shows it.
 *
 * Add a language by adding its font names and the characters its fonts replace.
 */
internal object LegacyFontEncodings {

    private class Encoding(val fontNames: Regex, val letters: Map<Char, String>)

    private val encodings = listOf(
        // Yoruba: Yoruba_ITC Officina Serif, Yor_Arial, Yor_Times New Roman, Yur_Arial, …
        Encoding(
            Regex("""^(yoruba|yor|yur)[_ ]""", RegexOption.IGNORE_CASE),
            mapOf('[' to "ẹ", ']' to "ọ", '{' to "Ẹ", '}' to "Ọ"),
        ),
    )

    fun isLegacy(fontName: String): Boolean = encodingFor(fontName) != null

    /** Whether [text] uses [fontName]'s stand-ins as letters: one touching a letter inside a word. */
    fun usesStandIns(text: String, fontName: String): Boolean {
        val letters = encodingFor(fontName)?.letters ?: return false
        return text.indices.any { at ->
            text[at] in letters &&
                (text.getOrNull(at - 1)?.isLetter() == true || text.getOrNull(at + 1)?.isLetter() == true)
        }
    }

    fun decode(text: String, fontName: String): String {
        val letters = encodingFor(fontName)?.letters ?: return text
        return buildString(text.length) {
            for (char in text) append(letters[char] ?: char)
        }
    }

    private fun encodingFor(fontName: String): Encoding? {
        val name = fontName.trim()
        if (name.isEmpty()) return null
        return encodings.firstOrNull { it.fontNames.containsMatchIn(name) }
    }
}
