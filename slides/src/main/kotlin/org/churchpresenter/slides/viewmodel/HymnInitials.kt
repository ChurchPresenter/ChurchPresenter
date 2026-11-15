package org.churchpresenter.slides.viewmodel

import net.sourceforge.pinyin4j.PinyinHelper
import java.util.concurrent.ConcurrentHashMap

/** Matches every supported pronunciation without constructing all polyphonic title combinations. */
internal object HymnInitials {
    private val characterInitials = ConcurrentHashMap<Char, Set<Char>>()

    fun matches(title: String, query: String): Boolean {
        val letters = query.filterNot(Char::isWhitespace).lowercase()
        if (letters.isEmpty() || letters.any { it !in 'a'..'z' }) return false
        val initials = title.map { character ->
            characterInitials.computeIfAbsent(character) {
                val pronunciations = PinyinHelper.toHanyuPinyinStringArray(it)
                pronunciations?.mapNotNull { reading -> reading.firstOrNull()?.lowercaseChar() }?.toSet()
                    ?: setOf(it.lowercaseChar()).filter { letter -> letter in 'a'..'z' }.toSet()
            }
        }.filter { it.isNotEmpty() }
        if (letters.length > initials.size) return false
        return (0..initials.size - letters.length).any { start ->
            letters.indices.all { offset -> letters[offset] in initials[start + offset] }
        }
    }
}
