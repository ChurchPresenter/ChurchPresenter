package org.churchpresenter.bibleedits

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.VerseEdit

/** What the saved history makes the verses of [bibleKey] read, as edits a loaded module takes. */
fun verseEditsFor(entries: List<EditEntry>, bibleKey: String): List<VerseEdit> =
    currentChanges(entries)
        .filterKeys { it.bible == bibleKey }
        .mapNotNull { (address, change) ->
            when {
                change.removed -> VerseEdit(address.book, address.chapter, address.verse, null)
                change.text != null -> VerseEdit(address.book, address.chapter, address.verse, change.text)
                else -> null
            }
        }

/** Lays the corrections saved in [store] for [bibleKey] over this loaded module. */
fun Bible.applySavedEdits(store: BibleEditsStore, bibleKey: String) {
    applyVerseEdits(verseEditsFor(store.load(), bibleKey))
}
