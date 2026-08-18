package converter.library

import java.nio.charset.Charset

/**
 * Turns Rich Text Format into the plain text underneath it.
 *
 * Every song format that stores formatted lyrics stores them as RTF: ProPresenter 4-7 (base64 in
 * the XML, raw bytes in the protobuf), all four EasyWorship flavours, and MediaShout 7. So this is
 * shared rather than reimplemented per converter.
 *
 * Two things make a naive `\\[a-z]+` strip give wrong answers on real files:
 *
 *  - **Non-Latin lyrics are byte escapes, not characters.** Cyrillic is written as `\'e0` — one
 *    byte per character, meaningless without the code page it was written in. The code page comes
 *    from `\ansicpg`, but a font can override it: an otherwise cp1252 document holds its Russian in
 *    a font declared `\fcharset204`, and the escapes in that font's runs are cp1251. That is why
 *    the font table is parsed instead of skipped like the other control tables.
 *  - **`\u` needs its replacement skipped.** RTF writes a Unicode character as `ၕ` followed by
 *    `\ucN` "fallback" characters for readers that cannot handle it. Emitting both doubles the text.
 */
object RtfText {

    /** `\fcharset` numbers to the Windows code page each selects. */
    private val CHARSET_CODE_PAGES = mapOf(
        0 to 1252, 2 to 1252, 77 to 10000, 128 to 932, 129 to 949, 130 to 1361, 134 to 936,
        136 to 950, 161 to 1253, 162 to 1254, 163 to 1258, 177 to 1255, 178 to 1256, 186 to 1257,
        204 to 1251, 222 to 874, 238 to 1250, 254 to 437, 255 to 850,
    )

    /**
     * Control tables and metadata whose contents are never lyrics. `\*` marks most of them, but
     * these are written without it and would otherwise land in the output as font and colour names.
     */
    private val SKIPPED_DESTINATIONS = setOf(
        "colortbl", "stylesheet", "info", "pict", "object", "themedata", "datastore",
        "latentstyles", "listtable", "listoverridetable", "rsidtbl", "generator", "filetbl",
        "revtbl", "xmlnstbl", "fchars", "keycode", "footer", "header", "footnote",
    )

    /** Control words that end a line. `\par` is a paragraph, `\line` a soft break within one. */
    private val LINE_BREAKS = setOf("par", "line", "sect", "page")

    private const val DEFAULT_CODE_PAGE = 1252

    /** One nesting level of `{...}`, inherited by value from its parent on `{`. */
    private data class Group(
        var ignore: Boolean = false,
        var unicodeSkip: Int = 1,
        var codePage: Int = DEFAULT_CODE_PAGE,
    )

    /**
     * Extracts the text of [rtf], or returns it unchanged when it is not RTF at all.
     *
     * Passing plain text through untouched is deliberate: EasyWorship service files written by
     * third-party tools store bare text in the slot the format says is RTF, and losing those songs
     * to a format check helps nobody.
     *
     * @param defaultCodePage the code page for `\'hh` escapes before any `\ansicpg` is seen.
     */
    fun toPlainText(rtf: String, defaultCodePage: Int = DEFAULT_CODE_PAGE): String {
        if (!rtf.trimStart().startsWith("{\\rtf")) return TextUtils.sanitizeLyricText(rtf)

        val out = StringBuilder()
        val bytes = ArrayList<Byte>()          // consecutive \'hh escapes, decoded together
        val stack = ArrayDeque<Group>()
        var group = Group(codePage = defaultCodePage)
        val fontCodePages = HashMap<Int, Int>()
        var fontTableDepth = -1                // nesting depth of \fonttbl, -1 when outside it
        var pendingFont = -1                   // font being declared inside \fonttbl
        var index = 0

        fun flushBytes() {
            if (bytes.isEmpty()) return
            if (!group.ignore) out.append(String(bytes.toByteArray(), charsetFor(group.codePage)))
            bytes.clear()
        }

        while (index < rtf.length) {
            when (val c = rtf[index]) {
                '{' -> {
                    flushBytes()
                    stack.addLast(group)
                    group = group.copy()
                    index++
                }

                '}' -> {
                    flushBytes()
                    if (fontTableDepth >= 0 && stack.size <= fontTableDepth) fontTableDepth = -1
                    group = stack.removeLastOrNull() ?: Group(codePage = defaultCodePage)
                    index++
                }

                '\\' -> {
                    val next = rtf.getOrNull(index + 1)
                    when {
                        next == null -> index++

                        // \'hh — one raw byte in the current code page.
                        next == '\'' -> {
                            val hex = rtf.substring(
                                (index + 2).coerceAtMost(rtf.length),
                                (index + 4).coerceAtMost(rtf.length),
                            )
                            val value = hex.toIntOrNull(radix = 16)
                            if (value != null) bytes.add(value.toByte())
                            index += 4
                        }

                        // \\ \{ \} are the literal characters.
                        next == '\\' || next == '{' || next == '}' -> {
                            flushBytes()
                            if (!group.ignore) out.append(next)
                            index += 2
                        }

                        // A line break inside the source is a paragraph break in the text.
                        next == '\n' || next == '\r' -> {
                            flushBytes()
                            if (!group.ignore) out.append('\n')
                            index += 2
                        }

                        next == '~' -> {
                            flushBytes()
                            if (!group.ignore) out.append(' ')
                            index += 2
                        }

                        // \- is an optional hyphen and \_ a non-breaking one; neither is printed.
                        next == '-' || next == '_' -> index += 2

                        // \* marks a destination the reader is allowed not to understand.
                        next == '*' -> {
                            group.ignore = true
                            index += 2
                        }

                        next.isLetter() -> {
                            flushBytes()
                            val word = readControlWord(rtf, index + 1)
                            index = word.end
                            when {
                                word.name == "fonttbl" -> {
                                    fontTableDepth = stack.size
                                    group.ignore = true      // font names are not lyrics
                                }
                                word.name == "ansicpg" && word.parameter != null -> {
                                    group.codePage = word.parameter
                                }
                                word.name == "uc" && word.parameter != null -> {
                                    group.unicodeSkip = word.parameter.coerceAtLeast(0)
                                }
                                word.name == "f" && word.parameter != null -> {
                                    if (fontTableDepth >= 0) {
                                        pendingFont = word.parameter
                                    } else {
                                        fontCodePages[word.parameter]?.let { group.codePage = it }
                                    }
                                }
                                word.name == "fcharset" && word.parameter != null && pendingFont >= 0 -> {
                                    CHARSET_CODE_PAGES[word.parameter]?.let { fontCodePages[pendingFont] = it }
                                }
                                word.name == "u" && word.parameter != null -> {
                                    if (!group.ignore) out.append(codePointOf(word.parameter))
                                    index = skipUnicodeFallback(rtf, index, group.unicodeSkip)
                                }
                                // \binN is followed by N raw bytes that are not text.
                                word.name == "bin" && word.parameter != null -> {
                                    index = (index + word.parameter.coerceAtLeast(0)).coerceAtMost(rtf.length)
                                }
                                word.name == "tab" -> if (!group.ignore) out.append('\t')
                                word.name in LINE_BREAKS -> if (!group.ignore) out.append('\n')
                                word.name in SKIPPED_DESTINATIONS -> group.ignore = true
                            }
                        }

                        else -> index += 2
                    }
                }

                /*
                 * A literal newline is ignorable whitespace — `\par` is what ends a paragraph — and
                 * it must stay ignorable, because RTF writers hard-wrap long paragraphs with one
                 * and honouring those would chop lyrics mid-sentence.
                 *
                 * The exception is a newline that sits exactly on a run boundary, which is how the
                 * macOS writer behind ProPresenter separates one styled run from the next. Without
                 * this, a slide's stray 12pt lead-in run is glued onto the front of its first sung
                 * line instead of being a line of its own that can be recognised and dropped.
                 */
                '\n', '\r' -> {
                    if (startsRun(rtf, index)) {
                        flushBytes()
                        if (!group.ignore) out.append('\n')
                    }
                    index++
                }

                else -> {
                    flushBytes()
                    if (!group.ignore) out.append(c)
                    index++
                }
            }
        }
        flushBytes()
        return TextUtils.sanitizeLyricText(out.toString())
    }

    /**
     * Whether the newline at [index] is followed by a font selection, and so opens a new run.
     *
     * `\f` with a *digit* is the font-number control word; `\fs`, `\fswiss` and the rest of the
     * `\f…` family are not, which is what keeps this from firing on ordinary formatting.
     */
    private fun startsRun(rtf: String, index: Int): Boolean {
        var i = index
        while (i < rtf.length && (rtf[i] == '\n' || rtf[i] == '\r')) i++
        return rtf.getOrNull(i) == '\\' && rtf.getOrNull(i + 1) == 'f' && rtf.getOrNull(i + 2)?.isDigit() == true
    }

    /** A control word and where it ends, the delimiting space consumed. */
    private data class ControlWord(val name: String, val parameter: Int?, val end: Int)

    private fun readControlWord(rtf: String, start: Int): ControlWord {
        var i = start
        while (i < rtf.length && rtf[i].isLetter()) i++
        val name = rtf.substring(start, i)
        val numberStart = i
        if (i < rtf.length && rtf[i] == '-') i++
        while (i < rtf.length && rtf[i].isDigit()) i++
        val parameter = rtf.substring(numberStart, i).toIntOrNull()
        // A single space after a control word delimits it and is not text.
        if (i < rtf.length && rtf[i] == ' ') i++
        return ControlWord(name, parameter, i)
    }

    /**
     * `\uN` is signed 16-bit, so anything above 32767 is written negative and wraps.
     */
    private fun codePointOf(parameter: Int): Char =
        (if (parameter < 0) parameter + 0x10000 else parameter).toChar()

    /**
     * Steps over the [skip] fallback characters that follow a `\uN`, counting a `\'hh` escape as
     * the one character it stands for rather than as its four source characters.
     */
    private fun skipUnicodeFallback(rtf: String, start: Int, skip: Int): Int {
        var i = start
        var remaining = skip
        while (remaining > 0 && i < rtf.length) {
            if (rtf[i] == '\\' && rtf.getOrNull(i + 1) == '\'') i += 4 else i++
            remaining--
        }
        return i
    }

    /** The charset for a Windows code page, falling back to cp1252 when the JVM has no such name. */
    private fun charsetFor(codePage: Int): Charset {
        val name = when (codePage) {
            10000 -> "x-MacRoman"
            437, 850 -> "IBM$codePage"
            else -> "windows-$codePage"
        }
        return runCatching { Charset.forName(name) }.getOrElse { Charset.forName("windows-1252") }
    }
}
