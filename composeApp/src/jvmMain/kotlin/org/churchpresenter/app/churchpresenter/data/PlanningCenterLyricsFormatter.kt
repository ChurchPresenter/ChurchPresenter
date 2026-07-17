package org.churchpresenter.app.churchpresenter.data

/**
 * Converts a Planning Center Services "chord chart" (chords inline as bracketed tokens, e.g.
 * `[G]Amazing [C]grace how [G]sweet`) into plain lyric text ChurchPresenter can display, by
 * stripping the bracketed chord tokens and leaving everything else (including section header
 * lines) untouched.
 *
 * The regex only matches brackets whose content looks like a chord symbol (starts with a note
 * letter A-G, optional accidental/quality/extension/slash-bass) — this is a best-effort mapping
 * pending verification against a real Planning Center account's chord chart output; loose enough
 * to catch common chord shapes without also eating a `[Verse 1]`-style section header, since those
 * don't match the chord pattern.
 */
object PlanningCenterLyricsFormatter {

    private val chordToken = Regex(
        """\[[A-G](?:#|b)?(?:maj|min|sus|dim|aug|add)?m?\d{0,2}(?:/[A-G](?:#|b)?)?\]"""
    )

    fun stripChords(chordChart: String): String =
        chordChart.lines().joinToString("\n") { line ->
            chordToken.replace(line, "").replace(Regex(" {2,}"), " ").trimEnd()
        }

    private val tagRegex = Regex("<[^>]+>")
    private val brOrCloseParagraph = Regex("(?i)<br\\s*/?>|</p>")
    private val whitespaceBetweenTags = Regex(">\\s+<")

    /**
     * Converts Planning Center's "html_details" rich text (plain `<p>`-per-line markup, no
     * nested formatting seen in practice) into plain text: one line per `<p>`/`<br>`, entities
     * decoded, remaining tags stripped. A lone `&nbsp;` paragraph (PCO's blank-line spacer)
     * collapses to an empty line, preserving verse/section breaks.
     *
     * PCO's stored markup already has a real newline between each `</p>` and the next `<p>`
     * (cosmetic source formatting, not a second line break) — collapsed away first via
     * [whitespaceBetweenTags] so it doesn't double up with the newline this function inserts for
     * every `</p>`/`<br>` and produce a spurious blank line between every single lyric line.
     */
    fun htmlDetailsToPlainText(html: String): String =
        html
            .replace(whitespaceBetweenTags, "><")
            .replace(brOrCloseParagraph, "\n")
            .replace(tagRegex, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim('\n')
}
