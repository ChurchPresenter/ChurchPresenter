package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.utils.isHeaderLine
import org.churchpresenter.app.churchpresenter.utils.songBackgroundDirectiveOf
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SONG_LOWER_THIRD_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.songBackgroundFields
import org.churchpresenter.core.models.songs.songBackgroundFrom
import org.churchpresenter.core.models.songs.songBackgroundKeys

/**
 * One authored section of a lyric, as the background panel addresses it.
 *
 * "Authored" rather than presented: this reads the lyrics the way they are written, so the sections
 * here are the ones the operator can see in the editor and point at. The presented list is a
 * different thing — a chorus repeats, and a `[---]` splits a section into slides — and neither is
 * something to hang a stored background on.
 */
internal data class SectionBackgroundSlot(
    /** The section's name with its brackets off, or empty for words written before any header. */
    val label: String,
    /** Where the section's header sits in the lyric lines, or -1 when it has none. */
    val headerIndex: Int,
    val background: SongBackground,
    val lowerThirdBackground: SongBackground,
) {
    val isCustom: Boolean get() = background.isCustom || lowerThirdBackground.isCustom
}

/**
 * Every section of [lines] that a background can be pinned to, in the order written, each carrying
 * whatever its own `[background: …]` directives say.
 *
 * A run of words before the first header is a section too — a song written without headers at all is
 * one section, and it can have a background like any other.
 */
internal fun sectionBackgroundSlots(lines: List<String>): List<SectionBackgroundSlot> {
    val slots = mutableListOf<SectionBackgroundSlot>()
    val fields = mutableMapOf<String, String>()
    var label = ""
    var headerIndex = -1
    var hasBody = false

    fun flush() {
        if (headerIndex < 0 && !hasBody && fields.isEmpty()) return
        slots.add(
            SectionBackgroundSlot(
                label = label,
                headerIndex = headerIndex,
                background = songBackgroundFrom(fields, SONG_BACKGROUND_PREFIX),
                lowerThirdBackground = songBackgroundFrom(fields, SONG_LOWER_THIRD_BACKGROUND_PREFIX),
            )
        )
        fields.clear()
        hasBody = false
    }

    lines.forEachIndexed { index, line ->
        val directive = songBackgroundDirectiveOf(line)
        when {
            isHeaderLine(line) -> {
                flush()
                headerIndex = index
                label = line.trim().trim('[', ']', '{', '}').trim()
            }
            directive != null -> fields[directive.first] = directive.second
            line.isNotBlank() -> hasBody = true
        }
    }
    flush()
    return slots
}

/**
 * [lines] with the section at [slot] carrying [background] under [prefix] — its existing directives
 * for that prefix replaced, and nothing else in the song touched.
 *
 * An inheriting background writes no directives at all, which is both how the file format records
 * "inherit" everywhere else and what makes switching back to the song's background leave the lyrics
 * exactly as they were before anyone opened the panel. Directives are written directly under the
 * section's header so they read as part of it.
 */
internal fun withSectionBackground(
    lines: List<String>,
    slot: Int,
    prefix: String,
    background: SongBackground,
): List<String> {
    val slots = sectionBackgroundSlots(lines)
    val target = slots.getOrNull(slot) ?: return lines
    val keys = songBackgroundKeys(prefix).toSet()

    val start = target.headerIndex + 1
    val end = slots.drop(slot + 1).firstOrNull { it.headerIndex >= 0 }?.headerIndex ?: lines.size
    val body = lines.subList(start, end)
        .filterNot { line -> songBackgroundDirectiveOf(line)?.let { it.first in keys } == true }
    val directives = songBackgroundFields(background, prefix).map { (key, value) -> "[$key: $value]" }

    return lines.take(start) + directives + body + lines.drop(end)
}
