package org.churchpresenter.helper.intent.glossary

import org.churchpresenter.helper.intent.normalize

/**
 * One language's words, rewritten into the English the rules read: "сделай фон синим" becomes
 * "make background blue". A key word ending in `*` matches any word that starts with it, which is how an
 * inflected language names every form of a word once. [spaced] is false for a language written
 * without spaces between words, where a key matches anywhere in the text.
 */
internal class Glossary(val language: String, entries: List<Pair<String, String>>, val spaced: Boolean = true) {

    private class Entry(key: List<String>, val english: String) {
        val words = key.map { it.removeSuffix("*") }
        val prefixes = key.map { it.endsWith("*") }
        val phrase = words.joinToString(" ")
    }

    private val entries: List<Entry> = entries
        .map { (key, english) -> Entry(key.trim().split(Regex("""\s+""")).map(::foldWord), english) }
        .filter { it.phrase.isNotBlank() }
        .sortedWith(compareByDescending<Entry> { it.words.size }.thenByDescending { it.phrase.length })

    fun toEnglish(normalized: String): String {
        val text = fold(normalized)
        return normalize(if (spaced) byWords(text) else byText(text))
    }

    private fun byWords(text: String): String {
        val words = text.split(' ')
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val entry = entries.firstOrNull { it.matchesAt(words, i) }
            if (entry != null) {
                out += entry.english
                i += entry.words.size
            } else {
                out += words[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun Entry.matchesAt(text: List<String>, start: Int): Boolean {
        if (start + words.size > text.size) return false
        return words.indices.all { k ->
            val word = text[start + k]
            if (prefixes[k]) word.startsWith(words[k]) else word == words[k]
        }
    }

    private fun byText(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val entry = entries.firstOrNull { text.startsWith(it.phrase, i) }
            if (entry != null) {
                out.append(' ').append(entry.english).append(' ')
                i += entry.phrase.length
            } else {
                out.append(text[i])
                i++
            }
        }
        return out.toString()
    }

    private companion object {
        fun fold(text: String): String = normalize(text).replace('ё', 'е')

        fun foldWord(word: String): String =
            if (word.endsWith("*")) fold(word.removeSuffix("*")) + "*" else fold(word)
    }
}
