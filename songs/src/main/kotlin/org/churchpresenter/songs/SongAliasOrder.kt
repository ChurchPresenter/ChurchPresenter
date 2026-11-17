package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem

private val DIGIT_RUN = Regex("\\d+")

/**
 * Aliases in natural order: each run of digits compared as a number, so "Ch 9" comes before "Ch 10",
 * and the rest ignoring case.
 */
internal val aliasOrder: Comparator<SongItem> = Comparator { a, b -> naturalCompare(a.alias.trim(), b.alias.trim()) }

internal fun naturalCompare(a: String, b: String): Int {
    val pad = { s: String -> DIGIT_RUN.replace(s.lowercase()) { it.value.trimStart('0').padStart(20, '0') } }
    return pad(a).compareTo(pad(b))
}
