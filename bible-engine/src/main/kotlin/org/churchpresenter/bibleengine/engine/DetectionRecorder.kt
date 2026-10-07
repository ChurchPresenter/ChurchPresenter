package org.churchpresenter.bibleengine.engine

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.detection.ReferenceWatcher
import org.churchpresenter.bibleengine.version.VersionDetector

/**
 * What happens to a built detection besides deciding whether it goes out: the per-utterance context
 * stamped onto it, the detection and candidate logs it is written to, and the version scoring it
 * feeds.
 */
internal class DetectionRecorder(
    private val clock: () -> Long,
    private val versionDetector: VersionDetector,
) {

    /**
     * Records a built-but-not-emitted detection to the candidate (near-miss) log for training, stamped
     * with the same segment/track context as a real emission. Floored + toggle-gated; never throws.
     */
    fun logCandidate(state: UtteranceState, event: ScriptureEvent, reason: String) {
        if (!Config.logCandidates || event.confidence < Config.candidateLogMinConfidence) return
        // "deduped" rows are correct detections repeating (a held passage), not genuine near-misses —
        // they swamped the candidate log and carried no tuning signal. Keep only true near-misses
        // ("below-confidence" / "low-agreement"); real misses are recovered offline against ground truth.
        if (reason == "deduped") return
        val stamped = stamp(state, event, clock())
        DetectionLogger.logCandidate(state.transcript, state.translation, stamped, reason)
    }

    fun logged(state: UtteranceState, events: List<ScriptureEvent>): List<ScriptureEvent> {
        // Stamp the triggering STT segment + per-track corroboration onto every emitted event here —
        // the single funnel for all detection paths — so both the broadcast and the detection log
        // carry the correlation key and the transcription/translation markers.
        val now = clock()
        val stamped = events.map { stamp(state, it, now) }
        for (e in stamped) DetectionLogger.log(state.transcript, state.translation, e)
        return stamped
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
    fun observeVersion(state: UtteranceState, event: ScriptureEvent) {
        versionDetector.observe(
            code = event.reference.canonicalCodeStart,
            anchorText = event.verseText,
            spoken = state.transcript,
            script = dominantScript(state.transcript),
        )
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
}
