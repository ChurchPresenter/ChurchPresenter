package engine.bible

import engine.Config
import kotlin.math.ln

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

    // Fuzzy-rescue structures (see fuzzyExpansions): light stem -> original index terms, plus
    // (first char, stem length) buckets so the distance-1 scan only touches a small candidate set.
    private val stemToTerms = HashMap<String, MutableSet<String>>()
    private val stemBuckets = HashMap<Pair<Char, Int>, MutableList<String>>()

    init {
        buildIndex()
        buildStemStructures()
    }

    private fun buildStemStructures() {
        for (term in invertedIndex.keys) {
            val stem = stemFor(term) ?: continue
            if (stemToTerms.getOrPut(stem) { LinkedHashSet() }.add(term) &&
                stemToTerms.getValue(stem).size == 1
            ) {
                stemBuckets.getOrPut(stem.first() to stem.length) { mutableListOf() }.add(stem)
            }
        }
    }

    private fun buildIndex() {
        var totalLen = 0L
        for (t in translations) {
            for (v in t.byBCV.values) {
                if (v.isHeader || v.text.isBlank()) continue
                val docId = documents.size
                val tokens = tokenize(v.text)
                if (tokens.isEmpty()) continue
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
        if (groups.isEmpty()) return emptyList()

        // Every query token must be matchable — by its exact term or a fuzzy expansion. A doc
        // qualifies when it contains at least one term from EVERY group (union within a group,
        // intersection across groups) — exact-only queries reduce to the old all-terms semantics.
        val groupPostings = groups.map { group -> group.filter { invertedIndex.containsKey(it.first) } }
        if (groupPostings.any { it.isEmpty() }) return emptyList()

        val docIdSets = groupPostings
            .map { group ->
                group.flatMapTo(HashSet()) { (term, _) -> invertedIndex.getValue(term).map { it.docId } }
            }
            .sortedBy { it.size }
        val candidates = docIdSets[0].toMutableSet()
        for (i in 1 until docIdSets.size) candidates.retainAll(docIdSets[i])
        if (candidates.isEmpty()) return emptyList()

        val scores = HashMap<Int, Double>(candidates.size * 2)
        for (group in groupPostings) {
            for ((term, weight) in group) {
                scoreTerm(term, weight, restrictTo = candidates, scores)
            }
        }
        return topResults(scores, topK)
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
            else fuzzyExpansions(token).map { it to FUZZY_WEIGHT }.ifEmpty { listOf(token to 1.0) }
        }

    /**
     * Conservative fuzzy fallback for a query token with no exact posting: light-stem the token
     * (shared with the index-side stem structures), then take the terms of an exactly-matching
     * stem, else of stems within one edit (same first char, length ±1 — bucket-scanned). STT
     * garbles like "туждающие"→"труждающиеся" differ by 3+ raw edits from Russian inflection but
     * by exactly one on the stems ("туждающ"/"труждающ"). Capped at [MAX_FUZZY_EXPANSIONS].
     */
    private fun fuzzyExpansions(token: String): List<String> {
        if (token.length < MIN_FUZZY_TOKEN_LEN) return emptyList()
        val stem = stemFor(token) ?: return emptyList()
        stemToTerms[stem]?.let { return it.take(MAX_FUZZY_EXPANSIONS) }
        val out = ArrayList<String>(MAX_FUZZY_EXPANSIONS)
        for (len in stem.length - 1..stem.length + 1) {
            val bucket = stemBuckets[stem.first() to len] ?: continue
            for (candidate in bucket) {
                if (withinOneEdit(stem, candidate)) {
                    for (term in stemToTerms.getValue(candidate)) {
                        out.add(term)
                        if (out.size >= MAX_FUZZY_EXPANSIONS) return out
                    }
                }
            }
        }
        return out
    }

    /** Light, language-symmetric stem: strip RU reflexive + trailing vowel endings / common EN
     *  suffixes while staying long — used identically at index build and query time. Null for
     *  tokens too short to stem safely. */
    private fun stemFor(term: String): String? {
        if (term.length < MIN_FUZZY_TOKEN_LEN || term.any { it.isDigit() }) return null
        var s = term
        if (s.endsWith("ся") || s.endsWith("сь")) s = s.dropLast(2)
        when {
            s.endsWith("ing") && s.length > 6 -> s = s.dropLast(3)
            s.endsWith("ed") && s.length > 6 -> s = s.dropLast(2)
            s.endsWith("s") && s.length > 6 -> s = s.dropLast(1)
        }
        while (s.length > 5 && s.last() in RU_STEM_TRIM) s = s.dropLast(1)
        return s.takeIf { it.length >= 5 }
    }

    /** Damerau-Levenshtein distance ≤ 1 (substitution, adjacent transposition, or one indel). */
    private fun withinOneEdit(a: String, b: String): Boolean {
        if (a == b) return true
        val (s, t) = if (a.length <= b.length) a to b else b to a
        return when (t.length - s.length) {
            0 -> {
                var firstDiff = -1
                var diffs = 0
                for (i in s.indices) {
                    if (s[i] != t[i]) {
                        if (diffs == 0) firstDiff = i
                        diffs++
                        if (diffs > 2) return false
                    }
                }
                diffs == 1 || (diffs == 2 && firstDiff + 1 < s.length &&
                    s[firstDiff] == t[firstDiff + 1] && s[firstDiff + 1] == t[firstDiff] &&
                    s.substring(firstDiff + 2) == t.substring(firstDiff + 2))
            }
            1 -> {
                var i = 0
                while (i < s.length && s[i] == t[i]) i++
                s.substring(i) == t.substring(i + 1)
            }
            else -> false
        }
    }

    fun tokenize(text: String): List<String> =
        // \p{L} + ё→е: see AgreementScorer.tokenize — index and query share this function,
        // so the normalization stays symmetric by construction.
        text.lowercase().replace('ё', 'е')
            .replace(Regex("[^\\p{L}0-9]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOPWORDS }

    companion object {
        // Fuzzy-rescue tuning: only long tokens are stemmed/expanded (short words collide),
        // expansions carry a score penalty, and at most 2 index terms join per garbled token.
        private const val MIN_FUZZY_TOKEN_LEN = 6
        private const val MAX_FUZZY_EXPANSIONS = 2
        private const val FUZZY_WEIGHT = 0.7
        private val RU_STEM_TRIM = "аеиоуыэюяйь".toSet()

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
