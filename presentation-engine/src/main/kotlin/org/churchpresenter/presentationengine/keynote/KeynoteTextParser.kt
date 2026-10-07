package org.churchpresenter.presentationengine.keynote

import org.churchpresenter.presentationengine.keynote.KnFields as F
import java.awt.Color

/** A text storage's paragraphs, each with the character and paragraph style in force at its start. */
internal object KeynoteTextParser {

    /** Last-resort size when neither the character nor the paragraph style names one. */
    private const val DEFAULT_FONT_SIZE_PT = 20.0

    /**
     * Keynote paragraph breaks: observed as a lone CR (`\r`, U+000D) in real files — not the
     * Unicode paragraph separator (` `) the code originally assumed, which never actually
     * appears (confirmed via `dumpKeynote`'s raw code-point dump against a real multi-bullet
     * text box: bullets were silently concatenating into one line because the CR was never being
     * converted). Both are replaced 1-for-1 with `\n` so [parseParagraphs]'s running character
     * offset (`start += line.length + 1`, used to look up the attribute-run tables, which are
     * indexed against the *original* string) stays exactly in sync.
     */
    fun normalizeParagraphBreaks(raw: String): String =
        raw.replace(' ', '\n').replace('\r', '\n')

    fun parseParagraphs(index: ObjectIndex, storage: IwaMessage): List<KnParagraph> {
        val raw = storage.strings(F.STORAGE_TEXT).joinToString("")
        if (raw.isEmpty()) return emptyList()
        val text = normalizeParagraphBreaks(raw)

        val charStyleTable = attributeRuns(storage.message(F.STORAGE_TABLE_CHAR_STYLE))
        val paraStyleTable = attributeRuns(storage.message(F.STORAGE_TABLE_PARA_STYLE))

        val paragraphs = mutableListOf<KnParagraph>()
        var start = 0
        for (line in text.split('\n')) {
            val cleanText = line.filterNot { it == '￼' || it == '￻' }
            val charStyleId = charStyleAt(charStyleTable, start)
            val paraStyleId = paraStyleAt(paraStyleTable, start)
            val paraStyle = paraStyleId?.let { index.message(it) }
            // Per property, not all-or-nothing: a character style that only overrides (say) italic
            // must still take its family, size and colour from the paragraph style. Falling back
            // wholesale is what left five of six paragraphs at the 20pt-black defaults below.
            val charProps = KeynoteStyleResolver.resolveCharProps(index, charStyleId)
            val paraProps = paraStyle?.let { KeynoteStyleResolver.resolveCharProps(index, paraStyleId) }
            val alignment = paraStyle?.message(F.PARAGRAPH_STYLE_PARA_PROPERTIES)
                ?.varint(F.PARA_PROPS_ALIGNMENT)?.toInt()
                ?: 0
            paragraphs.add(
                KnParagraph(
                    text = cleanText,
                    fontFamily = charProps?.fontName ?: paraProps?.fontName,
                    fontSizePt = charProps?.fontSize ?: paraProps?.fontSize ?: DEFAULT_FONT_SIZE_PT,
                    bold = charProps?.bold ?: paraProps?.bold ?: false,
                    italic = charProps?.italic ?: paraProps?.italic ?: false,
                    color = charProps?.color ?: paraProps?.color ?: Color.BLACK,
                    alignment = alignment
                )
            )
            start += line.length + 1
        }
        return paragraphs
    }

    /**
     * The runs of an attribute table, **keeping** those that carry no style object: in iWork a run
     * with the reference left off is not padding, it is the point at which the previous run's
     * override stops applying. Dropping them let a character style that covered one word leak
     * forward over every later paragraph in the box.
     */
    private fun attributeRuns(table: IwaMessage?): List<Pair<Int, Long?>> =
        table?.messages(F.ATTR_TABLE_ENTRIES)?.mapNotNull { entry ->
            val charIndex = entry.varint(F.ATTR_ENTRY_CHAR_INDEX)?.toInt() ?: return@mapNotNull null
            charIndex to entry.message(F.ATTR_ENTRY_OBJECT)?.varint(F.REFERENCE_IDENTIFIER)
        } ?: emptyList()

    /** Character override in force at [charIndex] — null once an object-less run has cleared it. */
    private fun charStyleAt(runs: List<Pair<Int, Long?>>, charIndex: Int): Long? =
        runs.lastOrNull { it.first <= charIndex }?.second

    /**
     * Paragraph style in force at [charIndex]. Unlike a character run, a paragraph run with no
     * object means "same style as the paragraph before" — Keynote writes one per paragraph and
     * only names the style where it changes — so the last *named* style carries forward.
     */
    private fun paraStyleAt(runs: List<Pair<Int, Long?>>, charIndex: Int): Long? =
        runs.lastOrNull { it.first <= charIndex && it.second != null }?.second
}
