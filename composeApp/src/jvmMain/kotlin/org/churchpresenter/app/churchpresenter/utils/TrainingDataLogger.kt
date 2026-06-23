package org.churchpresenter.app.churchpresenter.utils

import java.io.File

/**
 * Best-effort JSONL logger for Bible reference training data.
 * Writes two files to ~/.churchpresenter/bible-stt-logs/:
 *   live-references.jsonl   — ground truth: which verse actually went live and why
 *   suggestion-outcomes.jsonl — operator reactions to detection chips
 *
 * All writes are best-effort: errors are swallowed, never thrown into the UI path.
 */
object TrainingDataLogger {

    private val lock = Any()

    private val logDir by lazy {
        File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
    }
    private fun liveRefPath() = File(logDir, "live-references.jsonl").absolutePath
    private fun outcomePath() = File(logDir, "suggestion-outcomes.jsonl").absolutePath

    /**
     * Call when a Bible verse actually goes live on the output screen.
     * [book] is the canonical 1-based book number (1=Genesis … 66=Revelation).
     * [source] is "manual" (operator button/double-click) or "remote" (companion API).
     */
    fun logLiveReference(
        book: Int,
        chapter: Int,
        verseStart: Int?,
        verseEnd: Int?,
        source: String,
    ) {
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
                append(",\"source\":\"").append(source).append("\"}")
            }
            synchronized(lock) { File(p).appendText(line + "\n", Charsets.UTF_8) }
        }
    }

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
