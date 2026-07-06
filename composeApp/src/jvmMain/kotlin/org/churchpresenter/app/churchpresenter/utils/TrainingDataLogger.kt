package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort JSONL logger for Bible reference training data.
 * Writes session-keyed files to ~/.churchpresenter/bible-stt-logs/:
 *   live-references-<sessionId>.jsonl       — ground truth: which verse went live and why
 *   suggestion-outcomes-<sessionId>.jsonl   — operator reactions to detection chips
 *
 * When STT has supplied a stable [sessionId] (forwarded from the engine via BibleViewModel) the file
 * is keyed by it, giving an exact 1:1 join with the STT db and the engine detection-log and letting a
 * ChurchPresenter restart mid-service re-attach to the SAME file and append (no fragmentation). Until
 * the session id is known the name falls back to the process-start timestamp
 * (`live-references-YYYY-MM-DD_HH-mm-ss.jsonl`) — zero behaviour change. The target is chosen lazily
 * per write, so a detection-driven go-live always lands in the session-keyed file once STT has spoken.
 *
 * Files older than [MAX_AGE_DAYS] are deleted once per process (size control — see CrashReporter for
 * the same pattern). All writes are best-effort: errors are swallowed, never thrown into the UI path.
 */
object TrainingDataLogger {

    private const val MAX_AGE_DAYS = 30L
    private const val LIVE_REF_PREFIX = "live-references-"
    private const val OUTCOME_PREFIX = "suggestion-outcomes-"

    // Stable per-service session id from STT (db base name or UUID), set by BibleViewModel on the first
    // engine detection. Null until then; the filename falls back to [runStamp] (zero behaviour change).
    @Volatile var sessionId: String? = null

    private val lock = Any()
    private val cleanedUp = AtomicBoolean(false)
    // Files (by absolute path) that have already had their one session header written. A set rather
    // than a flag, so each session-keyed file gets exactly one header and a CP restart that re-attaches
    // to an existing file just continues it.
    private val headerWritten = java.util.Collections.synchronizedSet(HashSet<String>())

    // Frozen at first use (≈ app start), down to the second so quick restarts never share a file.
    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    private val logDir by lazy {
        File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
    }

    /** Session-keyed suffix when STT has provided an id, else the process-start timestamp. */
    private fun suffix(): String = sessionId?.let { sanitize(it) } ?: runStamp
    private fun liveRefPath() = File(logDir, "$LIVE_REF_PREFIX${suffix()}.jsonl").absolutePath
    private fun outcomePath() = File(logDir, "$OUTCOME_PREFIX${suffix()}.jsonl").absolutePath

    /** Keeps `[A-Za-z0-9._-]`, replacing anything else with `_`, so any session id is filename-safe. */
    private fun sanitize(raw: String): String =
        raw.map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-') it else '_' }
            .joinToString("")

    /**
     * Writes a one-time per-file session header (the session id this file's rows belong to) as the
     * first line, so the file ties back to the STT db + engine detection-log even in isolation. Keyed
     * per file: a CP restart re-attaching to an existing file does not add a second header.
     */
    private fun ensureSessionHeader(path: String) {
        if (!headerWritten.add(path)) return
        val file = File(path)
        if (file.exists() && file.length() > 0L) return
        runCatching {
            val line = buildString {
                append("{\"type\":\"session\"")
                append(",\"ts_ms\":").append(System.currentTimeMillis())
                if (sessionId != null) append(",\"sessionId\":\"").append(esc(sessionId!!)).append("\"")
                else append(",\"sessionId\":null")
                append("}")
            }
            synchronized(lock) { file.appendText(line + "\n", Charsets.UTF_8) }
        }
    }

    /**
     * Deletes dated training-data logs older than [MAX_AGE_DAYS]. Runs at most once per process
     * (first write triggers it). Mirrors CrashReporter's age-based cleanup. Best-effort.
     */
    private fun cleanupOldLogsOnce() {
        if (!cleanedUp.compareAndSet(false, true)) return
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * 24 * 60 * 60 * 1000
            logDir.listFiles()?.forEach { file ->
                val n = file.name
                val dated = file.isFile && n.endsWith(".jsonl") &&
                    (n.startsWith(LIVE_REF_PREFIX) || n.startsWith(OUTCOME_PREFIX))
                if (dated && file.lastModified() < cutoff) file.delete()
            }
        }
    }

    /**
     * Call when a Bible verse actually goes live on the output screen.
     * [book] is the canonical 1-based book number (1=Genesis … 66=Revelation).
     * [source] is "manual" (operator button/double-click/Enter), "auto" (auto-follow drove the
     * go-live from an engine detection), or "remote" (companion API).
     * [autoFollow] is whether auto-follow was ENABLED at the time — distinct from [source], so a
     * manual override while auto-follow is on is still distinguishable for analysis.
     * [matchType] is the detection's engine match type ("explicit"/"continuation"/"chapter-scan"/
     * "chapter-history"/"reverse") when this go-live traces back to a specific detection, else null
     * (e.g. free browsing, typed reference). Lets offline analysis measure acceptance-by-tier for
     * auto-follow's instant-vs-staged split (see AGENT.md "Bible Follow Along — Tiered Auto-Follow").
     */
    fun logLiveReference(
        book: Int,
        chapter: Int,
        verseStart: Int?,
        verseEnd: Int?,
        source: String,
        segmentId: String? = null,
        autoFollow: Boolean = false,
        matchType: String? = null,
    ) {
        cleanupOldLogsOnce()
        val p = liveRefPath()
        ensureSessionHeader(p)
        runCatching {
            val line = buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                append(",\"book\":").append(book)
                append(",\"chapter\":").append(chapter)
                if (verseStart != null) append(",\"verseStart\":").append(verseStart)
                else append(",\"verseStart\":null")
                if (verseEnd != null) append(",\"verseEnd\":").append(verseEnd)
                else append(",\"verseEnd\":null")
                // Stable per-service session id — exact join key to the STT db + engine detection-log.
                if (sessionId != null) append(",\"sessionId\":\"").append(esc(sessionId!!)).append("\"")
                else append(",\"sessionId\":null")
                // Clock-free correlation key back to the STT transcript + engine detection (no NTP).
                if (segmentId != null) append(",\"segmentId\":\"").append(esc(segmentId)).append("\"")
                else append(",\"segmentId\":null")
                append(",\"autoFollow\":").append(autoFollow)
                if (matchType != null) append(",\"matchType\":\"").append(esc(matchType)).append("\"")
                else append(",\"matchType\":null")
                append(",\"source\":\"").append(source).append("\"}")
            }
            synchronized(lock) { File(p).appendText(line + "\n", Charsets.UTF_8) }
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

    /**
     * Call when the operator acts on a suggestion chip shown by the detection engine.
     * [suggestedBook] is the canonical 1-based book number.
     * [action] is "accepted" (chip clicked), "dismissed" (clear button), or "corrected" (a different
     * verse went live, overriding this suggestion — [correctedRef] holds what was actually shown).
     * [matchType] is the suggested detection's engine match type (see [logLiveReference]) — lets
     * offline analysis measure acceptance/dismissal/correction rate by tier.
     */
    fun logSuggestionOutcome(
        suggestedBook: Int,
        suggestedChapter: Int,
        suggestedVerse: Int?,
        action: String,
        correctedRef: String? = null,
        matchType: String? = null,
    ) {
        cleanupOldLogsOnce()
        val p = outcomePath()
        ensureSessionHeader(p)
        runCatching {
            val line = buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                if (sessionId != null) append(",\"sessionId\":\"").append(esc(sessionId!!)).append("\"")
                else append(",\"sessionId\":null")
                append(",\"suggestedBook\":").append(suggestedBook)
                append(",\"suggestedChapter\":").append(suggestedChapter)
                if (suggestedVerse != null) append(",\"suggestedVerse\":").append(suggestedVerse)
                else append(",\"suggestedVerse\":null")
                append(",\"action\":\"").append(action).append("\"")
                if (correctedRef != null) append(",\"correctedRef\":\"").append(correctedRef).append("\"")
                if (matchType != null) append(",\"matchType\":\"").append(esc(matchType)).append("\"")
                else append(",\"matchType\":null")
                append("}")
            }
            synchronized(lock) { File(p).appendText(line + "\n", Charsets.UTF_8) }
        }
    }
}
