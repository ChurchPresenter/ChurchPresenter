package org.churchpresenter.updater

/**
 * A release's notes, read for the update window rather than shown as raw markdown.
 *
 * The releases are written as `**Group**` lines, each over a list of `- change (#123)` bullets, with
 * an optional paragraph before the first group (a nightly says what it is built from). GitHub's own
 * generated notes put the pull request at the end instead (`by @someone in …/pull/123`); both kinds of
 * reference come out of the text as [NotesLine.pulls], so the window can draw them as links.
 *
 * A horizontal rule ends the notes: below it the releases list the installer for each platform, which
 * is for the release page — the updater has already picked this machine's.
 */
internal data class NotesGroup(val title: String?, val lines: List<NotesLine>)

/** One line under a group: a bullet or a plain sentence, and the pull requests it names. */
internal data class NotesLine(val text: String, val pulls: List<Int>, val bullet: Boolean)

private val rulePattern = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")
private val headingPattern = Regex("""^(?:#{1,6}\s+(.+?)\s*#*|\*\*(.+?)\*\*:?)$""")
private val bulletPattern = Regex("""^[-*+]\s+(.+)$""")
private val pullRefPattern = Regex("""\s*\(#(\d+)\)""")
private val pullUrlPattern = Regex("""\s*(?:by @\S+\s+)?in\s+https://github\.com/\S+/pull/(\d+)\s*$""")
private val linkPattern = Regex("""\[([^\]]+)]\([^)]+\)""")
private val emphasisPattern = Regex("""\*\*|__|`""")
private val strayCommaPattern = Regex("""\s+([,;.])""")

/** [notes] as groups of lines, in the order written. Blank notes give no groups. */
internal fun parseReleaseNotes(notes: String): List<NotesGroup> {
    val groups = mutableListOf<NotesGroup>()
    var title: String? = null
    var lines = mutableListOf<NotesLine>()
    fun flush() {
        if (title != null || lines.isNotEmpty()) groups += NotesGroup(title, lines)
        lines = mutableListOf()
    }
    val written = notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
    written.takeWhile { !rulePattern.matches(it) }.forEach { raw ->
        val heading = headingPattern.matchEntire(raw)
        if (heading != null) {
            flush()
            // "**Media:**" and "**Media**:" both name the group Media.
            title = cleanInline(heading.groupValues[1].ifEmpty { heading.groupValues[2] }).trimEnd(':').trim()
            return@forEach
        }
        val bullet = bulletPattern.matchEntire(raw)
        lines += notesLine(bullet?.groupValues?.get(1) ?: raw, bullet = bullet != null)
    }
    flush()
    return groups
}

private fun notesLine(text: String, bullet: Boolean): NotesLine {
    val pulls = mutableListOf<Int>()
    var rest = pullUrlPattern.replace(text) { match -> pulls += match.groupValues[1].toInt(); "" }
    rest = pullRefPattern.replace(rest) { match -> pulls += match.groupValues[1].toInt(); "" }
    return NotesLine(cleanInline(rest), pulls, bullet)
}

/** The words of a markdown line without its markup: links keep their text, emphasis and code lose their marks. */
private fun cleanInline(text: String): String =
    text.replace(linkPattern, "$1")
        .replace(emphasisPattern, "")
        .replace(strayCommaPattern, "$1")
        .trim()

/** Where pull request [number] lives on GitHub. */
internal fun pullRequestUrl(number: Int): String = "${UpdateChecker.PULLS_URL}/$number"
