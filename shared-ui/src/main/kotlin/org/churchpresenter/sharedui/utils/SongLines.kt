package org.churchpresenter.sharedui.utils

import org.churchpresenter.songchords.ChordTransposer
import java.util.Locale

/**
 * Any line wrapped in [] or {} is a section header — except one holding nothing but a chord, which
 * an instrumental break writes as its own line. See [ChordTransposer.isSectionHeader].
 */
fun isHeaderLine(line: String): Boolean = ChordTransposer.isSectionHeader(line)

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
    val label = header?.trim()?.trim('[', ']', '{', '}')?.trim().orEmpty()
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
