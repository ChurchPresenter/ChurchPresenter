package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.BibleVerse

/**
 * How aggressively the reverse lookup (identify the passage from the spoken verse text) fires.
 * [minRun] is the required length of the longest *contiguous* run of words shared between the spoken
 * text and a verse. Thresholds were tuned on real services: actual reading produces runs of 11-23,
 * while normal commentary almost never exceeds 5 — so 6 is essentially false-positive-free, 5 is
 * rare, 4 is noticeably noisier. (3 was unusable.)
 */
enum class TextMatchLevel(val minRun: Int, val label: String) {
    OFF(Int.MAX_VALUE, "Off"),
    CONSERVATIVE(6, "Conservative"),
    BALANCED(5, "Balanced"),
    AGGRESSIVE(4, "Aggressive"),
}

/**
 * Reverse passage lookup: given a chunk of spoken transcript, find the Bible verse whose text it is
 * (most closely) reading. Pure and self-contained so it can be unit-tested; built once per loaded
 * Bible. Matching is by the **longest contiguous run** of identical words — a 6-word verbatim run is
 * vanishingly rare in ordinary speech but trivial when someone reads a verse aloud.
 */
class BibleTextMatcher(verses: List<BibleVerse>) {

    data class Match(
        val book: Int,          // canonical book id (maps via canonicalBookIdToIndex)
        val chapter: Int,
        val verse: Int,
        val verseText: String,
        val run: Int,
    )

    private val meta: List<BibleVerse> = verses
    private val verseTokens: List<IntArray>
    private val wordIds = HashMap<String, Int>()
    // trigram (3 consecutive word ids) → verse indices that contain it
    private val trigramIndex = HashMap<Long, MutableList<Int>>()

    init {
        verseTokens = ArrayList(verses.size)
        for ((vi, v) in verses.withIndex()) {
            val ids = tokenize(v.verseText).map { internWord(it) }.toIntArray()
            verseTokens.add(ids)
            for (j in 0..ids.size - 3) {
                val key = trigramKey(ids[j], ids[j + 1], ids[j + 2])
                trigramIndex.getOrPut(key) { ArrayList(2) }.add(vi)
            }
        }
    }

    /** Returns the best-matching verse for [spokenText] at [level], or null if below threshold. */
    fun match(spokenText: String, level: TextMatchLevel): Match? {
        if (level == TextMatchLevel.OFF) return null
        // Use the tail of the spoken text (recent words) so old content doesn't dominate.
        val all = tokenize(spokenText)
        if (all.size < level.minRun) return null
        val spoken = all.takeLast(SPOKEN_WINDOW).map { wordIds[it] ?: -1 }.toIntArray()

        // Candidate verses: those sharing at least one trigram with the spoken tail.
        val candidates = HashSet<Int>()
        for (j in 0..spoken.size - 3) {
            val a = spoken[j]; val b = spoken[j + 1]; val c = spoken[j + 2]
            if (a < 0 || b < 0 || c < 0) continue
            trigramIndex[trigramKey(a, b, c)]?.let { candidates.addAll(it) }
        }
        if (candidates.isEmpty()) return null

        var bestRun = 0
        var bestVerse = -1
        for (vi in candidates) {
            val run = longestRun(spoken, verseTokens[vi])
            if (run > bestRun) { bestRun = run; bestVerse = vi }
        }
        if (bestRun < level.minRun || bestVerse < 0) return null
        val v = meta[bestVerse]
        return Match(v.book, v.chapter, v.verseNumber, v.verseText, bestRun)
    }

    /** Longest run of consecutive word ids common to both sequences (classic DP, O(n·hits)). */
    private fun longestRun(spoken: IntArray, verse: IntArray): Int {
        if (spoken.isEmpty() || verse.isEmpty()) return 0
        val posByWord = HashMap<Int, MutableList<Int>>()
        verse.forEachIndexed { idx, w -> if (w >= 0) posByWord.getOrPut(w) { ArrayList() }.add(idx) }
        var best = 0
        var prev = HashMap<Int, Int>()   // verse-position → run length ending here
        for (w in spoken) {
            if (w < 0) { prev = HashMap(); continue }
            val cur = HashMap<Int, Int>()
            for (vp in posByWord[w].orEmpty()) {
                val r = (prev[vp - 1] ?: 0) + 1
                cur[vp] = r
                if (r > best) best = r
            }
            prev = cur
        }
        return best
    }

    private fun internWord(w: String): Int = wordIds.getOrPut(w) { wordIds.size }

    private fun trigramKey(a: Int, b: Int, c: Int): Long =
        (a.toLong() shl 42) xor (b.toLong() shl 21) xor c.toLong()

    private fun tokenize(text: String): List<String> =
        TOKEN.findAll(text.lowercase().replace('ё', 'е')).map { it.value }.toList()

    companion object {
        private const val SPOKEN_WINDOW = 25   // recent spoken words to consider
        private val TOKEN = Regex("[\\p{L}0-9]+")
    }
}
