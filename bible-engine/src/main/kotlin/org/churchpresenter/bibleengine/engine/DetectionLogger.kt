package org.churchpresenter.bibleengine.engine

import org.churchpresenter.bibleengine.Config
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Appends every emitted detection — with the triggering transcript/translation — to a session-keyed
 * JSONL file, turning each live service into labeled-ish regression data without manual annotation.
 * Disabled unless [path] is set (EngineServer/BibleEngineClient point it at the log dir).
 *
 * The configured [path]'s parent + base name `detection-log` is rolled into
 * `detection-log-<sessionId>.jsonl` when STT has supplied a stable [sessionId], otherwise into
 * `detection-log-YYYY-MM-DD_HH-mm-ss.jsonl` (the process-start timestamp). The file is chosen
 * **lazily per write**, so a ChurchPresenter restart mid-service re-attaches to the SAME
 * session-keyed file and appends (no fragmentation), and a fresh `sessionId` (STT restart mid-CP)
 * rolls subsequent writes to a new file. Each distinct file gets exactly one session header.
 *
 * Files older than [MAX_AGE_DAYS] are pruned once per process for size control. Best-effort; never
 * throws into the detection path.
 *
 * [logCandidate] writes built-but-not-emitted near-misses to a parallel `candidate-log-*.jsonl`
 * (same naming/rotation/cleanup) for confidence tuning — gated by `Config.logCandidates`.
 *
 * One JSON object per line: {ts, ref, book, chapter, verse, source, confidence, emitted, reason?,
 * sessionId, segmentId, sttStartTime, tracks, transcript, translation}.
 */
object DetectionLogger {
    @Volatile var path: String? = null

    // Stable per-service session id from STT (db base name or UUID). Null until the STT stream ships
    // it; the filename then falls back to [runStamp] (zero behaviour change). Set lazily by the
    // detection engine, so the filename is bound only at first write — by which time STT has connected.
    @Volatile var sessionId: String? = null

    private const val MAX_AGE_DAYS = 30L
    private const val BASE_PREFIX = "detection-log-"
    private const val CANDIDATE_PREFIX = "candidate-log-"
    private const val STICKY_PREFIX = "sticky-log-"

    private val cleanedUp = AtomicBoolean(false)

    // Single background writer: log calls build their line on the caller thread and enqueue;
    // this daemon drains FIFO, so the synchronous disk append is off the detection path while
    // per-file ordering (header first, then rows) is preserved. [pending] lets tests and
    // shutdown wait for the queue to hit disk.
    private val writeQueue = java.util.concurrent.LinkedBlockingQueue<Pair<File, String>>()
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)
    private val writerThread = Thread({
        while (true) {
            val (file, text) = writeQueue.take()
            runCatching { file.appendText(text, Charsets.UTF_8) }
            pending.decrementAndGet()
        }
    }, "ble-log-writer").apply { isDaemon = true; start() }

    private fun enqueue(target: File, line: String) {
        pending.incrementAndGet()
        writeQueue.put(target to line + "\n")
    }

    /** Blocks until every queued line has been written — for tests and orderly shutdown. */
    fun drainForTests() {
        val deadline = System.currentTimeMillis() + 5_000
        while (pending.get() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
    }
    // Files (by absolute path) that have already had their one session header written. A set rather
    // than a single flag, so each session-keyed file gets exactly one header — appending to an
    // existing file (CP restart) just continues it, and a new session id starts a fresh header.
    private val headerWritten = java.util.Collections.synchronizedSet(HashSet<String>())

    // Frozen at first use (≈ process start), down to the second so quick restarts never share a file.
    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    /**
     * Session-keyed sibling of the configured [path]: `<parent>/<prefix><sessionId>.jsonl` when STT
     * has supplied a session id, else `<parent>/<prefix><runStamp>.jsonl`. Chosen per call so a
     * late-arriving session id (or a mid-service STT restart) lands in the right file.
     */
    private fun datedFile(configured: String, prefix: String): File {
        val parent = File(configured).absoluteFile.parentFile ?: File(".")
        val suffix = sessionId?.let { sanitize(it) } ?: runStamp
        return File(parent, "$prefix$suffix.jsonl")
    }

    /** Keeps `[A-Za-z0-9._-]`, replacing anything else with `_`, so any session id is filename-safe. */
    private fun sanitize(raw: String): String =
        raw.map {
            if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' ||
                it == '.' || it == '_' || it == '-'
            ) it else '_'
        }
            .joinToString("")

    /** Deletes dated detection + candidate + sticky logs older than [MAX_AGE_DAYS] in [dir]. Once per process. */
    private fun cleanupOldLogsOnce(dir: File?) {
        if (dir == null || !cleanedUp.compareAndSet(false, true)) return
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * 24 * 60 * 60 * 1000
            dir.listFiles()?.forEach { file ->
                val n = file.name
                if (file.isFile && n.endsWith(".jsonl") &&
                    (n.startsWith(BASE_PREFIX) || n.startsWith(CANDIDATE_PREFIX) || n.startsWith(STICKY_PREFIX)) &&
                    file.lastModified() < cutoff
                ) file.delete()
            }
        }
    }

    /** An emitted detection → `detection-log-*.jsonl`. */
    fun log(transcript: String, translation: String, event: ScriptureEvent) {
        val configured = path ?: return
        val target = datedFile(configured, BASE_PREFIX)
        cleanupOldLogsOnce(target.parentFile)
        ensureSessionHeader(target)
        enqueue(target, lineFor(transcript, translation, event, reason = null))
    }

    /**
     * Writes a one-time per-file session header — the session id, Bibles, level and thresholds that
     * produced this service's rows — as the first line of each detection-log file, so results tie back
     * to the exact config. Keyed per file so a CP restart appending to an existing session file does
     * NOT add a second header, while a new session id (new file) gets its own.
     */
    private fun ensureSessionHeader(target: File) {
        if (!headerWritten.add(target.absolutePath)) return
        // A CP restart re-attaches to an existing session-keyed file that already carries its header —
        // don't write a second one, just append the new run's rows after the existing content.
        if (target.exists() && target.length() > 0L) return
        runCatching {
            val line = buildString {
                append('{')
                append("\"type\":\"session\",")
                append("\"ts\":\"").append(Instant.now()).append("\",")
                if (sessionId != null) append("\"sessionId\":\"").append(esc(sessionId!!)).append("\",")
                else append("\"sessionId\":null,")
                append("\"bibles\":[")
                    .append(Config.loadedBibles.joinToString(",") { "\"" + esc(it) + "\"" })
                    .append("],")
                // Which versions could possibly have been named by version detection this session —
                // wider than "bibles" above, and the only way to interpret a detectedVersion later.
                append("\"versionCorpus\":[")
                    .append(Config.versionCorpusLabels.joinToString(",") { "\"" + esc(it) + "\"" })
                    .append("],")
                append("\"level\":\"").append(esc(Config.level)).append("\",")
                append("\"continuationSpeed\":\"").append(esc(Config.continuationSpeed)).append("\",")
                append("\"continuationMinCoverage\":").append(Config.continuationMinCoverage).append(',')
                append("\"minConfidenceEmit\":").append(Config.minConfidenceEmit).append(',')
                append("\"reverseMinScoreRatio\":").append(Config.reverseMinScoreRatio).append(',')
                append("\"reverseMinAgreement\":").append(Config.reverseMinAgreement).append(',')
                append("\"trackCoverageMin\":").append(Config.trackCoverageMin).append(',')
                append("\"stickyTtlMs\":").append(Config.stickyTtlMs).append(',')
                append("\"dedupTtlMs\":").append(Config.dedupTtlMs).append(',')
                append("\"logCandidates\":").append(Config.logCandidates).append(',')
                append("\"candidateLogMinConfidence\":").append(Config.candidateLogMinConfidence).append(',')
                append("\"sttServer\":\"").append(esc(Config.sttServerUrl)).append('"')
                append('}')
            }
            enqueue(target, line)
        }
    }

    /**
     * A built-but-not-emitted near-miss → `candidate-log-*.jsonl`. [reason] is why it was dropped
     * ("below-confidence" / "low-agreement"). "deduped" repeats are filtered out upstream in
     * DetectionEngine — they're a held passage repeating, not a tuning signal. Training data for tuning.
     */
    fun logCandidate(transcript: String, translation: String, event: ScriptureEvent, reason: String) {
        val configured = path ?: return
        val target = datedFile(configured, CANDIDATE_PREFIX)
        cleanupOldLogsOnce(target.parentFile)
        enqueue(target, lineFor(transcript, translation, event, reason))
    }

    /**
     * Every time the sticky book/chapter changes — even when nothing was emitted — for tracing the
     * origin of an unexpected jump (e.g. a stale/wrong sticky with no corresponding logged detection).
     * Independent file (`sticky-log-*.jsonl`) from detection-log/candidate-log so it can't affect
     * existing training tooling that reads those. Gated by [Config.logStickyChanges].
     */
    fun logStickyChange(
        transcript: String, translation: String,
        prevBook: Int?, prevChapter: Int?, newBook: Int?, newChapter: Int?,
    ) {
        if (!Config.logStickyChanges) return
        val configured = path ?: return
        val target = datedFile(configured, STICKY_PREFIX)
        cleanupOldLogsOnce(target.parentFile)
        runCatching {
            val line = buildString {
                append('{')
                append("\"ts\":\"").append(Instant.now()).append("\",")
                append("\"prevBook\":").append(prevBook ?: "null").append(',')
                append("\"prevChapter\":").append(prevChapter ?: "null").append(',')
                append("\"newBook\":").append(newBook ?: "null").append(',')
                append("\"newChapter\":").append(newChapter ?: "null").append(',')
                if (sessionId != null) append("\"sessionId\":\"").append(esc(sessionId!!)).append("\",")
                else append("\"sessionId\":null,")
                append("\"transcript\":\"").append(esc(transcript)).append("\",")
                append("\"translation\":\"").append(esc(translation)).append('"')
                append('}')
            }
            enqueue(target, line)
        }
    }

    /** Builds one JSONL row. [reason] null = emitted detection; non-null = near-miss candidate. */
    private fun lineFor(transcript: String, translation: String, event: ScriptureEvent, reason: String?): String {
        val r = event.reference
        return buildString {
            append('{')
            append("\"ts\":\"").append(Instant.now()).append("\",")
            append("\"ref\":\"").append(esc(r.displayRef)).append("\",")
            append("\"book\":").append(r.bookId).append(',')
            append("\"chapter\":").append(r.chapter).append(',')
            append("\"verseStart\":").append(r.verseStart).append(',')
            append("\"verseEnd\":").append(r.verseEnd ?: 0).append(',')
            append("\"source\":\"").append(esc(event.matchType)).append("\",")
            append("\"confidence\":").append(event.confidence).append(',')
            // Active aggressiveness level when this row was produced (off/conservative/balanced/aggressive).
            append("\"level\":\"").append(esc(Config.level)).append("\",")
            // Active "Verse speed" preset when this row was produced (balanced/fast) — independent of level.
            append("\"continuationSpeed\":\"").append(esc(Config.continuationSpeed)).append("\",")
            append("\"emitted\":").append(reason == null).append(',')
            if (reason != null) append("\"reason\":\"").append(esc(reason)).append("\",")
            // ── Diagnostics for tuning: which Bible matched, a stable group key, the decision
            //    sub-scores, and the speech/sticky context at the moment it fired. ──
            append("\"matchedBible\":\"").append(esc(event.translation)).append("\",")
            // Which version the speaker appeared to be READING — distinct from matchedBible above,
            // which is only where the displayed text was taken from. Omitted entirely when the
            // evidence didn't separate the candidates.
            event.detectedVersion?.let { append("\"detectedVersion\":\"").append(esc(it)).append("\",") }
            event.detectedVersionId?.let { append("\"detectedVersionId\":\"").append(esc(it)).append("\",") }
            event.detectedVersionConfidence?.let { append("\"detectedVersionConfidence\":").append(it).append(',') }
            append("\"refKey\":\"").append(r.bookId).append(':').append(r.chapter)
                .append(':').append(r.verseStart).append("\",")
            // book/chapter/verseStart above are the MATCHED MODULE's own numbering, which differs
            // between modules of the same translation: this machine carries two RST files, one
            // numbering Psalms per the Synodal (Ps 23 = "Господня земля") and one per the Hebrew
            // (Ps 23 = "Господь — Пастырь мой"). Anything joining this log to the app's ground truth
            // or to a replay must therefore compare the module-independent canonical code, not the
            // numbers above — so log it explicitly rather than making every consumer infer it.
            append("\"canonicalStart\":\"").append(esc(r.canonicalCodeStart ?: "")).append("\",")
            append("\"canonicalEnd\":\"").append(esc(r.canonicalCodeEnd ?: "")).append("\",")
            event.tier?.let { append("\"tier\":").append(it).append(',') }
            event.bm25Score?.let { append("\"bm25Score\":").append(it).append(',') }
            event.bm25Ratio?.let { append("\"bm25Ratio\":").append(it).append(',') }
            event.speechType?.let { append("\"speechType\":\"").append(esc(it)).append("\",") }
            event.stickyBook?.let { append("\"stickyBook\":").append(it).append(',') }
            event.stickyChapter?.let { append("\"stickyChapter\":").append(it).append(',') }
            append("\"agreement\":").append(AgreementScorer.score(event.verseText, transcript, translation)).append(',')
            append("\"coverageTranscript\":").append(AgreementScorer.coverage(event.verseText, transcript)).append(',')
            append("\"coverageTranslation\":")
                .append(AgreementScorer.coverage(event.verseText, translation)).append(',')
            // Stable per-service session id — the exact join key tying this row to the STT db and the
            // CP live-references log. Null when STT didn't provide it (pre-session_id data).
            if (event.sessionId != null) append("\"sessionId\":\"").append(esc(event.sessionId)).append("\",")
            else append("\"sessionId\":null,")
            // Clock-free correlation key: ties this row to the STT transcript and the operator's
            // go-live log without any wall-clock/NTP. Null when STT didn't provide it.
            if (event.segmentId != null) append("\"segmentId\":\"").append(esc(event.segmentId)).append("\",")
            else append("\"segmentId\":null,")
            if (event.sttStartTime != null) append("\"sttStartTime\":").append(event.sttStartTime).append(',')
            else append("\"sttStartTime\":null,")
            // Which STT track(s) corroborated this detection: transcription / translation / both.
            append("\"tracks\":[")
            append(event.tracks.joinToString(",") { "\"" + esc(it) + "\"" })
            append("],")
            append("\"transcript\":\"").append(esc(transcript)).append("\",")
            append("\"translation\":\"").append(esc(translation)).append('"')
            append('}')
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
