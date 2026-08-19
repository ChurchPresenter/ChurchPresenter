package engine.version

import engine.Config
import engine.bible.Script
import engine.engine.AgreementScorer
import kotlin.math.ln
import kotlin.math.min

/**
 * Scores one spoken verse against every version that has it, producing a per-version evidence delta.
 *
 * Plain word overlap cannot do this: ~80% of any verse is identical across translations, so the
 * shared bulk drowns the handful of words that actually distinguish them. Instead each token is
 * weighted by how RARE it is among the renderings of this one verse — the words every version shares
 * fall out to exactly zero, and what remains is the wording that identifies a version.
 *
 * Matthew 18:13 is the worked example: against KJV, NASB's distinctive set is
 * {turns, out, finds, truly, rejoices, over, more, have, gone} and KJV's is
 * {and, find, verily, unto, rejoiceth, sheep, went}. Hearing "if it turns out that he finds it"
 * lands squarely in the first set.
 *
 * Pure and stateless — [VersionDetector] holds the accumulation.
 */
object VersionScorer {

    /** Evidence contributed by one verse for one version. Negative means it argues *against*. */
    data class Delta(val id: String, val label: String, val value: Double)

    /**
     * @param candidates every version's rendering of the same verse
     * @param anchorText the DETECTING translation's rendering of that verse — the language anchor
     * @param spoken     the transcript window (never the translation track — see below)
     * @param script     dominant script of [spoken]
     * @return one delta per surviving candidate, or empty when this verse carries no usable signal.
     */
    fun deltas(
        candidates: List<VersionCandidate>,
        anchorText: String,
        spoken: String,
        script: Script,
    ): List<Delta> {
        if (spoken.isBlank() || candidates.isEmpty()) return emptyList()

        // Filter BEFORE the df counts below, not after: a candidate in another language contributes
        // tokens no one else has, which inflates the rarity of every genuine competitor's words.
        val anchorTokens = AgreementScorer.tokens(anchorText)
        val viable = candidates.filter { c ->
            c.script == script &&
                jaccard(AgreementScorer.tokens(c.text), anchorTokens) >= Config.versionCandidateMinJaccard
        }
        // With a single rendering there is nothing to be distinctive *against*; no weight is
        // definable, so the comparative path below cannot run. What IS reportable is the weaker
        // claim handled by [soleCandidate] — see Config's sole-candidate section.
        val n = viable.size
        if (n == 0) return emptyList()
        if (n == 1) return soleCandidate(viable[0], spoken)

        // The window must actually contain the verse. Partial windows are the dominant noise source
        // and are where the miss penalty below would misfire hardest — a version looks wrong merely
        // because the speaker hasn't reached the end of the verse yet.
        val covered = viable.any { AgreementScorer.coverage(it.text, spoken) >= Config.versionMinVerseCoverage }
        if (!covered) return emptyList()

        val tokenSets = viable.map { AgreementScorer.tokens(it.text) }
        val df = HashMap<String, Int>()
        for (set in tokenSets) for (t in set) df.merge(t, 1, Int::plus)

        // w(t) = ln(n / df(t)) / ln(n): exactly 0.0 for a token every version shares, exactly 1.0 for
        // one unique to a single version, whatever n is. The normalization is what keeps the floors in
        // Config meaningful as the user's bible folder grows or shrinks.
        val lnN = ln(n.toDouble())
        fun weight(t: String): Double = ln(n.toDouble() / (df[t] ?: 1)) / lnN

        // Tokens the speaker said that appear in NO version (STT noise, paraphrase, the preacher's own
        // words) have no df entry, are in no candidate's token set, and so enter neither sum below.
        // They are ignored by construction — no stopword list is needed here.
        val spokenTokens = AgreementScorer.tokens(spoken)
        var anyDistinctive = false
        val out = viable.mapIndexed { i, c ->
            var matched = 0.0
            var missed = 0.0
            for (t in tokenSets[i]) {
                val w = weight(t)
                if (w <= 0.0) continue
                anyDistinctive = true
                if (t in spokenTokens) matched += w else missed += w
            }
            Delta(c.id, c.label, min(matched, Config.versionMaxVerseDelta) - Config.versionMissPenalty * missed)
        }
        // A verse worded identically everywhere carries no signal at all. Return empty rather than a
        // row of zeroes, so it doesn't count toward the detector's minimum-verses gate — two such
        // verses are no more evidence than none.
        return if (anyDistinctive) out else emptyList()
    }

    /**
     * The one-rendering case: a flat vote for the only candidate, if the reading actually covers it.
     *
     * This is NOT the comparative claim the rest of this object makes, and it must not be mistaken
     * for one — nothing was ruled out, because there was nothing to rule out. It says "the only
     * translation installed for this language, and the speaker's words match it", which is what an
     * operator with a single-translation library wants on screen and cannot otherwise get.
     *
     * The coverage bar is higher than the comparative path's for a reason worth keeping: there, a
     * partial window that flatters the wrong candidate is corrected by the competitors it is scored
     * against. Here there are none, so nothing catches a bad window except the bar itself.
     */
    private fun soleCandidate(candidate: VersionCandidate, spoken: String): List<Delta> {
        if (!Config.versionSoleCandidateEnabled) return emptyList()
        val coverage = AgreementScorer.coverage(candidate.text, spoken)
        if (coverage < Config.versionSoleCandidateMinCoverage) return emptyList()
        return listOf(Delta(candidate.id, candidate.label, Config.versionSoleCandidateDelta))
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val union = a.size + b.size - a.count { it in b }
        return if (union == 0) 0.0 else a.count { it in b }.toDouble() / union
    }
}
