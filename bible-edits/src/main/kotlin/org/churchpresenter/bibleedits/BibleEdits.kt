package org.churchpresenter.bibleedits

import kotlinx.serialization.Serializable

/**
 * What one verse reads as after the person's changes, laid over the `.spb` file.
 *
 * The file itself is never rewritten: an edit is kept beside it, and the verse it changes is
 * found again by its place in that file's own numbering.
 */
@Serializable
data class VerseChange(
    val text: String? = null,
    val added: Boolean = false,
    val removed: Boolean = false,
)

/** One step in the history: a verse going from [before] to [after]; null is the file's own text. */
@Serializable
data class EditEntry(
    val id: Long,
    val time: Long,
    /** The `.spb` file's name, which is how a change finds its Bible again. */
    val bible: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val before: VerseChange? = null,
    val after: VerseChange? = null,
    val revertOf: Long? = null,
    /** The verse as the file has it, so the history reads without loading that Bible again. */
    val original: String? = null,
)

@Serializable
data class BibleEditsFile(
    val version: Int = 1,
    val entries: List<EditEntry> = emptyList(),
)

/** A verse in one Bible file, in that file's own numbering. */
data class VerseAddress(val bible: String, val book: Int, val chapter: Int, val verse: Int)

val EditEntry.address: VerseAddress get() = VerseAddress(bible, book, chapter, verse)

enum class EditKind { EDITED, ADDED, REMOVED, RESTORED, REVERTED }

fun EditEntry.kind(): EditKind {
    val b = before
    val a = after
    return when {
        revertOf != null -> EditKind.REVERTED
        a?.removed == true -> EditKind.REMOVED
        b?.removed == true -> EditKind.RESTORED
        a?.added == true && b?.added != true -> EditKind.ADDED
        b?.added == true && a == null -> EditKind.REMOVED
        else -> EditKind.EDITED
    }
}

/** Every verse's current change, replayed from the history in order. */
fun currentChanges(entries: List<EditEntry>): Map<VerseAddress, VerseChange> {
    val out = HashMap<VerseAddress, VerseChange>()
    for (e in entries) {
        val after = e.after
        if (after == null) out.remove(e.address) else out[e.address] = after
    }
    return out
}
