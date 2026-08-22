package org.churchpresenter.bibleengine.engine

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.BibleIndex
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
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

    private fun isMusic(speechType: String?): Boolean = speechType.equals("Music", ignoreCase = true)

    private fun runDetection(state: UtteranceState): List<ScriptureEvent> {
        val now = clock()

        // 1. Explicit / sticky references (stateful watcher). May yield several per utterance.
        //    Suppressed on music segments (sung lyrics aren't references being looked up).
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
                prevWatchBook, prevWatchChapter, state.watchBook, state.watchChapter,
            )
        }
        // Remember every chapter the sticky has pointed at this service (book+chapter-only
        // announcements included, even though those no longer emit a Ref — see ReferenceWatcher.emit)
        // so a later verse mention can resolve against any of them, not just the current one.
        run {
            val book = state.watchBook
            val chapter = state.watchChapter
            if (book != null && chapter != null) state.touchChapterHistory(book, chapter)
        }
        if (refs.isNotEmpty()) {
            val emitted = ArrayList<ScriptureEvent>()
            for (ref in refs) {
                val event = buildRefEvent(state, ref) ?: continue
                val decision = stabilizer.evaluate(refKey(event), event.confidence)
                observeVersion(state, event)
                val out = decision.toEvent(event)
                if (out != null) {
                    recordDetection(state, out)
                    emitted.add(out)
                } else if (decision is Stabilizer.EmitDecision.Suppress) {
                    logCandidate(state, event, decision.reason)
                }
            }
            if (emitted.isNotEmpty()) return logged(state, emitted)
        }

        // 2. Reverse BM25 lookup (gated by the client-selected level), validated against what was
        //    actually spoken so a spurious BM25 hit on a single rare word can't fire.
        //    Searched PER TRACK: ReverseLookup keeps only the last reverseWindowWords of its
        //    query, so searching the concatenated transcript+translation structurally discarded
        //    the transcript whenever the translation track was active (the tail was always
        //    English) — the best gated hit across the two tracks wins instead.
        val reverse = if (Config.reverseEnabled) {
            listOfNotNull(
                state.transcript.takeIf { it.isNotBlank() }?.let { ReverseLookup.search(it, index, translations) },
                state.translation.takeIf { it.isNotBlank() }?.let { ReverseLookup.search(it, index, translations) },
            ).maxByOrNull { it.confidence * 1_000_000 + it.score }
        } else null
        if (reverse != null) {
            val t = translations.find { it.id == reverse.translationId } ?: return emptyList()
            var verse = t.lookupVerse(reverse.bookNum, reverse.chapter, reverse.verse)
                ?: return emptyList()
            // Prefer the START of the contiguous covered passage: the reverse window keeps only
            // the newest words, so when a reading straddles verses N-1 and N, BM25 favors N —
            // but the passage (and the operator) started at N-1. Step back while the previous
            // verse of the same chapter is itself substantially present in either track
            // (real case: Matthew 11:28-29 read across one window; the engine offered 29,
            // the operator wanted 28).
            var backSteps = 0
            while (backSteps < 2) {
                val prev = t.lookupVerse(verse.bookNum, verse.chapter, verse.verse - 1)
                    ?.takeIf { !it.isHeader } ?: break
                val coverage = maxOf(
                    AgreementScorer.coverage(prev.text, state.transcript),
                    AgreementScorer.coverage(prev.text, state.translation),
                )
                if (coverage < Config.continuationMinCoverage) break
                verse = prev
                backSteps++
            }
            val agreement = AgreementScorer.score(verse.text, state.transcript, state.translation)
            val event = buildEvent(
                id = state.id,
                verse = verse,
                translation = t,
                confidence = reverse.confidence,
                matchType = "reverse",
                verseEnd = null,
            ).copy(bm25Score = reverse.score, bm25Ratio = reverse.ratio.takeIf { it.isFinite() && it < 1e6 })
            if (agreement >= Config.reverseMinAgreement) {
                val decision = stabilizer.evaluate(refKey(event), event.confidence)
                observeVersion(state, event)
                val out = decision.toEvent(event)
                if (out != null) {
                    recordDetection(state, out)
                    return logged(state, listOf(out))
                } else if (decision is Stabilizer.EmitDecision.Suppress) {
                    logCandidate(state, event, decision.reason)
                }
            } else {
                // BM25 hit that didn't share enough spoken words to fire — the prime near-miss to study.
                logCandidate(state, event, "low-agreement")
            }
        }

        // 3. Continuation — sequential next-3 from a confirmed verse first (cheap, precise); when
        //    that doesn't apply or doesn't find a match (no verse confirmed yet, or a jump further
        //    than 3 verses within the same chapter), fall back to scoring every verse in the known
        //    sticky chapter, so a bare "book + chapter" announcement doesn't need an explicit verse
        //    citation to be found once the reading actually starts.
        val sequential = ContinuationEngine.check(state, translations, now)
        val cont = sequential ?: ContinuationEngine.checkChapterScope(state, pickTranslation(state), now)
        if (cont != null) {
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
            val event = buildEvent(
                id = state.id,
                verse = cont.verse,
                translation = cont.translation,
                confidence = cont.confidence,
                matchType = matchType,
                verseEnd = null,
            ).copy(type = "scripture.continuation")
            val decision = stabilizer.evaluate(refKey(event), event.confidence)
            observeVersion(state, event)
            if (decision !is Stabilizer.EmitDecision.Suppress) {
                recordDetection(state, event)
                return logged(state, listOf(event))
            } else {
                logCandidate(state, event, decision.reason)
            }
        }

        return emptyList()
    }

    /** Maps a stabilizer decision to the event to emit (with the right type), or null to suppress. */
    private fun Stabilizer.EmitDecision.toEvent(event: ScriptureEvent): ScriptureEvent? = when (this) {
        is Stabilizer.EmitDecision.NewDetection -> event
        is Stabilizer.EmitDecision.UpdatedDetection -> event.copy(type = "scripture.updated")
        is Stabilizer.EmitDecision.Suppress -> null
    }

    /**
     * Records a built-but-not-emitted detection to the candidate (near-miss) log for training, stamped
     * with the same segment/track context as a real emission. Floored + toggle-gated; never throws.
     */
    private fun logCandidate(state: UtteranceState, event: ScriptureEvent, reason: String) {
        if (!Config.logCandidates || event.confidence < Config.candidateLogMinConfidence) return
        // "deduped" rows are correct detections repeating (a held passage), not genuine near-misses —
        // they swamped the candidate log and carried no tuning signal. Keep only true near-misses
        // ("below-confidence" / "low-agreement"); real misses are recovered offline against ground truth.
        if (reason == "deduped") return
        val stamped = stamp(state, event, clock())
        DetectionLogger.logCandidate(state.transcript, state.translation, stamped, reason)
    }

    private fun logged(state: UtteranceState, events: List<ScriptureEvent>): List<ScriptureEvent> {
        // Stamp the triggering STT segment + per-track corroboration onto every emitted event here —
        // the single funnel for all detection paths — so both the broadcast and the detection log
        // carry the correlation key and the transcription/translation markers.
        val now = clock()
        val stamped = events.map { stamp(state, it, now) }
        for (e in stamped) DetectionLogger.log(state.transcript, state.translation, e)
        return stamped
    }

    /** Stamps the per-utterance context (segment, tracks, speech type, sticky, version) onto an event. */
    private fun stamp(state: UtteranceState, event: ScriptureEvent, now: Long): ScriptureEvent {
        // Read the version verdict here, the one funnel both emissions and candidate rows pass
        // through. Note the explicit-reference path calls recordDetection once per event and then
        // logs them together, so a multi-event utterance stamps all of its events with the tally's
        // final state rather than with a per-event snapshot. Acceptable: the verdict moves slowly.
        val version = versionDetector.verdict()
        return event.copy(
            segmentId = state.segmentId ?: event.segmentId,
            sttStartTime = state.sttStartTime ?: event.sttStartTime,
            sessionId = state.sessionId ?: event.sessionId,
            tracks = corroboratingTracks(state, event, now),
            speechType = state.speechType ?: event.speechType,
            stickyBook = state.watchBook ?: event.stickyBook,
            stickyChapter = state.watchChapter ?: event.stickyChapter,
            detectedVersion = version?.label,
            detectedVersionId = version?.id,
            detectedVersionConfidence = version?.confidence,
        )
    }

    /** The STT track(s) that support [event] — verse text read in the track, or its citation spoken there. */
    private fun corroboratingTracks(state: UtteranceState, event: ScriptureEvent, now: Long): List<String> {
        val tracks = ArrayList<String>(2)
        if (trackSupports(state.transcript, event, now)) tracks.add("transcription")
        if (trackSupports(state.translation, event, now)) tracks.add("translation")
        return tracks
    }

    private fun trackSupports(trackText: String, event: ScriptureEvent, now: Long): Boolean {
        if (trackText.isBlank()) return false
        // Verse being read in this track (covers reverse / continuation / explicit-after-read).
        if (AgreementScorer.coverage(event.verseText, trackText) >= Config.trackCoverageMin) return true
        // Citation spoken in this track — a throwaway sticky so we don't disturb the live context
        // (covers explicit references before the verse itself is read aloud).
        val sticky = object : ReferenceWatcher.Sticky {
            override var watchBook: Int? = null
            override var watchChapter: Int? = null
            override var watchExpiresAt: Long = 0L
        }
        return ReferenceWatcher.process(trackText, sticky, now).any {
            it.bookNum == event.reference.bookId && it.chapter == event.reference.chapter
        }
    }

    private fun buildRefEvent(state: UtteranceState, ref: ReferenceWatcher.Ref): ScriptureEvent? {
        val t = pickTranslation(state)
        // Fail closed on both lookups: never fabricate a verse the speaker didn't cite. A null
        // verseStart must not become "verse 1", and a verse number that doesn't exist in this
        // translation (misheard number, versification mismatch) must not silently substitute the
        // chapter's first verse — this event can go live unattended at 0.95 confidence.
        val verseStart = ref.verseStart ?: return null
        val verse = t.lookupVerse(ref.bookNum, ref.chapter, verseStart)
            ?.takeIf { !it.isHeader }
            ?: return null
        val verseEnd = ref.verseEnd?.takeIf { it > verse.verse }
        val endCode = verseEnd?.let { t.lookupVerse(ref.bookNum, ref.chapter, it)?.code }
        val bookName = t.bookName(ref.bookNum)
        val displayRef = if (verseEnd != null) "$bookName ${ref.chapter}:${verse.verse}-$verseEnd"
        else "$bookName ${ref.chapter}:${verse.verse}"

        // Tier 2 (sticky, no book spoken) is corroborated by the spoken verse content; tier 1
        // (explicit book+chapter+verse) is trusted outright.
        val confidence = when (ref.tier) {
            1 -> 0.95
            else -> {
                val agree = AgreementScorer.score(verse.text, state.transcript, state.translation)
                (0.60 + (agree * 0.30)).coerceIn(0.60, 0.88)
            }
        }
        val matchType = if (ref.tier == 2) "continuation" else "explicit"
        val type = if (ref.tier == 2) "scripture.continuation" else "scripture.detected"

        return ScriptureEvent(
            type = type,
            id = state.id,
            reference = ScriptureReference(
                bookId = ref.bookNum,
                bookName = bookName,
                chapter = ref.chapter,
                verseStart = verse.verse,
                verseEnd = verseEnd,
                displayRef = displayRef,
                canonicalCodeStart = verse.code,
                canonicalCodeEnd = endCode,
                numbering = t.numbering,
            ),
            verseText = verse.text,
            confidence = confidence,
            matchType = matchType,
            translation = t.abbreviation,
            tier = ref.tier,
        )
    }

    private fun buildEvent(
        id: String,
        verse: EngineVerse,
        translation: EngineTranslation,
        confidence: Double,
        matchType: String,
        verseEnd: Int?,
    ): ScriptureEvent {
        val bookName = translation.bookName(verse.bookNum)
        val displayRef = "$bookName ${verse.chapter}:${verse.verse}"
        return ScriptureEvent(
            type = "scripture.detected",
            id = id,
            reference = ScriptureReference(
                bookId = verse.bookNum,
                bookName = bookName,
                chapter = verse.chapter,
                verseStart = verse.verse,
                verseEnd = verseEnd,
                displayRef = displayRef,
                canonicalCodeStart = verse.code,
                canonicalCodeEnd = null,
                numbering = translation.numbering,
            ),
            verseText = verse.text,
            confidence = confidence,
            matchType = matchType,
            translation = translation.abbreviation,
        )
    }

    /**
     * Picks the translation whose verse text is displayed for an explicit/sticky/chapter-scope
     * detection: match the citing track's dominant script against each loaded bible's
     * content-derived [Script] (ids/language fields are filename-derived and unreliable — the
     * old hardcoded id lookup showed KJV text for Russian citations whenever filenames didn't
     * happen to match "RUS_RST"/"ENG_KJV"). The transcript track is the citing track; the
     * translation track only decides when the transcript is blank.
     */
    private fun pickTranslation(state: UtteranceState): EngineTranslation {
        val citing = state.transcript.ifBlank { state.translation }
        val script = dominantScript(citing)
        return translations.firstOrNull { it.script == script } ?: translations.first()
    }

    private fun dominantScript(text: String): Script {
        var latin = 0
        var cyrillic = 0
        for (ch in text) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
                ch in 'Ѐ'..'ӿ' -> cyrillic++
            }
        }
        return when {
            cyrillic > latin -> Script.CYRILLIC
            latin > 0 -> Script.LATIN
            else -> Script.OTHER
        }
    }

    /**
     * Feeds one built detection to version scoring — emitted or suppressed alike.
     *
     * Called after the emit decision on every path, so it can never influence what goes on screen,
     * and returns immediately (the scoring itself runs on the detector's own thread). Suppressed
     * duplicates are included deliberately: a verse deduped as "already showing" is still a verse
     * being read aloud right now, which is exactly the evidence this wants, and dropping it starves
     * short or slowly-read passages. [VersionDetector] de-dupes per verse code, so nothing is
     * double-counted.
     *
     * The TRANSCRIPT track alone: the translation track is machine-translated output whose word
     * choices belong to no bible, and they would land squarely in the slots that distinguish one
     * version from another.
     */
    private fun observeVersion(state: UtteranceState, event: ScriptureEvent) {
        versionDetector.observe(
            code = event.reference.canonicalCodeStart,
            anchorText = event.verseText,
            spoken = state.transcript,
            script = dominantScript(state.transcript),
        )
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

    private fun refKey(event: ScriptureEvent): String =
        "${event.reference.bookId}:${event.reference.chapter}:${event.reference.verseStart}"
}
