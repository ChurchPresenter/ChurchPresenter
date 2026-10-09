package org.churchpresenter.helper.action

import org.churchpresenter.helper.HelperText

/** What came of carrying out a [HelperAction]. */
sealed interface ActionOutcome {
    /** It was done. [undo] takes it back, when it can be. */
    data class Done(val message: HelperText? = null, val undo: UndoEntry? = null) : ActionOutcome

    /** It was not done, and [reason] says why. [instead] is an offer that would let it go ahead. */
    data class Refused(val reason: HelperText, val instead: HelperAction? = null) : ActionOutcome

    /** It is answered by pointing at things rather than doing them. */
    data class Guide(val tour: GuideTour) : ActionOutcome

    /** It opened the helper's own display setup. */
    data object DisplaySetup : ActionOutcome
}

/**
 * One change the helper can take back. [revert] does it and returns false when it no longer can —
 * the operator changed the same settings since.
 */
class UndoEntry(val label: HelperText, val revert: () -> Boolean)

/** The last few changes the helper made, newest last. Kept in memory only, for this session. */
class UndoStack(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries = ArrayDeque<UndoEntry>()

    /** The change the next undo takes back, or null when there is none. */
    val latest: UndoEntry? get() = entries.lastOrNull()

    fun push(entry: UndoEntry) {
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    /** Takes the newest change off and reverts it; false when there was nothing or it could not be reverted. */
    fun undo(): Boolean {
        val entry = entries.removeLastOrNull() ?: return false
        return entry.revert()
    }

    private companion object {
        const val DEFAULT_CAPACITY = 5
    }
}
