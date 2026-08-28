package org.churchpresenter.converter.song

/**
 * Just enough of a JSON reader to walk a VideoPsalm song book.
 *
 * VideoPsalm writes something JSON-shaped that no JSON parser will accept: a byte-order mark opens
 * the file, keys are unquoted, and — the one that matters — a verse's lyrics are stored with real
 * line breaks and other control characters inside the quoted string rather than as `\n`. Repairing
 * the text into valid JSON and handing it to a real parser is what OpenLP's importer does; reading
 * it directly is shorter, and it cannot lose a song to a repair pass that mistook one of those
 * line breaks for the end of a string.
 *
 * Only what a song book holds is understood: objects, arrays and strings. A number, `true` or
 * `null` is kept as the text it was written as, because every number in the format — a verse id, a
 * tag, a font size — is either wanted as a string or not wanted at all. Nothing here reports a
 * syntax error either: a truncated book gives up the songs it did parse rather than none of them.
 */
internal class LooseJson private constructor(private val fields: Map<String, Any>) {

    /** [name] as text, empty when the object does not carry it or carries something structured. */
    fun text(name: String): String = fields[name] as? String ?: ""

    /** The objects in the array at [name] — empty for a missing field or an array of scalars. */
    fun children(name: String): List<LooseJson> =
        (fields[name] as? List<*>)?.filterIsInstance<LooseJson>().orEmpty()

    companion object {
        fun parse(source: String): LooseJson = Reader(source.removePrefix(BOM)).readObject()

        private const val BOM = "﻿"
    }

    /**
     * A cursor over the text.
     *
     * Every read is bounded by the end of the input rather than by a well-formed document, so a
     * file that stops mid-verse ends the object it was in instead of throwing.
     */
    private class Reader(private val source: String) {

        private var at = 0

        fun readObject(): LooseJson {
            skipWhitespace()
            if (at < source.length && source[at] == '{') at++
            val fields = LinkedHashMap<String, Any>()
            while (true) {
                skipSeparators()
                if (at >= source.length || source[at] == '}') {
                    if (at < source.length) at++
                    return LooseJson(fields)
                }
                val key = readKey()
                skipWhitespace()
                if (at < source.length && source[at] == ':') at++
                fields[key] = readValue()
            }
        }

        private fun readArray(): List<Any> {
            at++ // the opening bracket
            val items = mutableListOf<Any>()
            while (true) {
                skipSeparators()
                if (at >= source.length || source[at] == ']') {
                    if (at < source.length) at++
                    return items
                }
                items.add(readValue())
            }
        }

        private fun readValue(): Any {
            skipWhitespace()
            if (at >= source.length) return ""
            return when (source[at]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                else -> readBareValue()
            }
        }

        /** An unquoted key, which is how the format writes all of them, or a quoted one. */
        private fun readKey(): String {
            if (source[at] == '"') return readString()
            val start = at
            while (at < source.length && source[at] != ':' && source[at] != '}') at++
            return source.substring(start, at).trim()
        }

        private fun readString(): String {
            at++ // the opening quote
            val out = StringBuilder()
            while (at < source.length) {
                when (source[at]) {
                    '\\' -> out.append(readEscape())
                    '"' -> {
                        at++
                        return out.toString()
                    }
                    else -> out.append(source[at++])
                }
            }
            return out.toString()
        }

        /** What the backslash at the cursor stands for; an unknown escape is the character itself. */
        private fun readEscape(): String {
            at++ // the backslash
            if (at >= source.length) return ""
            val escaped = source[at++]
            return when (escaped) {
                'n' -> "\n"
                'r' -> "\r"
                't' -> "\t"
                'b' -> "\b"
                'u' -> readCodePoint()
                else -> escaped.toString()
            }
        }

        private fun readCodePoint(): String {
            val end = minOf(at + HEX_DIGITS, source.length)
            val code = source.substring(at, end).toIntOrNull(HEX_RADIX) ?: return "u"
            at = end
            return code.toChar().toString()
        }

        /** A number, `true`, `false` or `null` — kept as the text it was written as. */
        private fun readBareValue(): String {
            val start = at
            while (at < source.length && source[at] !in VALUE_END) at++
            return source.substring(start, at).trim()
        }

        private fun skipWhitespace() {
            while (at < source.length && source[at].isWhitespace()) at++
        }

        private fun skipSeparators() {
            while (at < source.length && (source[at].isWhitespace() || source[at] == ',')) at++
        }

        private companion object {
            const val HEX_DIGITS = 4
            const val HEX_RADIX = 16
            const val VALUE_END = ",}]"
        }
    }
}
