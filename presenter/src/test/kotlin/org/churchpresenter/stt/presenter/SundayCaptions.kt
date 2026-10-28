package org.churchpresenter.stt.presenter

import org.churchpresenter.core.models.stt.STTSegment

/**
 * Made-up captions shaped like a live service: segments of uneven length arriving one at a time,
 * each id one past the last, seen through the server's sliding window of the newest [window].
 *
 * Every word is a token `w<3 digits><letters>` that is unique across the whole run, and its first
 * [KEY_CHARS] characters are unique too -- so a word can be followed across frames even while the
 * drip feed has typed only part of it.
 */
internal object SundayCaptions {

    const val KEY_CHARS = 4

    private val LETTERS = listOf(
        "amber", "birch", "cedar", "dune", "ember", "fern", "grove", "heath", "iris", "juniper",
        "kelp", "lark", "moss", "nettle", "oak", "pine", "quill", "reed", "sage", "thyme",
    )

    // Words per segment, cycled: short and long ones mixed, as speech arrives
    private val LENGTHS = listOf(7, 12, 5, 9, 14, 6, 10, 8, 11, 4)

    /** The text of segment [index] (0-based), its words numbered on from the segments before it. */
    fun text(index: Int): String {
        val first = (0 until index).sumOf { LENGTHS[it % LENGTHS.size] }
        val count = LENGTHS[index % LENGTHS.size]
        return (first until first + count).joinToString(" ") { word(it) }
    }

    fun word(n: Int): String = "w%03d%s".format(n, LETTERS[n % LETTERS.size])

    fun segment(index: Int, text: String = text(index)) = STTSegment(
        id = index + 1, timestamp = "", text = text, start = index * 4.0, end = index * 4.0 + 4.0, completed = true,
    )

    /** What the server sends once [count] segments exist: the newest [window] of them, oldest first. */
    fun windowAt(count: Int, window: Int = 100): List<STTSegment> =
        (maxOf(0, count - window) until count).map { segment(it) }
}
