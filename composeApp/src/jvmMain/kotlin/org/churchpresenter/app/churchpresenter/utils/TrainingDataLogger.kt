package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort JSONL logger for Bible reference training data.
 * Writes per-run timestamped files to ~/.churchpresenter/bible-stt-logs/:
 *   live-references-YYYY-MM-DD_HH-mm-ss.jsonl   — ground truth: which verse went live and why
 *   suggestion-outcomes-YYYY-MM-DD_HH-mm-ss.jsonl — operator reactions to detection chips
 *
 * The timestamp is frozen at process start, so every write in one app run lands in the same file but
 * separate app starts never combine. Files older than [MAX_AGE_DAYS] are deleted once per process
 * (size control — see CrashReporter for the same pattern). All writes are best-effort: errors are
 * swallowed, never thrown into the UI path.
 */
object TrainingDataLogger {

    private const val MAX_AGE_DAYS = 30L
    private const val LIVE_REF_PREFIX = "live-references-"
    private const val OUTCOME_PREFIX = "suggestion-outcomes-"

    private val lock = Any()
    private val cleanedUp = AtomicBoolean(false)

    // Frozen at first use (≈ app start), down to the second so quick restarts never share a file.
    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    private val logDir by lazy {
        File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
    }
    private fun liveRefPath() = File(logDir, "$LIVE_REF_PREFIX$runStamp.jsonl").absolutePath
    private fun outcomePath() = File(logDir, "$OUTCOME_PREFIX$runStamp.jsonl").absolutePath

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
     */
    fun logLiveReference(
        book: Int,
        chapter: Int,
        verseStart: Int?,
        verseEnd: Int?,
        source: String,
        segmentId: String? = null,
    ) {
        cleanupOldLogsOnce()
        val p = liveRefPath()
        runCatching {
            val line = buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                append(",\"book\":").append(book)
                append(",\"chapter\":").append(chapter)
                if (verseStart != null) append(",\"verseStart\":").append(verseStart)
                else append(",\"verseStart\":null")
                if (verseEnd != null) append(",\"verseEnd\":").append(verseEnd)
                else append(",\"verseEnd\":null")
                // Clock-free correlation key back to the STT transcript + engine detection (no NTP).
                if (segmentId != null) append(",\"segmentId\":\"").append(esc(segmentId)).append("\"")
                else append(",\"segmentId\":null")
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
     * [action] is "accepted" (chip clicked) or "dismissed" (clear button).
     */
    fun logSuggestionOutcome(
        suggestedBook: Int,
        suggestedChapter: Int,
        suggestedVerse: Int?,
        action: String,
        correctedRef: String? = null,
    ) {
        cleanupOldLogsOnce()
        val p = outcomePath()
        runCatching {
            val line = buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                append(",\"suggestedBook\":").append(suggestedBook)
                append(",\"suggestedChapter\":").append(suggestedChapter)
                if (suggestedVerse != null) append(",\"suggestedVerse\":").append(suggestedVerse)
                else append(",\"suggestedVerse\":null")
                append(",\"action\":\"").append(action).append("\"")
                if (correctedRef != null) append(",\"correctedRef\":\"").append(correctedRef).append("\"")
                append("}")
            }
            synchronized(lock) { File(p).appendText(line + "\n", Charsets.UTF_8) }
        }
    }
}
