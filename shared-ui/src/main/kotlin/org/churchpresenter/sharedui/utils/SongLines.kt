package org.churchpresenter.sharedui.utils

import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.songchords.SecondVoice
import java.util.Locale

/**
 * Any line wrapped in [] or {} is a section header — except one holding nothing but a chord, which
 * an instrumental break writes as its own line. See [ChordTransposer.isSectionHeader].
 */
fun isHeaderLine(line: String): Boolean = ChordTransposer.isSectionHeader(line)

/** A header's name as written, without its brackets: `[Chorus 1]` → `Chorus 1`, `{Refrain}` → `Refrain`. */
fun sectionLabelOf(header: String): String = header.trim().trim('[', ']', '{', '}').trim()

/** {} = chorus, [] = verse/other */
fun isChorusHeader(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("{") && t.endsWith("}")
}

/**
 * Section names that are typed as verses but are not one: an intro, a bridge, a tag.
 *
 * Only `{}` marks a chorus, so every other header -- `[Verse 2]`, `[Bridge]`, `[Tag]` -- parses as
 * [Constants.SECTION_TYPE_VERSE]. That is right for what the section *is*, and wrong for the one
 * question the chorus auto-repeat asks of it, which is whether the congregation sings the chorus
 * after it. They do after a verse; they do not after a bridge.
 */
private val NON_VERSE_SECTION_NAMES = setOf(
    "intro",
    "outro",
    "bridge",
    "tag",
    "end",
    "ending",
    "interlude",
    "instrumental",
    "prechorus",
    "vamp",
    "coda",
    "turnaround",
    "break",
)

/**
 * Whether [header] names a verse -- as opposed to a bridge, an intro, a tag or another section that
 * merely shares the verse's `[]` bracket. A section with no header at all is body text, so a verse.
 *
 * A trailing number is part of the label, not of the name (`[Verse 2]`, `[Bridge 2]`), and the names
 * are matched ignoring case, spaces and hyphens so `[Pre-Chorus]` and `[pre chorus]` are one name.
 * The list is English-only: a translated header falls through as a verse, which is the behaviour
 * this replaced and so never a regression.
 */
fun isVerseHeader(header: String?): Boolean {
    val label = header?.let(::sectionLabelOf).orEmpty()
    if (label.isEmpty()) return true
    val name = label.trimEnd { it.isDigit() || it.isWhitespace() }
        .lowercase(Locale.US)
        .replace(Regex("[\\s-]"), "")
    return name !in NON_VERSE_SECTION_NAMES
}

/**
 * Whether [line] is a manual slide break — a line of dashes in brackets, `[---]`, that ends a slide
 * without ending the section. See [ChordTransposer.isSlideBreak], which owns the grammar; this is
 * the app-side name, matching [isHeaderLine].
 */
fun isSlideBreak(line: String): Boolean = ChordTransposer.isSlideBreak(line)

/**
 * The key and value of a background directive — `[background: gradient]` and its siblings, which a
 * section writes into the lyrics to give itself a background — or null when [line] is not one. See
 * [ChordTransposer.backgroundDirectiveOf], which owns the grammar; this is the app-side name,
 * matching [isHeaderLine] and [isSlideBreak].
 */
fun songBackgroundDirectiveOf(line: String): Pair<String, String>? =
    ChordTransposer.backgroundDirectiveOf(line)

/**
 * Whether [line] is a directive — `[background: …]` or `[voice: 2]` — rather than words: it
 * configures the lines around it and is never put on screen.
 */
fun isLyricDirective(line: String): Boolean =
    ChordTransposer.isBackgroundDirective(line) || SecondVoice.isVoiceDirective(line)

/**
 * [lines] as a slide built straight from a song shows them: directives gone, and the lines of a
 * `[voice: 2]` block written as `2>` lines, a block ending at `[voice: 1]` or the next header.
 */
fun presentableSongLines(lines: List<String>): List<String> {
    var voice = SecondVoice.MAIN
    return lines.mapNotNull { line ->
        val switched = SecondVoice.voiceDirectiveOf(line)
        when {
            switched != null -> { voice = switched; null }
            ChordTransposer.isBackgroundDirective(line) -> null
            isHeaderLine(line) -> { voice = SecondVoice.MAIN; line }
            voice == SecondVoice.SECOND && line.isNotBlank() -> SecondVoice.asSecondVoiceLine(line)
            else -> line
        }
    }
}

/**
 * [line] as words alone, its second-voice markup (`2>`, `2>(…)`) taken out — for anywhere a lyric
 * is shown or searched as plain text. See [SecondVoice], which owns the grammar.
 */
fun plainLyricLine(line: String): String = SecondVoice.plain(line)
