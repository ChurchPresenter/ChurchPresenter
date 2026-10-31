package org.churchpresenter.core.models.songs

/** The header keys a song's order and notes are written under. */
internal const val SONG_ORDER_KEY = "order"
internal const val SONG_NOTES_KEY = "notes"

/** Every header key [songArrangementFrom] reads. */
internal val SONG_ARRANGEMENT_KEYS = setOf(SONG_ORDER_KEY, SONG_NOTES_KEY)

private const val ORDER_SEPARATOR = " | "

/** What a `.song` header's arrangement keys say: the order the sections are sung in, and the notes. */
data class SongArrangement(
    val sectionOrder: List<String> = emptyList(),
    val notes: String = "",
)

/** The arrangement a `.song` header's [fields] carry. */
fun songArrangementFrom(fields: Map<String, String>): SongArrangement = SongArrangement(
    sectionOrder = fields[SONG_ORDER_KEY].orEmpty().split(ORDER_SEPARATOR.trim())
        .map { it.trim() }.filter { it.isNotEmpty() },
    notes = unescapeNotes(fields[SONG_NOTES_KEY].orEmpty()),
)

/** The header lines [song]'s arrangement is written as, in a fixed order; none for the defaults. */
fun songArrangementFields(song: SongItem): List<Pair<String, String>> = buildList {
    if (song.sectionOrder.isNotEmpty()) add(SONG_ORDER_KEY to song.sectionOrder.joinToString(ORDER_SEPARATOR))
    if (song.notes.isNotBlank()) add(SONG_NOTES_KEY to escapeNotes(song.notes.trim()))
}

/** [notes] on one header line: a backslash doubled, a line break written as `\n`. */
internal fun escapeNotes(notes: String): String =
    notes.replace("\r\n", "\n").replace("\\", "\\\\").replace("\n", "\\n")

/** The inverse of [escapeNotes]; a lone backslash before anything else is kept as written. */
internal fun unescapeNotes(value: String): String = buildString {
    var i = 0
    while (i < value.length) {
        val c = value[i]
        val next = value.getOrNull(i + 1)
        when {
            c == '\\' && next == 'n' -> { append('\n'); i += 2 }
            c == '\\' && next == '\\' -> { append('\\'); i += 2 }
            else -> { append(c); i++ }
        }
    }
}

/**
 * The key each written section is ordered by, one per entry of [headers] (a section's header, or
 * null for one written without).
 *
 * A header is its own key — `[Verse 3]` — so editing another verse never moves it. A header written
 * twice gets `#2`, `#3` on its later occurrences, and a section with no header is `#n` by its
 * position, which is the only thing that tells two of those apart.
 */
fun sectionKeysOf(headers: List<String?>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return headers.mapIndexed { index, header ->
        val label = header?.trim().orEmpty()
        if (label.isEmpty()) return@mapIndexed "#${index + 1}"
        val occurrence = (seen[label] ?: 0) + 1
        seen[label] = occurrence
        if (occurrence == 1) label else "$label#$occurrence"
    }
}

/**
 * The first section after [index] that is sung — what a stage monitor shows as "next" — stepping
 * over the ones the song's order leaves out. Null at the end of the song.
 */
fun nextSungSection(sections: List<LyricSection>, index: Int): LyricSection? =
    sections.drop((index + 1).coerceAtLeast(0)).firstOrNull { !it.skipped }
