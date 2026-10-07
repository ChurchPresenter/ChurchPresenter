package org.churchpresenter.bibleengine.bible

import org.churchpresenter.bibleengine.Config
import kotlin.math.ln

// Fuzzy-rescue tuning: only long tokens are stemmed/expanded (short words collide),
// expansions carry a score penalty, and at most 2 index terms join per garbled token.
private const val MIN_FUZZY_TOKEN_LEN = 6
private const val MAX_FUZZY_EXPANSIONS = 2
private const val FUZZY_WEIGHT = 0.7
private val RU_STEM_TRIM = "аеиоуыэюяйь".toSet()

/** An English suffix is stripped only from a word longer than this. */
private const val EN_SUFFIX_MIN_LEN = 6

/** A light stem is never trimmed below this length, and a shorter one is no stem at all. */
private const val MIN_STEM_LEN = 5

class BibleIndex(private val translations: List<EngineTranslation>) {

    data class SearchResult(
        val translationId: String,
        val verse: EngineVerse,
        val score: Double,
    )

    private data class DocEntry(val docId: Int, val tf: Float)

    private data class Document(
        val translationId: String,
        val verse: EngineVerse,
        val termCount: Int,
    )

    // Sized from the actual load (typically 1-2 bibles ≈ 31k verses each) instead of a fixed
    // 800k/500k pre-allocation that assumed every bible on disk would be indexed.
    private val documents = ArrayList<Document>(translations.sumOf { it.byBCV.size })
    private val invertedIndex = HashMap<String, MutableList<DocEntry>>(translations.sumOf { it.byBCV.size } / 2 + 16)
    private var avgDocLen = 0.0

    // Built from the index's terms once the index itself is, so declared after it.
    private val fuzzy: FuzzyStems = run {
        buildIndex()
        FuzzyStems(invertedIndex.keys)
    }

    private fun buildIndex() {
        var totalLen = 0L
        for (t in translations) {
            for (v in t.byBCV.values) {
                val tokens = if (v.isHeader || v.text.isBlank()) emptyList() else tokenize(v.text)
                if (tokens.isEmpty()) continue
                val docId = documents.size
                totalLen += tokens.size
                documents.add(Document(t.id, v, tokens.size))
                val tfMap = HashMap<String, Int>(tokens.size)
                for (tok in tokens) tfMap[tok] = (tfMap[tok] ?: 0) + 1
                for ((term, count) in tfMap) {
                    invertedIndex.getOrPut(term) { mutableListOf() }
                        .add(DocEntry(docId, count.toFloat()))
                }
            }
        }
        avgDocLen = if (documents.isEmpty()) 1.0 else totalLen.toDouble() / documents.size
    }

    fun search(query: String, topK: Int = Config.reverseTopK): List<SearchResult> {
        val groups = termGroups(tokenize(query).toSet())
        if (groups.isEmpty()) return emptyList()

        val scores = HashMap<Int, Double>(1024)
        for (group in groups) {
            for ((term, weight) in group) {
                scoreTerm(term, weight, restrictTo = null, scores)
            }
        }
        return topResults(scores, topK)
    }

    fun searchAllTerms(query: String, topK: Int = Config.reverseTopK): List<SearchResult> {
        val groups = termGroups(tokenize(query).toSet())

        // Every query token must be matchable — by its exact term or a fuzzy expansion. A doc
        // qualifies when it contains at least one term from EVERY group (union within a group,
        // intersection across groups) — exact-only queries reduce to the old all-terms semantics.
        val groupPostings = groups.map { group -> group.filter { invertedIndex.containsKey(it.first) } }
        val candidates = if (groups.isEmpty()) emptySet() else candidateDocs(groupPostings)
        if (candidates.isEmpty()) return emptyList()

        val scores = HashMap<Int, Double>(candidates.size * 2)
        for (group in groupPostings) {
            for ((term, weight) in group) {
                scoreTerm(term, weight, restrictTo = candidates, scores)
            }
        }
        return topResults(scores, topK)
    }

    /** The documents holding a term from every one of [groupPostings]; empty when any group has none. */
    private fun candidateDocs(groupPostings: List<List<Pair<String, Double>>>): Set<Int> {
        if (groupPostings.any { it.isEmpty() }) return emptySet()
        val docIdSets = groupPostings
            .map { group ->
                group.flatMapTo(HashSet()) { (term, _) -> invertedIndex.getValue(term).map { it.docId } }
            }
            .sortedBy { it.size }
        val candidates = docIdSets[0].toMutableSet()
        for (i in 1 until docIdSets.size) candidates.retainAll(docIdSets[i])
        return candidates
    }

    private fun scoreTerm(term: String, weight: Double, restrictTo: Set<Int>?, scores: HashMap<Int, Double>) {
        val postings = invertedIndex[term] ?: return
        val n = documents.size.toDouble()
        val k1 = Config.bm25K1
        val b = Config.bm25B
        val df = postings.size.toDouble()
        val idf = ln((n - df + 0.5) / (df + 0.5) + 1)
        for ((docId, tf) in postings) {
            if (restrictTo != null && docId !in restrictTo) continue
            val docLen = documents[docId].termCount.toDouble()
            val tfNorm = tf * (k1 + 1) / (tf + k1 * (1 - b + b * docLen / avgDocLen))
            scores[docId] = (scores[docId] ?: 0.0) + idf * tfNorm * weight
        }
    }

    private fun topResults(scores: Map<Int, Double>, topK: Int): List<SearchResult> =
        scores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { (docId, score) ->
                val doc = documents[docId]
                SearchResult(doc.translationId, doc.verse, score)
            }

    /**
     * One group per query token: the exact term at weight 1.0 when the index knows it, else its
     * fuzzy expansions at [FUZZY_WEIGHT] — garbled-STT rescue ("туждающие" for "труждающиеся").
     * A token with neither stays as a (postings-less) exact entry so searchAllTerms keeps its
     * all-terms fail-closed semantics.
     */
    private fun termGroups(queryTerms: Set<String>): List<List<Pair<String, Double>>> =
        queryTerms.map { token ->
            if (invertedIndex.containsKey(token)) listOf(token to 1.0)
            else fuzzy.expansions(token).map { it to FUZZY_WEIGHT }.ifEmpty { listOf(token to 1.0) }
        }

    fun tokenize(text: String): List<String> =
        // \p{L} + ё→е: see AgreementScorer.tokenize — index and query share this function,
        // so the normalization stays symmetric by construction.
        text.lowercase().replace('ё', 'е')
            .replace(Regex("[^\\p{L}0-9]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOPWORDS }

    companion object {
        // Very common function words removed at BOTH index and query time (symmetric). Kept
        // conservative — only grammatical words, no content words — so the all-terms reverse path
        // isn't forced to match "и"/"the"/"что" inside a verse, and BM25 length-norm isn't skewed.
        private val STOPWORDS: Set<String> = (
            // English
            "the a an and or of to in on at for with that this is are was were be by it as he she " +
            "we they you his her their our my me him them us so but not from which who whom unto " +
            "thy thee ye shall will have has had do did " +
            // Russian
            "не но на во об со ко из по за от же бы ли что как это этот так там тут вот для то он " +
            "она они оно мы вы ты его её их наш ваш мой твой быть был была было были чтобы если"
        ).split(" ").filter { it.isNotBlank() }.toHashSet()
    }
}

/**
 * The fuzzy-rescue structures over an index's [terms]: light stem -> original index terms, plus
 * (first char, stem length) buckets so the distance-1 scan only touches a small candidate set.
 */
private class FuzzyStems(terms: Set<String>) {
    private val stemToTerms = HashMap<String, MutableSet<String>>()
    private val stemBuckets = HashMap<Pair<Char, Int>, MutableList<String>>()

    init {
        for (term in terms) {
            val stem = lightStem(term) ?: continue
            if (stemToTerms.getOrPut(stem) { LinkedHashSet() }.add(term) &&
                stemToTerms.getValue(stem).size == 1
            ) {
                stemBuckets.getOrPut(stem.first() to stem.length) { mutableListOf() }.add(stem)
            }
        }
    }

    /**
     * Conservative fuzzy fallback for a query token with no exact posting: light-stem the token
     * (shared with the index-side stem structures), then take the terms of an exactly-matching
     * stem, else of stems within one edit (same first char, length ±1 — bucket-scanned). STT
     * garbles like "туждающие"→"труждающиеся" differ by 3+ raw edits from Russian inflection but
     * by exactly one on the stems ("туждающ"/"труждающ"). Capped at [MAX_FUZZY_EXPANSIONS].
     */
    fun expansions(token: String): List<String> {
        if (token.length < MIN_FUZZY_TOKEN_LEN) return emptyList()
        val stem = lightStem(token) ?: return emptyList()
        return stemToTerms[stem]?.take(MAX_FUZZY_EXPANSIONS)
            ?: (stem.length - 1..stem.length + 1).asSequence()
                .flatMap { len -> stemBuckets[stem.first() to len].orEmpty() }
                .filter { candidate -> withinOneEdit(stem, candidate) }
                .flatMap { candidate -> stemToTerms.getValue(candidate) }
                .take(MAX_FUZZY_EXPANSIONS)
                .toList()
    }
}

/** Light, language-symmetric stem: strip RU reflexive + trailing vowel endings / common EN
 *  suffixes while staying long — used identically at index build and query time. Null for
 *  tokens too short to stem safely. */
private fun lightStem(term: String): String? {
    if (term.length < MIN_FUZZY_TOKEN_LEN || term.any { it.isDigit() }) return null
    var s = term
    if (s.endsWith("ся") || s.endsWith("сь")) s = s.dropLast(2)
    when {
        s.endsWith("ing") && s.length > EN_SUFFIX_MIN_LEN -> s = s.dropLast("ing".length)
        s.endsWith("ed") && s.length > EN_SUFFIX_MIN_LEN -> s = s.dropLast(2)
        s.endsWith("s") && s.length > EN_SUFFIX_MIN_LEN -> s = s.dropLast(1)
    }
    while (s.length > MIN_STEM_LEN && s.last() in RU_STEM_TRIM) s = s.dropLast(1)
    return s.takeIf { it.length >= MIN_STEM_LEN }
}

/** Damerau-Levenshtein distance ≤ 1 (substitution, adjacent transposition, or one indel). */
private fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    val (s, t) = if (a.length <= b.length) a to b else b to a
    return when (t.length - s.length) {
        0 -> oneSubstitutionOrSwap(s, t)
        1 -> oneInsertionApart(s, t)
        else -> false
    }
}

/** Equal-length [s] and [t] differing in one place, or by one adjacent transposition. */
private fun oneSubstitutionOrSwap(s: String, t: String): Boolean {
    // More than two differences already rules both out, so the scan stops at the third.
    val diffs = s.indices.asSequence().filter { s[it] != t[it] }.take(3).toList()
    return when (diffs.size) {
        1 -> true
        2 -> diffs[0] + 1 < s.length && isAdjacentSwap(s, t, diffs[0])
        else -> false
    }
}

private fun isAdjacentSwap(s: String, t: String, at: Int): Boolean =
    s[at] == t[at + 1] && s[at + 1] == t[at] && s.substring(at + 2) == t.substring(at + 2)

/** [t], one character longer, is [s] with one character inserted. */
private fun oneInsertionApart(s: String, t: String): Boolean {
    var i = 0
    while (i < s.length && s[i] == t[i]) i++
    return s.substring(i) == t.substring(i + 1)
}
