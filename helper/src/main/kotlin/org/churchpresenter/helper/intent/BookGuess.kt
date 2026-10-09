package org.churchpresenter.helper.intent

/**
 * What a typed book name comes to against the loaded Bible: [name] is the Bible's own name for the book,
 * [sure] whether it was named outright (or by the start of a name) rather than guessed from a misspelling.
 */
internal data class BookGuess(val name: String, val sure: Boolean)

/**
 * The book [typed] names among [books] — each book the loaded Bible has, as the names it goes by (its
 * own, then the standard English one), in the Bible's order. Sure when a name is [typed] or starts with
 * it ("ps", "1 cor"); a guess when [typed] is one or two letters off one ("jhon"), or its letters run
 * through one in order ("jn"); null when nothing is that close.
 */
internal fun guessBook(books: List<List<String>>, typed: String): BookGuess? {
    val wanted = squash(typed)
    if (wanted.isEmpty()) return null
    val named = books.firstOrNull { names -> names.any { squash(it) == wanted } }
        ?: books.firstOrNull { names -> names.any { squash(it).startsWith(wanted) } }
    val guessed = books.mapNotNull { names ->
        names.minOf { editDistance(squash(it), wanted) }.takeIf { it <= maxOf(1, wanted.length / MISSPELLING) }
            ?.let { distance -> names to distance }
    }.minByOrNull { it.second }?.first
        ?: books.firstOrNull { names -> names.any { runsThrough(squash(it), wanted) } }
    return when {
        named != null -> BookGuess(named.first(), sure = true)
        guessed != null -> BookGuess(guessed.first(), sure = false)
        else -> null
    }
}

/** Lower case, with the spaces and dots taken out: "1 Cor." and "1cor" are one name. */
private fun squash(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/** Whether [short]'s letters appear in [name] in order, starting with its first: "jn" in "john". */
private fun runsThrough(name: String, short: String): Boolean {
    if (short.length < 2 || name.firstOrNull() != short.first()) return false
    var at = 0
    for (c in name) if (at < short.length && c == short[at]) at++
    return at == short.length
}

/** Edits — a letter added, dropped, changed, or two swapped — between [a] and [b]. */
private fun editDistance(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (swapped(a, b, i, j)) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.length][b.length]
}

/** Whether the letters just before [i] in [a] and [j] in [b] are the same two, the other way round. */
private fun swapped(a: String, b: String, i: Int, j: Int): Boolean =
    i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]

/** One edit is allowed for every this many letters typed (and always at least one). */
private const val MISSPELLING = 3
