package engine

object Config {
    var bibleRoot: String = System.getProperty("bible.root", "")
    var sttServerUrl: String = ""
    var outputPort: Int = 8765

    // Routine lifecycle chatter (STT/WS connect+disconnect) is OFF by default so the host app's
    // terminal stays quiet; enable with -Dengine.verbose=true for connectivity debugging. Genuine
    // errors (System.err — bind/parse/connection failures) are always printed regardless.
    var verboseLog: Boolean = System.getProperty("engine.verbose")?.toBooleanStrictOrNull() ?: false

    // Optional BM25 allow-list. Empty = index every SPB found in the bible folder (the default);
    // set specific ids only to cap memory when many large translations are present.
    val defaultTranslations = emptyList<String>()

    // Detection tuning (var ones are runtime-settable via the `set_tuning` WebSocket message)
    var reverseMinScoreRatio = 2.0
    // A reverse (BM25) hit must also share at least this much word-overlap with what was actually
    // spoken (transcript + translation), so a match on a single rare token can't fire on its own.
    var reverseMinAgreement = 0.15
    // Min fraction of a verse's words that must appear in a track (transcript / translation) for that
    // track to be marked as corroborating the detection (the per-chip transcription/translation icons).
    var trackCoverageMin = 0.4
    // Near-miss candidate logging (training data): log detections the engine BUILT but did NOT emit
    // (below the confidence threshold, deduped, or failed reverse-agreement), so false negatives are
    // visible for tuning. Floored to avoid noise. Toggle off with -Dengine.logCandidates=false.
    var logCandidates = System.getProperty("engine.logCandidates")?.toBooleanStrictOrNull() ?: true
    var candidateLogMinConfidence = 0.15
    // Traces every sticky book/chapter change to sticky-log-*.jsonl, even when nothing emits — the
    // diagnostic for an unexplained stale/wrong sticky that never produced a logged detection.
    // Low-volume (sticky changes are infrequent, not per-utterance); same default spirit as logCandidates.
    var logStickyChanges = System.getProperty("engine.logStickyChanges")?.toBooleanStrictOrNull() ?: true
    const val continuationTimeoutMs = 30_000L

    // Sequential continuation acceptance: fraction of the CANDIDATE VERSE's words that must be
    // present in the text window ("verse-side coverage", AgreementScorer.coverage). Verse-side —
    // not query-side — because the 2-segment sliding window dilutes a query-normalized overlap to
    // a ~50% ceiling even when a verse is read verbatim (the documented sequential-reading FN
    // class; Matthew 9:37 in an earlier session). Verses with < 4 distinct scoring words must
    // be fully covered instead (spurious-full-coverage guard).
    //
    // User-facing knob: this is the ONLY tuning constant behind the "Verse speed"
    // chip (BibleTab.kt) — see applyContinuationSpeed below. "Balanced" keeps this at the
    // original 0.5; "Fast" drops it to 0.45, chosen from a real sweep (0.5/0.45/0.4/0.35/0.3)
    // across 4 archived sessions (85 ground-truth references total): 0.45 was the only value
    // that improved recall (62->63/85) with ZERO added detection volume in 3 of 4 sessions and
    // no regressions anywhere. 0.4 was a net wash (gained 1 match, lost a different 1 elsewhere —
    // check() returns the FIRST next-candidate crossing the floor, not the best one, so a looser
    // floor can occasionally lock onto the wrong verse). 0.35/0.3 matched or slightly beat 0.45's
    // recall but with meaningfully more continuation emission churn (chip re-fire volume) for
    // little/no extra correct verses. Provisional like every other floor in this file — revisit
    // once more sessions accumulate real data (TRAINING_PLAN.md has the full sweep table).
    var continuationMinCoverage = 0.5
    const val dedupWindow = 32
    // Suppress an identical reference only within this window (time-based, replaces the old fixed
    // count-only window) so a passage read again later can re-fire.
    var dedupTtlMs = 45_000L
    // Re-emission churn control for a held passage. A reference already showing only re-emits as an
    // "updated" event when its confidence moves by at least [reEmitMinDelta] AND at least
    // [reEmitCooldownMs] has passed since the last (new or updated) emission. The reverse-lookup
    // confidence oscillates as the window slides, which previously re-presented the same verse many
    // times (Иакова 2:19 fired 11× in one service); these bound it to at most once per cooldown.
    var reEmitMinDelta = 0.15
    var reEmitCooldownMs = 10_000L
    var minConfidenceEmit = 0.4
    const val bm25K1 = 1.5
    const val bm25B = 0.75
    const val reverseWindowWords = 25
    const val reverseTopK = 10

    // Chapter-scoped continuation: once book+chapter is known (the sticky), score every verse in that
    // chapter against what was spoken instead of requiring an explicit verse citation. The candidate
    // pool is already narrowed to one chapter (~10-50 verses), so this can use a lower agreement floor
    // than the global reverse lookup without raising false-positive risk — protected instead by the
    // margin-over-runner-up gate below (same safety pattern as reverseMinScoreRatio). Starting values;
    // tune against real training data.
    // Raised 0.10 -> 0.20: a recorded session's replay showed 94 chapter-history
    // emissions with ZERO true positives at the old floor — pure operator chip spam. Values below
    // remain provisional until more services are recorded; the structural gates (candidate cap +
    // coverage floors) carry most of the cut.
    var chapterScopeMinAgreement = 0.20
    var chapterScopeMinRatio = 1.5

    // Chapter-scope candidate pool: only the current sticky chapter plus the N most recently
    // visited chapters — a sermon's active context, not every chapter touched all service
    // (chapterHistory itself stays unbounded for diagnostics; only the SCAN is capped).
    var chapterHistoryMaxCandidates = 5

    // Retired: revisiting an EARLIER chapter without restating it turned out not to earn
    // its keep. Across ten recorded services the operator never once accepted a `chapter-history`
    // suggestion, and across the eight replayable ones the tier produced a single true positive; an
    // earlier replay had already shown 94 emissions with zero. The pool now holds the current
    // sticky chapter only, which is the case that actually fires (`chapter-scan`: 12 of 13 correct).
    // Kept as a flag rather than deleted — the mechanism is sound and cheap to re-enable if a service
    // ever shows a preacher genuinely working two chapters at once; turn it on and check
    // `chapter-history`-tagged rows specifically before trusting it.
    var chapterHistoryEnabled = false

    // Verse-side coverage floors for the chapter-scope match (same metric as
    // continuationMinCoverage): the top candidate verse must be substantially PRESENT in the
    // window, not merely share a few words. Stricter for a chapter other than the current
    // sticky — revisiting an earlier chapter is rarer and riskier than matching the chapter
    // we're already expecting (see ContinuationEngine doc).
    var chapterScopeMinCoverage = 0.45
    var chapterHistoryMinCoverage = 0.6

    // How long an announced book+chapter stays "sticky" for verse-by-verse reading. Generous by
    // default because expositional reads span minutes; shrunk for aggressive/rapid-fire cadence.
    var stickyTtlMs = 180_000L

    // Gates the BM25 reverse (text) lookup. Explicit parsing + continuation always run.
    var reverseEnabled = true

    // Precision gate (independent of the aggressiveness level): when the STT engine labels a segment
    // as music, skip explicit/sticky reference detection on it. Sung lyrics quote scripture but are
    // not references being looked up, and they must not seed the sticky context. Requires a reliable
    // speech_type from the STT stream; safe to disable if that signal is unreliable.
    var suppressDuringMusic = true

    // ── Aggressiveness-gated recall toggles (set by applyLevel) ───────────────────
    // Risky inferences ride the existing level chip rather than being on unconditionally.
    // Defaults mirror BALANCED so a fresh Config (no applyLevel) behaves as the default level.

    // Fold STT spelling variants before book resolution (Cyrillic э→е so "эфесянам"→"ефесянам").
    // Cheap and almost always harmless (most э-words are fillers), but off at CONSERVATIVE.
    var normalizeStt: Boolean = true

    // Allow a Book named AFTER its chapter/verse numbers in the same utterance to attach to them
    // (e.g. "14 стих 3 главы … Матфея" → Matt 3:14) instead of flushing them as sticky. Higher
    // false-positive risk → AGGRESSIVE only.
    var inferBookAtEnd: Boolean = false

    // The active aggressiveness level name (off / conservative / balanced / aggressive), recorded on
    // each logged detection so a row self-describes which tuning produced it (it can change mid-session).
    var level: String = "balanced"

    // Translation ids actually indexed this session (e.g. ["RUS_RST","ENG_KJV"]) — logged in the
    // per-session header so a service's results are tied to the Bibles that produced them.
    var loadedBibles: List<String> = emptyList()

    /**
     * The tuning one aggressiveness level applies. The values are the levels themselves — see the
     * per-field comments above for what each one gates.
     */
    private data class LevelPreset(
        val reverseEnabled: Boolean,
        val minConfidenceEmit: Double,
        val reverseMinScoreRatio: Double,
        val stickyTtlMs: Long,
        val normalizeStt: Boolean,
        val inferBookAtEnd: Boolean,
    )

    private val levelPresets = mapOf(
        // "off" keeps the numeric floors it already had; only the gates go down.
        "off" to LevelPreset(false, minConfidenceEmit, reverseMinScoreRatio, stickyTtlMs, false, false),
        "conservative" to LevelPreset(true, 0.6, 2.5, 240_000L, normalizeStt = false, inferBookAtEnd = false),
        "balanced" to LevelPreset(true, 0.4, 2.0, 180_000L, normalizeStt = true, inferBookAtEnd = false),
        "aggressive" to LevelPreset(true, 0.3, 1.5, 90_000L, normalizeStt = true, inferBookAtEnd = true),
    )

    /** Maps the client's aggressiveness level to reverse-lookup tuning + gated recall. */
    fun applyLevel(level: String) {
        this.level = level.lowercase()
        val preset = levelPresets[level.lowercase()] ?: return
        reverseEnabled = preset.reverseEnabled
        minConfidenceEmit = preset.minConfidenceEmit
        reverseMinScoreRatio = preset.reverseMinScoreRatio
        stickyTtlMs = preset.stickyTtlMs
        normalizeStt = preset.normalizeStt
        inferBookAtEnd = preset.inferBookAtEnd
    }

    // The active "Verse speed" preset name, recorded on each logged detection the same way
    // `level` is — self-describing rows, independent of the aggressiveness level above (a
    // session can change either one mid-service without affecting the other).
    var continuationSpeed: String = "balanced"

    /**
     * Maps the client's "Verse speed" preset to [continuationMinCoverage] — the ONLY constant
     * this touches. Deliberately separate from [applyLevel]: the aggressiveness level never
     * changed this floor (checked directly against the code before this knob was added), and an
     * unrecognized name is a silent no-op, matching [applyLevel]'s existing behavior.
     */
    fun applyContinuationSpeed(speed: String) {
        this.continuationSpeed = speed.lowercase()
        continuationMinCoverage = when (speed.lowercase()) {
            "balanced" -> BALANCED_MIN_COVERAGE
            "fast" -> FAST_MIN_COVERAGE
            else -> return
        }
    }

    /** The two "Verse speed" presets — see [continuationMinCoverage] for where 0.45 comes from. */
    private const val BALANCED_MIN_COVERAGE = 0.5
    private const val FAST_MIN_COVERAGE = 0.45

    // ── Bible version detection ───────────────────────────────────────────────────
    // Which TRANSLATION the speaker is reading from, scored across every .spb in the bible root
    // (not just the two ChurchPresenter selected — the reader's version is often one the operator
    // hasn't loaded). REPORT ONLY: nothing here ever feeds back into which verse is detected.
    //
    // Every value below is provisional and unswept — unlike the detection floors above, no recorded
    // service has been scored against ground truth yet. They are reasoned defaults, deliberately
    // strict, on the principle that reporting nothing beats reporting a coin flip.

    var versionDetectionEnabled = System.getProperty("engine.versionDetection")?.toBooleanStrictOrNull() ?: true

    // Versions actually recognizable this session, logged in the per-session header. Without it a row
    // naming "NASB" can't be read back later: what the engine could possibly have answered depends
    // entirely on which files happened to be in the user's folder at the time.
    var versionCorpusLabels: List<String> = emptyList()

    // Cap on .spb files indexed for version scoring. A seek index is ~250 KB per bible, so this is a
    // runaway guard for an absurd folder, not a budget — set generously ON PURPOSE. The cap is a
    // filename-sorted prefix, and the folder walk is recursive: a tight cap silently truncates to an
    // alphabetical slice (16 over a real 71-file collection kept ACV through EMTV and dropped the
    // KJV and the NASB), which makes the right answer unreachable rather than merely unlikely.
    // ChurchPresenter's own selected bibles are always indexed regardless of this.
    var versionMaxCorpusBibles = 96

    // Above this token-Jaccard two modules are treated as the SAME version and collapsed into one
    // group. Without this, near-duplicate files of one translation (this repo's dev machine carries
    // two ~97%-identical RST modules) split the vote and tie forever, so every service reports
    // nothing. Genuinely distinct family members (KJV vs NKJV, ~0.7-0.85) stay separate and compete.
    var versionDuplicateJaccard = 0.95

    // Language coherence floor vs the DETECTING translation's rendering of the same verse. Separates
    // English from German/French/Spanish, which all share Script.LATIN. Content-based because the
    // `language` field is filename-derived and unreliable (see SpbLoader.extractLanguage).
    var versionCandidateMinJaccard = 0.15

    // The text window must actually contain the verse before that verse gets a vote. Partial windows
    // are the dominant noise source and are exactly where the miss penalty below misfires. A side
    // effect worth stating: tier-1 explicit references contribute nothing, because they fire on
    // "turn to John 3:16" before any verse text has been spoken. That is correct.
    var versionMinVerseCoverage = 0.55

    // Alpha: weight of NEGATIVE evidence (a distinctive word this version has that the speaker did
    // NOT say) relative to positive. Needed at all because positive-only scoring has a length bias —
    // a wordier rendering has more distinctive tokens and so more chances to collect noise. Held to
    // half because absence has many innocent explanations (partial window, dropped word, the reader
    // paraphrased or stumbled) while presence has essentially one.
    var versionMissPenalty = 0.5

    // Per-verse cap on positive evidence, so one long highly-divergent verse can't decide a service.
    var versionMaxVerseDelta = 8.0

    // Per-verse decay on the running tally: ~6.7-verse memory. This is what handles a change of
    // reader mid-service — there is no speaker diarization in the STT feed, so a new reader with a
    // different bible re-converges by out-weighing the decayed history rather than by being detected.
    var versionDecay = 0.85

    // No single verse may decide a version — many verses are word-identical across translations.
    var versionMinVerses = 2

    // Absolute floors on the verdict, in units of "distinctive words of evidence" (the weighting is
    // normalized so a word unique to one version scores 1.0 regardless of corpus size).
    var versionMinEvidence = 3.0
    // Margin over the runner-up to SEAT an answer. An absolute DIFFERENCE, never a ratio: with
    // negative evidence the scores go negative, where a ratio is meaningless (-4/-8 = 0.5; 6/-2 = -3).
    var versionMinMargin = 2.0

    // Margin required to REPLACE a standing answer with a different version — deliberately above
    // versionMinMargin. The answer is sticky (a preacher reads one bible for a whole service and
    // moves between passages freely), so changing what the operator is already looking at should
    // take more evidence than putting it there did; otherwise a couple of ambiguous verses flip it
    // back and forth. Weak evidence never un-publishes an answer at all — a verse that fails to
    // separate the candidates means "no new information", not "forget what you knew".
    var versionSwitchMinMargin = 3.0

    // Silence long enough to be a different part of the service — drop the tally and the answer.
    // This, not a passage change, is what ends stickiness.
    var versionResetGapMs = 120_000L

    // ── Sole-candidate reporting ──────────────────────────────────────────────────
    //
    // A library with ONE bible in the language being read (a Russian-only folder is the common case)
    // can never produce a comparative answer: the scoring below weighs each word by how rare it is
    // among the renderings of a verse, and with one rendering there is no rarity. Reported nothing,
    // forever, and looked like a broken feature.
    //
    // What is reportable there is a WEAKER claim, and these knobs keep it honest: not "this version
    // beat the others" but "the only translation installed for this language, and the words match".
    // Nothing was ruled out, so the bar for saying it is higher and the confidence it can reach is
    // capped — see versionSoleCandidateMaxConfidence.
    var versionSoleCandidateEnabled = System.getProperty("engine.versionSoleCandidate")
        ?.toBooleanStrictOrNull() ?: true

    // Deliberately above versionMinVerseCoverage: that floor exists to stop a partial window voting
    // in a race between candidates, where a wrong guess is corrected by the competitors. Here there
    // is no competitor to correct anything, so the reading itself has to carry the claim.
    var versionSoleCandidateMinCoverage = 0.75

    // Flat per-verse evidence. Not a weighted sum — with one rendering every weight is 0/0. Sized so
    // that versionMinVerses qualifying verses clear versionMinEvidence under versionDecay and no
    // fewer do: 2.0 + 2.0*0.85 = 3.7 at two verses, 2.0 at one.
    var versionSoleCandidateDelta = 2.0

    // A comparative answer earns its confidence from the margin over a runner-up. A sole candidate
    // has no runner-up, so its margin is just its own tally and the usual curve would read ~0.7-0.9
    // for what is really "nothing contradicted it". Held to the bottom half of the scale.
    var versionSoleCandidateMaxConfidence = 0.5
}
