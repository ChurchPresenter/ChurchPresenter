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
}
