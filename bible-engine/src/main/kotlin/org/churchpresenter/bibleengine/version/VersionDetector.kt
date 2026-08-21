package org.churchpresenter.bibleengine.version

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.Script
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.min

/**
 * Accumulates per-verse evidence from [VersionScorer] into a running answer to "which translation is
 * being read aloud".
 *
 * A single verse is rarely enough — plenty of verses are word-identical across translations — so
 * evidence is summed across the passage with an exponential decay, and no answer is published until
 * a leader clears both an absolute evidence floor and a margin over the runner-up.
 *
 * **Off the detection thread.** [observe] only hands its arguments to [scoringExecutor] and returns;
 * all corpus reads and scoring happen there. Version detection is a nice-to-have and reading a verse
 * costs a seek into every indexed bible, so getting a verse on screen must never wait on it. The
 * queue is bounded and drops when full for the same reason — a backlog could only ever produce a
 * stale answer late.
 *
 * **Sticky.** Once an answer is published it stands until a *different* version clears the higher
 * [Config.versionSwitchMinMargin] bar, or the service falls silent for [Config.versionResetGapMs].
 * A preacher reads one bible for the whole service and moves between passages freely, so a passage
 * change is no reason to forget. The per-verse decay still does the work of letting a genuinely
 * different reader take over — stickiness governs what is *displayed* between decisions, not what
 * the evidence says.
 *
 * Threading: every field except [current] is touched only on the scoring thread. [verdict] reads the
 * one volatile, so the detection thread can call it freely. Nothing here locks.
 */
class VersionDetector(
    private val corpus: () -> VersionCorpus,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Notified whenever the published answer changes, including to null. Called on the scoring thread. */
    private val onVerdictChanged: (Verdict?) -> Unit = {},
    /** Injectable so tests can run scoring inline and stay deterministic. */
    private val scoringExecutor: Executor = defaultExecutor(),
) {

    data class Verdict(val id: String, val label: String, val confidence: Double)

    private val scores = HashMap<String, Double>()
    private val labels = HashMap<String, String>()
    private var verses = 0
    private var lastCode: String? = null
    private var lastObservedAt = 0L

    /** The published answer. The only field that crosses threads. */
    @Volatile private var current: Verdict? = null

    /**
     * Feeds one detected verse in — returns immediately, having done no scoring. [anchorText] is the
     * detecting translation's rendering (the language anchor), [spoken] is the transcript window.
     *
     * [spoken] must be the TRANSCRIPT track only. Every other scorer in this module sums both tracks,
     * but the translation track is machine-translated output — a rendering that belongs to no bible,
     * whose word choices land squarely in the discriminative slots this scoring depends on.
     */
    fun observe(code: String?, bookId: Int, chapter: Int, anchorText: String, spoken: String, script: Script) {
        if (!Config.versionDetectionEnabled) return
        if (code == null || spoken.isBlank()) return
        val now = clock()
        try {
            scoringExecutor.execute { score(code, anchorText, spoken, script, now) }
        } catch (_: RejectedExecutionException) {
            // Queue full — drop it. Never run inline: that is exactly the stall this class avoids.
        }
    }

    /** The current answer, or null before one has been established. Safe from any thread. */
    fun verdict(): Verdict? = if (Config.versionDetectionEnabled) current else null

    // ── Scoring thread only ─────────────────────────────────────────────────────

    private fun score(code: String, anchorText: String, spoken: String, script: Script, now: Long) {
        // One sample per verse: the Stabilizer re-emits a held passage as scripture.updated, the
        // explicit-reference path can emit several events for one utterance, and suppressed
        // duplicates are fed in too. Counting those again would let a single verse dominate the
        // tally and defeat the minimum-verses guard.
        if (code == lastCode) return
        if (lastObservedAt != 0L && now - lastObservedAt > Config.versionResetGapMs) reset()

        val deltas = runCatching { VersionScorer.deltas(corpus().rendering(code), anchorText, spoken, script) }
            .getOrElse { emptyList() }
        lastCode = code
        lastObservedAt = now
        if (deltas.isEmpty()) return

        scores.replaceAll { _, v -> v * Config.versionDecay }
        for (d in deltas) {
            scores.merge(d.id, d.value, Double::plus)
            labels[d.id] = d.label
        }
        verses++
        publish()
    }

    /**
     * Recomputes the answer and, when it has changed, publishes it.
     *
     * Nothing is ever un-published by weak evidence: a verse that fails to separate the candidates
     * means "no new information", not "forget what you knew". Only a competitor that clears the
     * switch bar replaces a standing answer.
     */
    private fun publish() {
        val candidate = evaluate() ?: return
        val held = current
        if (held != null) {
            if (candidate.id == held.id) {
                // Same answer, firmer (or softer) — refresh the confidence without announcing a change.
                if (candidate.confidence != held.confidence) current = candidate
                return
            }
            // A different version must out-argue the incumbent by more than it took to seat it.
            if (marginOf(candidate.id) < Config.versionSwitchMinMargin) return
        }
        current = candidate
        onVerdictChanged(candidate)
    }

    /** The answer the accumulated evidence supports right now, ignoring what is already published. */
    private fun evaluate(): Verdict? {
        if (verses < Config.versionMinVerses) return null
        val ranked = scores.entries.sortedByDescending { it.value }
        val leader = ranked.firstOrNull() ?: return null
        if (leader.value < Config.versionMinEvidence) return null
        val margin = leader.value - (ranked.getOrNull(1)?.value ?: 0.0)
        if (margin < Config.versionMinMargin) return null
        // Saturating: ~0.49 at the 2.0 margin floor, ~0.8 at 5, ~0.96 at 10 distinctive words clear.
        var confidence = 1.0 - exp(-margin / 3.0)
        // Only ever one candidate: the margin is the tally itself, against nothing. The curve above
        // would read that as strong agreement when it is really "nothing contradicted it" — there
        // was nothing that could have. Capped so a single-translation library cannot present as more
        // certain than a corpus that actually ruled competitors out.
        if (ranked.size == 1) confidence = min(confidence, Config.versionSoleCandidateMaxConfidence)
        return Verdict(leader.key, labels[leader.key] ?: leader.key, (confidence * 1000).toInt() / 1000.0)
    }

    /** How far [id] leads the best score that is not its own. */
    private fun marginOf(id: String): Double {
        val mine = scores[id] ?: return 0.0
        val best = scores.entries.filter { it.key != id }.maxOfOrNull { it.value } ?: 0.0
        return mine - best
    }

    /**
     * Forgets everything, including the published answer — the service has moved on.
     *
     * Private: the only thing that ends stickiness is a long silence, which [score] detects itself.
     * Exposing it would invite callers to clear the answer for reasons that are not that.
     */
    private fun reset() {
        scores.clear()
        labels.clear()
        verses = 0
        lastCode = null
        lastObservedAt = 0L
        if (current != null) {
            current = null
            onVerdictChanged(null)
        }
    }

    /** Stops the scoring thread, when this detector owns one. */
    fun shutdown() {
        (scoringExecutor as? ThreadPoolExecutor)?.shutdownNow()
    }

    private companion object {
        /** Small enough that a backlog is dropped rather than allowed to report stale answers late. */
        const val QUEUE_CAPACITY = 32

        fun defaultExecutor(): Executor = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
            { r -> Thread(r, "ble-version").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )
    }
}
