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
import org.churchpresenter.songchords.SecondVoice
import org.churchpresenter.songchords.VOICE_LINE_MARK
import org.churchpresenter.songchords.VoiceKey
import org.churchpresenter.songchords.wholeLineKeyOf

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
 * [value] with [key] — voice 2, voice 3 or a colour of its own — marked where the operator points:
 * - words selected on one line are wrapped as `2>(…)`;
 * - a selection across lines, or no selection at all, marks each line it touches with `2>`, or —
 *   when every one of them is marked with it already — takes the mark off again. A line marked
 *   for another voice or colour is moved to this one.
 *
 * The selection follows the text it was on.
 */
internal fun toggleVoice(value: TextFieldValue, key: VoiceKey): TextFieldValue {
    val mark = key.mark
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selected = text.substring(start, end)
    if (selected.isNotBlank() && '\n' !in selected) {
        val wrapped = "$mark($selected)"
        val opening = mark.length + 1
        return TextFieldValue(
            text.take(start) + wrapped + text.drop(end),
            TextRange(start + opening, end + opening),
        )
    }
    val (firstLine, lastLineEnd) = lineSpanOf(value)
    val lines = text.substring(firstLine, lastLineEnd).split('\n')
    val marked = lines.filter { it.isNotBlank() }.all { wholeLineKeyOf(it) == key }
    val rewritten = lines.map { line ->
        val unmarked = VOICE_LINE_MARK.find(line)?.let { line.substring(it.range.last + 1).trimStart() }
        when {
            line.isBlank() -> line
            marked -> unmarked ?: line
            else -> mark + (unmarked ?: line)
        }
    }.joinToString("\n")
    val grown = rewritten.length - (lastLineEnd - firstLine)
    val newText = text.take(firstLine) + rewritten + text.drop(lastLineEnd)
    val caret = if (grown < 0) (end + grown).coerceAtLeast(firstLine) else end + grown
    return TextFieldValue(newText, TextRange(if (end > start) firstLine else caret, caret))
}

/**
 * The colour every line the cursor or selection of [value] touches is already marked with, when
 * they all share one — what the Color… button takes off instead of asking for a new colour.
 * Null when there is a selection inside one line, or the lines are not all in one colour.
 */
internal fun lineColorAt(value: TextFieldValue): VoiceKey? {
    val selected = value.text.substring(value.selection.min, value.selection.max)
    if (selected.isNotBlank() && '\n' !in selected) return null
    val (firstLine, lastLineEnd) = lineSpanOf(value)
    val keys = value.text.substring(firstLine, lastLineEnd).split('\n')
        .filter { it.isNotBlank() }
        .map { wholeLineKeyOf(it) }
        .distinct()
    return keys.singleOrNull()?.takeIf { it.voice == SecondVoice.CUSTOM }
}

/** Where the lines the cursor or selection of [value] touches start and end in its text. */
private fun lineSpanOf(value: TextFieldValue): Pair<Int, Int> {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val firstLine = text.lastIndexOf('\n', start - 1) + 1
    val lastLineEnd = text.indexOf('\n', if (end > start) end - 1 else end).let { if (it < 0) text.length else it }
    return firstLine to lastLineEnd
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
        ?.let(SecondVoice::plain)
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
