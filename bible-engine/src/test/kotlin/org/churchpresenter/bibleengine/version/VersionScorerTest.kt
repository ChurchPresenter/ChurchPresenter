package org.churchpresenter.bibleengine.version

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.Script
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionScorerTest {

    private var enabled = true
    private var coverage = 0.0
    private var penalty = 0.0
    private var cap = 0.0
    private var jaccard = 0.0
    private var soleEnabled = true
    private var soleCoverage = 0.0

    @BeforeTest fun snapshot() {
        enabled = Config.versionDetectionEnabled
        coverage = Config.versionMinVerseCoverage
        penalty = Config.versionMissPenalty
        cap = Config.versionMaxVerseDelta
        jaccard = Config.versionCandidateMinJaccard
        soleEnabled = Config.versionSoleCandidateEnabled
        soleCoverage = Config.versionSoleCandidateMinCoverage
    }

    @AfterTest fun restore() {
        Config.versionDetectionEnabled = enabled
        Config.versionMinVerseCoverage = coverage
        Config.versionMissPenalty = penalty
        Config.versionMaxVerseDelta = cap
        Config.versionCandidateMinJaccard = jaccard
        Config.versionSoleCandidateEnabled = soleEnabled
        Config.versionSoleCandidateMinCoverage = soleCoverage
    }

    private fun score(candidates: List<VersionCandidate>, spoken: String, anchor: String = KJV_MATT_18_13) =
        VersionScorer.deltas(candidates, anchor, spoken, Script.LATIN).associate { it.label to it.value }

    @Test fun `versions that word a verse identically produce no signal`() {
        // The null-report case: every token is shared, so every weight is exactly zero.
        val text = "For God so loved the world that he gave his only begotten Son."
        assertTrue(score(listOf(candidate("A", text), candidate("B", text)), text, anchor = text).isEmpty())
    }

    @Test fun `the NASB wording of Matthew 18 13 identifies NASB`() {
        val spoken = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        val d = score(listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)), spoken)
        assertTrue(d.getValue("NASB") > 0, "NASB should gain evidence, got ${d["NASB"]}")
        assertTrue(d.getValue("KJV") < 0, "KJV's unsaid distinctive words should count against it, got ${d["KJV"]}")
        assertTrue(d.getValue("NASB") > d.getValue("KJV"))
    }

    @Test fun `the KJV wording of the same verse identifies KJV`() {
        // Symmetry — guards against a scorer that merely favors one side's vocabulary.
        val spoken = "and if so be that he find it verily i say unto you he rejoiceth more of " +
            "that sheep than of the ninety and nine which went not astray"
        val d = score(listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)), spoken)
        assertTrue(d.getValue("KJV") > d.getValue("NASB"), "expected KJV to lead, got $d")
    }

    @Test fun `words belonging to no version are ignored`() {
        val base = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        // Deliberately none of these words appear in either rendering — "and", for instance, would
        // NOT be neutral, being a genuine KJV discriminator here.
        val noisy = "$base brother congregation microphone tuesday parking"
        val candidates = listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13))
        assertEquals(score(candidates, base), score(candidates, noisy))
    }

    @Test fun `a wordy version does not out-score a terse one that actually matched`() {
        // Length bias guard: without the miss penalty, the padded version collects more raw
        // distinctive mass simply by having more distinctive words to offer.
        val spoken = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        val padded = candidate(
            "PARA",
            NASB_MATT_18_13 + " Indeed the shepherd celebrates jubilantly throughout the entire " +
                "countryside summoning neighbours kinsfolk companions everywhere rejoicing abundantly",
        )
        val d = score(listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13), padded), spoken)
        assertTrue(d.getValue("NASB") > d.getValue("PARA"), "expected NASB over the padded text, got $d")
    }

    // ── The sole candidate ────────────────────────────────────────────────────
    //
    // A library with one bible in the language being read cannot produce a comparative answer, and
    // used to produce none at all. It reports the weaker claim instead: the only translation
    // installed, when the reading actually covers it. These pin both halves of that.

    @Test fun `the only version in the language is reported when the reading covers it`() {
        val d = score(listOf(candidate("KJV", KJV_MATT_18_13)), KJV_MATT_18_13)
        assertEquals(setOf("KJV"), d.keys, "got $d")
        assertEquals(Config.versionSoleCandidateDelta, d.getValue("KJV"))
    }

    @Test fun `a sole candidate the reading barely touches is not reported`() {
        // No competitor exists to correct a flattering partial window, so the bar is the only guard.
        val barelyRelated = "and he said unto them verily"
        assertTrue(
            score(listOf(candidate("KJV", KJV_MATT_18_13)), barelyRelated).isEmpty(),
            "a window this thin must not seat an answer",
        )
    }

    @Test fun `sole-candidate reporting can be switched off`() {
        Config.versionSoleCandidateEnabled = false
        assertTrue(score(listOf(candidate("KJV", KJV_MATT_18_13)), KJV_MATT_18_13).isEmpty())
    }

    @Test fun `a sole candidate is judged on its own coverage bar, not the comparative one`() {
        // The two bars move independently: raising the sole bar alone must silence it, which is what
        // proves it is not reading versionMinVerseCoverage.
        Config.versionSoleCandidateMinCoverage = 0.99
        Config.versionMinVerseCoverage = 0.1
        val partial = KJV_MATT_18_13.split(" ").take(6).joinToString(" ")
        assertTrue(score(listOf(candidate("KJV", KJV_MATT_18_13)), partial).isEmpty(), "got: $partial")
    }

    @Test fun `two versions still take the comparative path, not the sole-candidate one`() {
        // Guards the boundary: the flat sole-candidate vote must not leak into the n>=2 case, where
        // the answer has to come from rarity weighting.
        val spoken = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        val d = score(listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)), spoken)
        assertEquals(setOf("KJV", "NASB"), d.keys, "got $d")
        assertTrue(
            d.values.none { it == Config.versionSoleCandidateDelta },
            "a comparative score must not be the flat sole-candidate delta, got $d",
        )
    }

    @Test fun `a cross-script version is excluded before rarity is computed`() {
        // The filter must run BEFORE the df counts: a Russian rendering contributes tokens nobody
        // else has, which would inflate the apparent rarity of every English competitor's words.
        val spoken = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        val english = listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13))
        val russian = candidate(
            "RST",
            "и если случится найти ее то истинно говорю вам он радуется о ней более нежели о " +
                "девяноста девяти незаблудившихся",
            script = Script.CYRILLIC,
        )
        assertEquals(score(english, spoken), score(english + russian, spoken))
    }

    @Test fun `a window that does not contain the verse gets no vote`() {
        // Partial windows are the dominant noise source — and where the miss penalty misfires worst.
        assertTrue(score(listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)), "he finds it").isEmpty())
    }

    @Test fun `an unrelated-language version is excluded by content not by filename`() {
        val spoken = "if it turns out that he finds it truly i say to you he rejoices over it " +
            "more than over the ninety nine which have not gone astray"
        val english = listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13))
        val german = candidate(
            "LUT",
            "und wenn es sich begibt dass er es findet wahrlich ich sage euch er freut sich " +
                "darüber mehr als über die neunundneunzig die nicht verirrt waren",
        )
        assertEquals(score(english, spoken), score(english + german, spoken))
    }
}
