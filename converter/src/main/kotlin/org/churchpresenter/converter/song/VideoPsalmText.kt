package org.churchpresenter.converter.song

import java.text.Normalizer

/**
 * A VideoPsalm text field as plain Unicode: its inline markup removed, the characters it set in a
 * legacy-encoded font turned into the letters that font showed, and the result NFC-normalized.
 *
 * VideoPsalm's inline tags are `<cAARRGGBB>`…`</c>` (color), `<fFont Name>`…`</f>` (font),
 * `<s1384>`…`</s>` (size), `<kAARRGGBB>`…`</k>` and `<b>`, `<i>`, `<u>`. A character is in the
 * innermost `<f>` around it, or else in `bodyFont` — the font the verse, song or book draws its text
 * in. Text in the body font is read as `typedFor` when the body font is not a legacy one: a song
 * typed for a legacy font that the book stopped naming, which only ever read right with that font.
 */
internal object VideoPsalmText {

    private val tag = Regex(
        """<(?<close>/?)(?:f(?<font>[^<>\r\n]*)|c[0-9a-fA-F]*|s\d*|k[0-9a-fA-F]*|[biu])>""",
    )

    /** Every font a book names, in a style (`FontName:"…"`) or inline (`<f…>`). */
    private val namedFonts = Regex("""(?:FontName:"|<f)([^"<>\r\n]+)""")

    private const val FORM_FEED = '\u000c'

    fun plain(text: String, bodyFont: String = "", typedFor: String = ""): String {
        val body = if (LegacyFontEncodings.isLegacy(bodyFont)) bodyFont else typedFor.ifEmpty { bodyFont }
        val fonts = ArrayDeque(listOf(body))
        val out = StringBuilder(text.length)
        var at = 0
        for (match in tag.findAll(text)) {
            out.append(LegacyFontEncodings.decode(text.substring(at, match.range.first), fonts.last()))
            at = match.range.last + 1
            val font = match.groups["font"] ?: continue
            if (match.groups["close"]?.value.isNullOrEmpty()) {
                fonts.addLast(font.value.trim())
            } else if (fonts.size > 1) {
                fonts.removeLast()
            }
        }
        out.append(LegacyFontEncodings.decode(text.substring(at), fonts.last()))
        return Normalizer.normalize(out.toString().replace(FORM_FEED, '\n'), Normalizer.Form.NFC)
    }

    /** The font a book, song or verse sets its lyrics in, from its `Style`; empty when it names none. */
    fun bodyFontOf(node: LooseJson): String =
        node.child("Style")?.child("Body")?.text("FontName").orEmpty().trim()

    /** The first legacy-encoded font a whole book's [source] names anywhere; empty when none. */
    fun legacyFontOf(source: String): String =
        namedFonts.findAll(source).map { it.groupValues[1].trim() }
            .firstOrNull { LegacyFontEncodings.isLegacy(it) }.orEmpty()

    /**
     * The legacy font a song was typed for although nothing in it names one: its book uses
     * [legacyFont] somewhere and the song uses that font's stand-ins inside its words.
     */
    fun typedFor(song: LooseJson, legacyFont: String): String {
        if (legacyFont.isEmpty()) return ""
        val words = (listOf(song.text("Text")) + song.children("Verses").map { it.text("Text") })
            .joinToString("\n") { plain(it) }
        return if (LegacyFontEncodings.usesStandIns(words, legacyFont)) legacyFont else ""
    }
}
