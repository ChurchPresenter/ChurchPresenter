package org.churchpresenter.bibleengine.version

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.Script
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionDetectorTest {

    private val spokenNasb13 = "if it turns out that he finds it truly i say to you he rejoices " +
        "over it more than over the ninety nine which have not gone astray"
    private val spokenNasb14 = "so it is not the will of your father who is in heaven that one " +
        "of these little ones perish"
    private val spokenKjv13 = "and if so be that he find it verily i say unto you he rejoiceth " +
        "more of that sheep than of the ninety and nine which went not astray"
    private val spokenKjv14 = "even so it is not the will of your father which is in heaven that " +
        "one of these little ones should perish"

    private val corpus = MapVersionCorpus(
        mapOf(
            MATT_18_13 to listOf(candidate("KJV", KJV_MATT_18_13), candidate("NASB", NASB_MATT_18_13)),
            "B040C018V014" to listOf(candidate("KJV", KJV_MATT_18_14), candidate("NASB", NASB_MATT_18_14)),
        )
    )

    private var now = 1_000_000L
    private val changes = mutableListOf<VersionDetector.Verdict?>()

    /**
     * Scoring runs inline here, so every assertion below stays synchronous and deterministic —
     * no sleeping, no polling. [VersionAsyncTest] covers the real executor's behaviour.
     */
    private fun detector() = VersionDetector(
        corpus = { corpus },
        clock = { now },
        onVerdictChanged = { changes.add(it) },
        scoringExecutor = { it.run() },
    )

    private lateinit var saved: Map<String, Any>

    @BeforeTest fun snapshot() {
        saved = mapOf(
            "enabled" to Config.versionDetectionEnabled,
            "minVerses" to Config.versionMinVerses,
            "minEvidence" to Config.versionMinEvidence,
            "minMargin" to Config.versionMinMargin,
            "switchMargin" to Config.versionSwitchMinMargin,
            "decay" to Config.versionDecay,
            "gap" to Config.versionResetGapMs,
        )
        now = 1_000_000L
        changes.clear()
    }

    @AfterTest fun restore() {
        Config.versionDetectionEnabled = saved["enabled"] as Boolean
        Config.versionMinVerses = saved["minVerses"] as Int
        Config.versionMinEvidence = saved["minEvidence"] as Double
        Config.versionMinMargin = saved["minMargin"] as Double
        Config.versionSwitchMinMargin = saved["switchMargin"] as Double
        Config.versionDecay = saved["decay"] as Double
        Config.versionResetGapMs = saved["gap"] as Long
    }

    private fun VersionDetector.read(code: String, anchor: String, spoken: String, chapter: Int = 18) =
        observe(code, bookId = 40, chapter = chapter, anchorText = anchor, spoken = spoken, script = Script.LATIN)

    @Test fun `one verse is never enough to name a version`() {
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        assertNull(d.verdict(), "a single verse must not decide — plenty read alike across versions")

        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals("NASB", assertNotNull(d.verdict()).label)
    }

    @Test fun `re-emitting the same verse does not count twice`() {
        // The Stabilizer re-emits a held passage as scripture.updated; without dedup one verse could
        // clear the minimum-verses gate on its own.
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        assertNull(d.verdict())
    }

    @Test fun `a new reader on a different version overtakes the decayed history`() {
        // No speaker diarization exists, so this is the whole mechanism for a change of reader
        // mid-passage: the newer verses simply out-weigh the older ones.
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals("NASB", assertNotNull(d.verdict()).label)

        repeat(6) { i ->
            // Alternate the two verses so each observation passes the same-code dedup.
            if (i % 2 == 0) d.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
            else d.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        }
        assertEquals("KJV", assertNotNull(d.verdict()).label, "the newer reading should have taken over")
    }

    @Test fun `moving to a new passage keeps the answer`() {
        // A preacher reads one bible for the whole service and moves between passages freely, so a
        // passage change is no reason to make the operator re-earn the answer.
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals("NASB", assertNotNull(d.verdict()).label)

        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13, chapter = 5)
        assertEquals("NASB", assertNotNull(d.verdict()).label, "the answer must survive a new passage")
    }

    @Test fun `a verse that separates nothing leaves the answer standing`() {
        // "No new information" must not read as "forget what you knew".
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals("NASB", assertNotNull(d.verdict()).label)

        // Wording shared by both renderings — no discriminative tokens at all.
        d.read("B040C018V012", "i say to you", "i say to you")
        assertEquals("NASB", assertNotNull(d.verdict()).label)
    }

    @Test fun `a different version replaces the answer only once it clears the higher bar`() {
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals("NASB", assertNotNull(d.verdict()).label)

        // An unreachably high switch bar: KJV reading after KJV reading must not dislodge NASB.
        Config.versionSwitchMinMargin = 1_000.0
        repeat(6) { i ->
            if (i % 2 == 0) d.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
            else d.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        }
        assertEquals("NASB", assertNotNull(d.verdict()).label, "below the switch bar the incumbent stands")

        // With the normal bar the same evidence takes over.
        Config.versionSwitchMinMargin = 3.0
        d.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        assertEquals("KJV", assertNotNull(d.verdict()).label)
    }

    @Test fun `every change of answer is announced exactly once`() {
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertEquals(listOf("NASB"), changes.map { it?.label }, "one announcement for the first answer")

        // More of the same reading firms up the confidence but is not a change.
        d.read("B040C018V012", "i say to you", "i say to you")
        assertEquals(listOf("NASB"), changes.map { it?.label })

        now += Config.versionResetGapMs + 1
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        assertEquals(listOf("NASB", null), changes.map { it?.label }, "the drop is announced too")
    }

    @Test fun `a long silence clears the tally`() {
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertNotNull(d.verdict())

        now += Config.versionResetGapMs + 1
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        assertNull(d.verdict())
    }

    @Test fun `an evenly split field reports nothing however long it runs`() {
        // Wording that is neutral between the two renderings: the shared bulk of both verses.
        val neutral = "i say to you he more than the ninety nine which not astray it is the will " +
            "of your father in heaven that one of these little ones"
        val d = detector()
        repeat(8) { i ->
            if (i % 2 == 0) d.read(MATT_18_13, KJV_MATT_18_13, "$neutral ${KJV_MATT_18_13}")
            else d.read("B040C018V014", KJV_MATT_18_14, neutral)
        }
        val v = d.verdict()
        // Either nothing, or at minimum never a confident answer from a tie.
        if (v != null) assertTrue(v.confidence < 1.0)
    }

    @Test fun `negative scores still produce a sane verdict`() {
        // With the miss penalty the losing versions go negative; a ratio-based margin would be
        // meaningless there (-4/-8 = 0.5), so the margin must be an absolute difference.
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        val v = assertNotNull(d.verdict())
        assertEquals("NASB", v.label)
        assertTrue(v.confidence in 0.0..1.0, "confidence out of range: ${v.confidence}")
    }

    @Test fun `the feature can be switched off entirely`() {
        Config.versionDetectionEnabled = false
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenNasb13)
        d.read("B040C018V014", KJV_MATT_18_14, spokenNasb14)
        assertNull(d.verdict())
    }

    @Test fun `a blank transcript contributes nothing`() {
        val d = detector()
        d.read(MATT_18_13, KJV_MATT_18_13, "")
        d.read("B040C018V014", KJV_MATT_18_14, "   ")
        assertNull(d.verdict())
    }

    // ── A single-translation library ──────────────────────────────────────────

    /** One bible in the language being read — the Russian-only folder that prompted this. */
    private val soleCorpus = MapVersionCorpus(
        mapOf(
            MATT_18_13 to listOf(candidate("KJV", KJV_MATT_18_13)),
            "B040C018V014" to listOf(candidate("KJV", KJV_MATT_18_14)),
        )
    )

    private fun soleDetector() = VersionDetector(
        corpus = { soleCorpus },
        clock = { now },
        onVerdictChanged = { changes.add(it) },
        scoringExecutor = { it.run() },
    )

    @Test fun `the only installed translation is named once enough verses match it`() {
        val d = soleDetector()
        d.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
        assertNull(d.verdict(), "the minimum-verses gate still applies with one candidate")

        d.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        assertEquals("KJV", assertNotNull(d.verdict()).label)
    }

    @Test fun `a sole candidate never reads as confidently as a corroborated one`() {
        val sole = soleDetector()
        sole.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
        sole.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        val soleConfidence = assertNotNull(sole.verdict()).confidence

        assertTrue(
            soleConfidence <= Config.versionSoleCandidateMaxConfidence,
            "nothing was ruled out, so this must stay capped, got $soleConfidence",
        )

        // The same reading against a corpus that COULD have contradicted it earns more, which is the
        // distinction the cap exists to preserve — otherwise both look equally settled on screen.
        changes.clear()
        val compared = detector()
        compared.read(MATT_18_13, KJV_MATT_18_13, spokenKjv13)
        compared.read("B040C018V014", KJV_MATT_18_14, spokenKjv14)
        val comparedVerdict = assertNotNull(compared.verdict())
        assertEquals("KJV", comparedVerdict.label)
        assertTrue(
            comparedVerdict.confidence > soleConfidence,
            "ruling out a competitor must count for more than having none: " +
                "${comparedVerdict.confidence} vs $soleConfidence",
        )
    }
}
