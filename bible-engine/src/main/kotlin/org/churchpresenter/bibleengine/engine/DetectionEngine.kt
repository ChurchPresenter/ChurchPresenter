package org.churchpresenter.bibleengine.engine

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.BibleIndex
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.detection.ContinuationEngine
import org.churchpresenter.bibleengine.detection.ReferenceWatcher
import org.churchpresenter.bibleengine.detection.ReverseLookup
import org.churchpresenter.bibleengine.version.VersionCorpus
import org.churchpresenter.bibleengine.version.VersionDetector
import kotlinx.serialization.Serializable

@Serializable
data class ScriptureReference(
    val bookId: Int,
    val bookName: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int?,
    val displayRef: String,
    val canonicalCodeStart: String,
    val canonicalCodeEnd: String?,
    val numbering: String,
)

@Serializable
data class ScriptureEvent(
    val type: String,
    val id: String,
    val reference: ScriptureReference,
    val verseText: String,
    val confidence: Double,
    val matchType: String,
    val translation: String,
    // STT segment that triggered this detection. Clock-free correlation key shared by the STT
    // transcript rows, the detection log, and the operator's go-live log. Null when the STT stream
    // doesn't provide it (older schema).
    val segmentId: String? = null,
    val sttStartTime: Double? = null,
    // Stable per-service session id from STT — the exact join key shared by the STT db, the detection
    // log and the CP live-references log. Null when the STT stream doesn't provide it (older schema).
    val sessionId: String? = null,
    // Which STT track(s) corroborate this detection — subset of {"transcription","translation"}.
    // A corroboration/confidence signal for the UI: both present = strongest.
    val tracks: List<String> = emptyList(),
    // ── Bible version detection (report only; never influences which verse is detected) ──
    // Which TRANSLATION the speaker appears to be reading from, scored across every bible in the
    // folder — not just the two indexed for detection, so this is frequently a version the operator
    // has not loaded and is NOT where [verseText] came from (that is [translation]). Null whenever
    // the evidence is thin or the field is too close to call.
    val detectedVersion: String? = null,
    val detectedVersionId: String? = null,
    val detectedVersionConfidence: Double? = null,
    // ── Diagnostics (logged for training; not used by the UI) ──
    val tier: Int? = null,             // explicit-ref tier (1/2); null for reverse/continuation
    val bm25Score: Double? = null,     // reverse: top BM25 score
    val bm25Ratio: Double? = null,     // reverse: top-1/top-2 score ratio (margin over runner-up)
    val speechType: String? = null,    // STT speech_type at decision time (Speaking/Quiet/Music)
    val stickyBook: Int? = null,       // sticky context book when this fired
    val stickyChapter: Int? = null,    // sticky context chapter when this fired
)

private const val MAX_UTTERANCES = 128

class DetectionEngine(
    private val translations: List<EngineTranslation>,
    // Injectable time source so the replay harness (DbReplayTest) can drive every time-dependent
    // gate (sticky TTL, dedup TTL, continuation timeout, re-emit cooldown) from recorded ts_ms
    // values — making a replayed session fully deterministic. Production uses the wall clock.
    private val clock: () -> Long = System::currentTimeMillis,
    // Every version the engine can RECOGNIZE, which is wider than the translations above (those are
    // the two ChurchPresenter selected for detection). Supplied as a function because the corpus is
    // indexed on a background thread at startup — until it lands this is EMPTY and no version is
    // ever reported, which is harmless. Defaulted so replay and tests construct the engine unchanged.
    private val versionCorpus: () -> VersionCorpus = { VersionCorpus.EMPTY },
    // Notified when the detected READING VERSION changes. Pushed to clients separately from the
    // scripture events because it is settled asynchronously, usually a verse or two after the
    // detection that first hinted at it — so it can never ride the event that produced it.
    onVersionChanged: (VersionDetector.Verdict?) -> Unit = {},
    // Injectable so tests can score inline and assert without racing the scoring thread. Null means
    // the detector runs its own thread, which is what production wants.
    versionScoringExecutor: java.util.concurrent.Executor? = null,
) {

    // Index every loaded translation by default; an explicit Config.defaultTranslations allow-list
    // can restrict it (e.g. to cap memory when many large translations are present).
    private val indexTranslations =
        if (Config.defaultTranslations.isEmpty()) translations
        else translations.filter { it.id in Config.defaultTranslations }.ifEmpty { translations }
    private val index = BibleIndex(indexTranslations)
    private val stabilizer = Stabilizer(clock)
    private val versionDetector =
        if (versionScoringExecutor == null) VersionDetector(versionCorpus, clock, onVersionChanged)
        else VersionDetector(versionCorpus, clock, onVersionChanged, versionScoringExecutor)
    private val events = DetectionEvents(translations)
    private val recorder = DetectionRecorder(clock, versionDetector)
    // Access-ordered LRU bound: the STT path only ever uses the single id "live", but the
    // direct-WS input path takes caller-supplied ids with no natural end — a long-lived
    // standalone server must not grow without bound. All access is confined to the single
    // detection thread (see EngineServer), so a plain LinkedHashMap is safe.
    private val utterances = object : LinkedHashMap<String, UtteranceState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UtteranceState>): Boolean =
            size > MAX_UTTERANCES
    }


    fun processTranscription(
        id: String,
        text: String,
        speechType: String? = null,
        segmentId: String? = null,
        startTime: Double? = null,
        sessionId: String? = null,
    ): List<ScriptureEvent> {
        val state = utterances.getOrPut(id) { UtteranceState(id) }
        state.transcript = text
        if (speechType != null) state.speechType = speechType
        if (segmentId != null) state.segmentId = segmentId
        if (startTime != null) state.sttStartTime = startTime
        applySessionId(state, sessionId)
        state.updatedAt = clock()
        return runDetection(state)
    }

    fun processTranslation(
        id: String,
        text: String,
        speechType: String? = null,
        segmentId: String? = null,
        startTime: Double? = null,
        sessionId: String? = null,
    ): List<ScriptureEvent> {
        val state = utterances.getOrPut(id) { UtteranceState(id) }
        state.translation = text
        if (speechType != null) state.speechType = speechType
        if (segmentId != null) state.segmentId = segmentId
        if (startTime != null) state.sttStartTime = startTime
        applySessionId(state, sessionId)
        state.updatedAt = clock()
        return runDetection(state)
    }

    /** Stores the STT session id on the utterance and points the detection log at the matching file. */
    private fun applySessionId(state: UtteranceState, sessionId: String?) {
        if (sessionId != null) {
            state.sessionId = sessionId
            DetectionLogger.sessionId = sessionId
        }
    }

    /**
     * The three detection paths in order of trust. Each answers null to hand on to the next; an
     * empty list stops the run with nothing emitted.
     */
    private fun runDetection(state: UtteranceState): List<ScriptureEvent> {
        val now = clock()
        return detectRefs(state, now)
            ?: detectReverse(state)
            ?: detectContinuation(state, now)
            ?: emptyList()
    }

    /**
     * 1. Explicit / sticky references (stateful watcher). May yield several per utterance.
     *    Suppressed on music segments (sung lyrics aren't references being looked up).
     */
    private fun detectRefs(state: UtteranceState, now: Long): List<ScriptureEvent>? {
        val prevWatchBook = state.watchBook
        val prevWatchChapter = state.watchChapter
        val refs = ReferenceWatcher.process(
            state.transcript, state.translation, state, now, isMusic = isMusic(state.speechType),
        )
        // Trace every sticky change — even when nothing emits — so an unexpected jump (e.g. a stale
        // sticky with no corresponding logged detection) can be diagnosed after the fact.
        if (state.watchBook != prevWatchBook || state.watchChapter != prevWatchChapter) {
            DetectionLogger.logStickyChange(
                state.transcript, state.translation,
                StickyChange(prevWatchBook, prevWatchChapter, state.watchBook, state.watchChapter),
            )
        }
        // Remember every chapter the sticky has pointed at this service (book+chapter-only
        // announcements included, even though those no longer emit a Ref — see ReferenceWatcher.emit)
        // so a later verse mention can resolve against any of them, not just the current one.
        val book = state.watchBook
        val chapter = state.watchChapter
        if (book != null && chapter != null) state.touchChapterHistory(book, chapter)

        val emitted = refs.mapNotNull { ref -> events.buildRefEvent(state, ref)?.let { decide(state, it) } }
        return if (emitted.isNotEmpty()) recorder.logged(state, emitted) else null
    }

    /**
     * 2. Reverse BM25 lookup (gated by the client-selected level), validated against what was
     *    actually spoken so a spurious BM25 hit on a single rare word can't fire.
     *    Searched PER TRACK: ReverseLookup keeps only the last reverseWindowWords of its
     *    query, so searching the concatenated transcript+translation structurally discarded
     *    the transcript whenever the translation track was active (the tail was always
     *    English) — the best gated hit across the two tracks wins instead.
     */
    private fun detectReverse(state: UtteranceState): List<ScriptureEvent>? {
        val reverse = (if (Config.reverseEnabled) bestReverseHit(state, index, translations) else null) ?: return null
        val t = translations.find { it.id == reverse.translationId }
        val hit = t?.lookupVerse(reverse.bookNum, reverse.chapter, reverse.verse)
        if (t == null || hit == null) return emptyList()
        val verse = events.passageStart(t, hit, state)
        val agreement = AgreementScorer.score(verse.text, state.transcript, state.translation)
        val event = events.buildEvent(
            id = state.id,
            verse = verse,
            translation = t,
            confidence = reverse.confidence,
            matchType = "reverse",
        ).copy(bm25Score = reverse.score, bm25Ratio = reverse.ratio.takeIf { it.isFinite() && it < 1e6 })
        return if (agreement < Config.reverseMinAgreement) {
            // BM25 hit that didn't share enough spoken words to fire — the prime near-miss to study.
            recorder.logCandidate(state, event, "low-agreement")
            null
        } else {
            decide(state, event)?.let { recorder.logged(state, listOf(it)) }
        }
    }

    /**
     * 3. Continuation — sequential next-3 from a confirmed verse first (cheap, precise); when
     *    that doesn't apply or doesn't find a match (no verse confirmed yet, or a jump further
     *    than 3 verses within the same chapter), fall back to scoring every verse in the known
     *    sticky chapter, so a bare "book + chapter" announcement doesn't need an explicit verse
     *    citation to be found once the reading actually starts.
     */
    private fun detectContinuation(state: UtteranceState, now: Long): List<ScriptureEvent>? {
        val sequential = ContinuationEngine.check(state, translations, now)
        val cont = sequential
            ?: ContinuationEngine.checkChapterScope(state, events.pickTranslation(state), now)
            ?: return null
        // Kept distinct from "continuation" in the detection log so the paths stay visually
        // separable for training/triage: a chapter-wide scan is a materially different signal
        // than the cheap sequential-next-verse check, and matching a DIFFERENT, earlier chapter
        // via history (a preacher revisiting a passage) is rarer/riskier than matching the
        // chapter we're already expecting — worth telling apart in the log.
        val matchType = when {
            sequential != null -> "continuation"
            cont.verse.bookNum == state.watchBook && cont.verse.chapter == state.watchChapter -> "chapter-scan"
            else -> "chapter-history"
        }
        val event = events.buildEvent(
            id = state.id,
            verse = cont.verse,
            translation = cont.translation,
            confidence = cont.confidence,
            matchType = matchType,
        ).copy(type = "scripture.continuation")
        // A continuation goes out as built, whether the stabilizer calls it new or updated.
        return decide(state, event, retype = false)?.let { recorder.logged(state, listOf(it)) }
    }

    /**
     * Puts [event] past the stabilizer, feeds it to version scoring, and records it when it goes out
     * or logs it as a near-miss when suppressed. Returns what goes out, or null. With [retype] an
     * update is sent as `scripture.updated`; without, the event keeps its own type.
     */
    private fun decide(state: UtteranceState, event: ScriptureEvent, retype: Boolean = true): ScriptureEvent? {
        val decision = stabilizer.evaluate(refKey(event), event.confidence)
        recorder.observeVersion(state, event)
        val out = if (retype) {
            decision.toEvent(event)
        } else {
            event.takeIf { decision !is Stabilizer.EmitDecision.Suppress }
        }
        if (out != null) {
            recordDetection(state, out)
        } else if (decision is Stabilizer.EmitDecision.Suppress) {
            recorder.logCandidate(state, event, decision.reason)
        }
        return out
    }

    /** Stops the version scoring thread. */
    fun shutdown() {
        versionDetector.shutdown()
    }

    private fun recordDetection(state: UtteranceState, event: ScriptureEvent) {
        state.lastDetected = UtteranceLastRef(
            bookNum = event.reference.bookId,
            chapter = event.reference.chapter,
            verseStart = event.reference.verseStart,
        )
        state.lastDetectedAt = clock()
        state.lastTranslationId = translations.find { it.abbreviation == event.translation }?.id ?: ""
        state.lastConfidence = event.confidence
    }
}

private fun isMusic(speechType: String?): Boolean = speechType.equals("Music", ignoreCase = true)

/** The better gated reverse hit of the two tracks, each searched on its own; null when neither has one. */
private fun bestReverseHit(
    state: UtteranceState,
    index: BibleIndex,
    translations: List<EngineTranslation>,
): ReverseLookup.ReverseResult? = listOfNotNull(
    state.transcript.takeIf { it.isNotBlank() }?.let { ReverseLookup.search(it, index, translations) },
    state.translation.takeIf { it.isNotBlank() }?.let { ReverseLookup.search(it, index, translations) },
).maxByOrNull { it.confidence * CONFIDENCE_RANK_WEIGHT + it.score }

/** Ranks reverse hits by confidence first, the BM25 score only breaking ties. */
private const val CONFIDENCE_RANK_WEIGHT = 1_000_000

/** Maps a stabilizer decision to the event to emit (with the right type), or null to suppress. */
private fun Stabilizer.EmitDecision.toEvent(event: ScriptureEvent): ScriptureEvent? = when (this) {
    is Stabilizer.EmitDecision.NewDetection -> event
    is Stabilizer.EmitDecision.UpdatedDetection -> event.copy(type = "scripture.updated")
    is Stabilizer.EmitDecision.Suppress -> null
}

private fun refKey(event: ScriptureEvent): String =
    "${event.reference.bookId}:${event.reference.chapter}:${event.reference.verseStart}"
