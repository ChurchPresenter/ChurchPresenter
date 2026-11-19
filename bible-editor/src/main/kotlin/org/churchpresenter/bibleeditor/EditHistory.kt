package org.churchpresenter.bibleeditor

import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseAddress
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bibleedits.address
import org.churchpresenter.bibleedits.currentChanges
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Every change made in the editor, in order, and how many of them are saved.
 *
 * What a verse reads as now is the last change to it, so the edits are never kept apart from the
 * history: discarding is cutting the history back to what was saved.
 */
class EditHistory(private val now: () -> Long = System::currentTimeMillis) {

    var entries by mutableStateOf<List<EditEntry>>(emptyList())
        private set

    var savedCount by mutableIntStateOf(0)
        private set

    val changes: Map<VerseAddress, VerseChange> by derivedStateOf { currentChanges(entries) }

    val unsavedCount: Int get() = entries.size - savedCount

    /** Takes [loaded] as the saved history, replacing whatever was here. */
    fun reset(loaded: List<EditEntry>) {
        entries = loaded
        savedCount = loaded.size
    }

    /** Records [address] going to [after]; nothing happens when it already reads that way. */
    fun commit(address: VerseAddress, after: VerseChange?, original: String?, revertOf: Long? = null) {
        val before = changes[address]
        if (before == after) return
        val entry = EditEntry(
            id = (entries.maxOfOrNull { it.id } ?: 0) + 1,
            time = now(),
            bible = address.bible,
            book = address.book,
            chapter = address.chapter,
            verse = address.verse,
            before = before,
            after = after,
            revertOf = revertOf,
            original = original,
        )
        entries = entries + entry
    }

    /** Whether the verse [entry] changed already reads the way it did before that change. */
    fun isUndone(entry: EditEntry): Boolean = changes[entry.address] == entry.before

    fun revert(entry: EditEntry) {
        if (!isUndone(entry)) commit(entry.address, entry.before, entry.original, revertOf = entry.id)
    }

    fun isUnsaved(entry: EditEntry): Boolean = entries.indexOf(entry) >= savedCount

    fun markSaved() {
        savedCount = entries.size
    }

    fun discardUnsaved() {
        entries = entries.take(savedCount)
    }

    fun lastFor(address: VerseAddress): EditEntry? = entries.lastOrNull { it.address == address }
}
