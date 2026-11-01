package org.churchpresenter.core.models.songs

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The header keys a song's order, notes and section cues are written under. */
internal const val SONG_ORDER_KEY = "order"
internal const val SONG_NOTES_KEY = "notes"
internal const val SONG_CUES_KEY = "cues"

/** Every header key [songArrangementFrom] reads. */
internal val SONG_ARRANGEMENT_KEYS = setOf(SONG_ORDER_KEY, SONG_NOTES_KEY, SONG_CUES_KEY)

private val cuesSerializer = MapSerializer(String.serializer(), String.serializer())

private const val ORDER_SEPARATOR = " | "

/** What a `.song` header's arrangement keys say: the order the sections are sung in, and the notes. */
data class SongArrangement(
    val sectionOrder: List<String> = emptyList(),
    val notes: String = "",
    val sectionNotes: Map<String, String> = emptyMap(),
)

/** The arrangement a `.song` header's [fields] carry. */
fun songArrangementFrom(fields: Map<String, String>): SongArrangement = SongArrangement(
    sectionOrder = fields[SONG_ORDER_KEY].orEmpty().split(ORDER_SEPARATOR.trim())
        .map { it.trim() }.filter { it.isNotEmpty() },
    notes = unescapeNotes(fields[SONG_NOTES_KEY].orEmpty()),
    sectionNotes = decodeCues(fields[SONG_CUES_KEY].orEmpty()),
)

/** The header lines [song]'s arrangement is written as, in a fixed order; none for the defaults. */
fun songArrangementFields(song: SongItem): List<Pair<String, String>> = buildList {
    if (song.sectionOrder.isNotEmpty()) add(SONG_ORDER_KEY to song.sectionOrder.joinToString(ORDER_SEPARATOR))
    if (song.notes.isNotBlank()) add(SONG_NOTES_KEY to escapeNotes(song.notes.trim()))
    val cues = song.sectionNotes.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
    if (cues.isNotEmpty()) add(SONG_CUES_KEY to Json.encodeToString(cuesSerializer, cues))
}

/** The `cues:` header value — a JSON object, section key to cue — or none when it is not one. */
internal fun decodeCues(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    return try {
        Json.decodeFromString(cuesSerializer, value)
    } catch (_: SerializationException) {
        emptyMap()
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }
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

/** The bare section name an order key holds: `[Verse 1]` → `Verse 1`, `{Chorus}#2` → `Chorus`. */
fun sectionOrderLabel(key: String): String = key.substringBefore('#').trim().trim('[', ']', '{', '}').trim()

/**
 * A section order key — `[Verse 1]`, `{Chorus}#2`, `[1 куплет]`, `#3` — short enough for a table
 * cell: the name's first letter and its number, `V1`, `C`, `К1`, wherever in the name the number
 * is written. A name with no letter keeps its digits; a section with no name keeps its `#n`.
 */
fun sectionOrderAbbreviation(key: String): String {
    val label = sectionOrderLabel(key)
    val digits = label.filter { it.isDigit() }
    val letter = label.firstOrNull { it.isLetter() }?.uppercase() ?: return digits.ifEmpty { key }
    return letter + digits
}
