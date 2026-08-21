package org.churchpresenter.bibleengine.engine

object AgreementScorer {

    fun score(candidateText: String, transcript: String, translation: String): Double {
        val s1 = wordOverlap(candidateText, transcript)
        val s2 = wordOverlap(candidateText, translation)
        return s1 + s2
    }

    /**
     * Fraction (0..1) of [verseText]'s distinct words that also appear in [trackText] — i.e. "how
     * much of this verse is being read in this track". Used to attribute a detection to the
     * transcription and/or translation track for the corroboration markers.
     */
    fun coverage(verseText: String, trackText: String): Double {
        val verseWords = tokenize(verseText)
        if (verseWords.isEmpty()) return 0.0
        val trackWords = tokenize(trackText)
        return verseWords.intersect(trackWords).size.toDouble() / verseWords.size
    }

    private fun wordOverlap(doc: String, query: String): Double {
        if (query.isBlank()) return 0.0
        val docWords = tokenize(doc)
        val queryWords = tokenize(query)
        if (queryWords.isEmpty()) return 0.0
        val common = docWords.intersect(queryWords).size
        return common.toDouble() / queryWords.size
    }

    /**
     * The module's single text-normalization path — public so version detection scores against the
     * exact same token shape the agreement/coverage gates use. Keep it the only one: the `ё→е` fold
     * and the `\p{L}` class below were both bug fixes, and a second tokenizer would silently drift.
     */
    fun tokens(text: String): Set<String> = tokenize(text)

    private fun tokenize(text: String): Set<String> =
        // \p{L}: keep ALL letters (the old a-z+Cyrillic class shredded accented Latin —
        // "hätte" -> "h"+"tte" — for the non-EN/RU bibles). ё→е folds standard Russian
        // orthography variance symmetrically (verse text and STT share this path).
        text.lowercase().replace('ё', 'е')
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 3 }
            .toSet()
}
