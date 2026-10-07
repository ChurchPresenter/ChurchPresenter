package org.churchpresenter.songs

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.secondary_title
import org.churchpresenter.strings.generated.resources.song_named_title
import org.churchpresenter.strings.generated.resources.song_pane_secondary
import org.churchpresenter.strings.generated.resources.song_pane_translation
import org.churchpresenter.strings.generated.resources.song_title
import org.churchpresenter.strings.generated.resources.song_translation_title
import org.jetbrains.compose.resources.stringResource

private val SECTION_MARKER_LINE = Regex("""^[\[{][^\]}]*[\]}]$""")
private val CHORD_MARKER = Regex("""\[[^\]]*]""")

/** What a language's pane tab says: its own name where it has one, its position otherwise. */
@Composable
internal fun translationPaneLabel(index: Int, label: String): String = when {
    label.isNotBlank() -> label
    index == 0 -> stringResource(Res.string.song_pane_secondary)
    else -> stringResource(Res.string.song_pane_translation, index + 2)
}

/**
 * What the first language's title card says: "<name> Title" once Language 1 is named, as the other
 * languages' cards do, and "Song Title" until then. Only the label follows the name -- the field is
 * still the song's own title, the one the library lists and search finds.
 */
@Composable
internal fun primaryTitleLabel(name: String): String =
    if (name.isNotBlank()) stringResource(Res.string.song_named_title, name)
    else stringResource(Res.string.song_title)

/**
 * What a language's title card says: "<name> Title" where the language has a name -- the same name
 * its pane tab shows -- and its position otherwise, exactly as before languages could be named.
 */
@Composable
internal fun translationTitleLabel(index: Int, name: String): String = when {
    name.isNotBlank() -> stringResource(Res.string.song_named_title, name)
    index == 0 -> stringResource(Res.string.secondary_title)
    else -> stringResource(Res.string.song_translation_title, index + 2)
}

/**
 * Puts [snippet] in at the caret, replacing whatever is selected, and leaves the caret after it.
 *
 * With [ownLine] the snippet is given a blank line above and a line below unless it already has
 * them, which is what a section marker needs: markers are only read as headers when they stand
 * alone on their line.
 */
internal fun insertSnippet(value: TextFieldValue, snippet: String, ownLine: Boolean): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val before = value.text.take(start)
    val after = value.text.drop(end)
    val piece = if (!ownLine) snippet else buildString {
        if (before.isNotEmpty() && !before.endsWith("\n\n")) {
            append(if (before.endsWith("\n")) "\n" else "\n\n")
        }
        append(snippet)
        if (!after.startsWith("\n")) append("\n")
    }
    return TextFieldValue(before + piece + after, TextRange(start + piece.length))
}

/**
 * [value] with every chord moved by [steps] semitones. The cursor keeps its place in the words:
 * a chord before it can change length (`C` → `C#`), so the new offset is the length of the text
 * before it once that part is transposed too.
 */
internal fun transposeValue(value: TextFieldValue, steps: Int, flats: Boolean): TextFieldValue {
    if (steps == 0) return value
    val text = ChordTransposer.transposeText(value.text, steps, flats)
    fun moved(offset: Int) =
        ChordTransposer.transposeText(value.text.take(offset), steps, flats).length.coerceAtMost(text.length)
    return TextFieldValue(text, TextRange(moved(value.selection.start), moved(value.selection.end)))
}

/**
 * The first line the audience would actually read, for the background preview to sit behind.
 *
 * Only a line that is *entirely* a marker is a section header — `[Verse 1]`, `{Chorus}`. A line
 * merely starting with one is a lyric carrying its first chord, and skipping it would leave the
 * preview showing the second line of the song.
 */
internal fun firstLyricLine(lyrics: String): String =
    lyrics.lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !SECTION_MARKER_LINE.matches(it) }
        ?.replace(CHORD_MARKER, "")
        ?.trim()
        .orEmpty()

/**
 * This value with the section at [slot] carrying [background] under [prefix].
 *
 * The caret is kept where it was, clamped into the rewritten text: the panel writes into the lyrics
 * box while the operator is looking at the panel, and a caret that jumped to the top every time
 * would move the next thing they type.
 */
internal fun TextFieldValue.withSectionBackgroundAt(
    slot: Int,
    prefix: String,
    background: SongBackground,
): TextFieldValue {
    val next = withSectionBackground(text.split("\n"), slot, prefix, background).joinToString("\n")
    return TextFieldValue(next, TextRange(selection.min.coerceAtMost(next.length)))
}
