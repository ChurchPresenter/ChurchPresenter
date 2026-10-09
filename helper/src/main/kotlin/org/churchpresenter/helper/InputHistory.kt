package org.churchpresenter.helper

private const val MAX_ENTRIES = 50

/**
 * What the operator typed to Wick this run, newest last, walked like a shell's history: Up steps back
 * through it, Down steps forward and, past the newest, returns what was being typed before.
 */
internal class InputHistory {
    private val entries = ArrayDeque<String>()

    // Steps back from the newest, or null while not walking the history.
    private var cursor: Int? = null
    private var draft = ""

    /** Files [text] as the newest entry and stops walking. A repeat of the newest is filed once. */
    fun record(text: String) {
        cursor = null
        draft = ""
        if (entries.lastOrNull() == text) return
        entries.addLast(text)
        if (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    /** The entry before the one shown, keeping [current] as the draft on the first step; null with none. */
    fun older(current: String): String? {
        if (entries.isEmpty()) return null
        val at = cursor
        if (at == null) draft = current
        val next = ((at ?: -1) + 1).coerceAtMost(entries.lastIndex)
        cursor = next
        return entries[entries.lastIndex - next]
    }

    /** The entry after the one shown, the draft past the newest; null while not walking. */
    fun newer(): String? {
        val at = cursor ?: return null
        if (at == 0) {
            cursor = null
            return draft
        }
        cursor = at - 1
        return entries[entries.lastIndex - (at - 1)]
    }
}
