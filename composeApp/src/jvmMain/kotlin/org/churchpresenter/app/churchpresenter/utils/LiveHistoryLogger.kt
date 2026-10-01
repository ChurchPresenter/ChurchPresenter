package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What one line of the on-screen history says was showing. Identifiers only — never lyric, verse or
 * slide text. [contentType] is a `Presenting` name, `NONE` when the screen is cleared or blank;
 * every other field is null when it does not apply and is then left out of the line.
 */
internal data class LiveHistoryEntry(
    val contentType: String,
    val songId: String? = null,
    val songbook: String? = null,
    val songNumber: Int? = null,
    val songTitle: String? = null,
    val sectionIndex: Int? = null,
    val sectionType: String? = null,
    val lineIndex: Int? = null,
    val verseCode: String? = null,
    val reference: String? = null,
    val fileName: String? = null,
    val slideIndex: Int? = null,
)

/**
 * Best-effort JSONL log of what was on screen and when, one line per on-screen change, written to
 * `~/.churchpresenter/bible-stt-logs/live-content-<suffix>.jsonl` beside [TrainingDataLogger]'s
 * files and under the same rules: the suffix is the STT session id ([TrainingDataLogger.sessionId],
 * shared so the two never disagree) or else the process-start timestamp, each file opens with one
 * session header line, and [TrainingDataLogger.cleanupOldLogsOnce] sweeps it after 30 days.
 *
 * Content setters fire on every call even when nothing changed, so a line is only written when it
 * differs from the previous line in the same file.
 *
 * `PresenterManager` knows a live section's title and number but not its songbook. The go-live
 * paths that do know the song row report it through [noteLiveSong]; a LYRICS line is stamped with
 * that `songId` while its title and number still match.
 */
object LiveHistoryLogger {

    internal const val PREFIX = "live-content-"

    private data class NotedSong(
        val songId: String,
        val songbook: String,
        val songNumber: Int,
        val title: String,
        val source: String,
        val sourcePending: Boolean,
    ) {
        fun matches(entry: LiveHistoryEntry): Boolean =
            entry.contentType == Presenting.LYRICS.name && entry.songTitle == title && entry.songNumber == songNumber
    }

    // Everything below is read and written under [lock].
    private val lock = Any()

    // Per file, so a new session's file starts with a line of its own.
    private val lastWritten = HashMap<String, LiveHistoryEntry>()
    private var lastReported: LiveHistoryEntry? = null
    private var notedSong: NotedSong? = null

    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    /**
     * Keyed by the STT session id when known, else by the process-start timestamp. The folder is
     * resolved from `user.home` on every write rather than once per JVM, as `CrashReporter` does.
     */
    private fun path(): String {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs").also { it.mkdirs() }
        val suffix = TrainingDataLogger.sessionId?.let { sanitize(it) } ?: runStamp
        return File(dir, "$PREFIX$suffix.jsonl").absolutePath
    }

    private fun sanitize(raw: String): String =
        raw.map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }
            .joinToString("")

    /** Call on every on-screen change; writes only when the logged fields differ from the last line. */
    internal fun logLiveState(entry: LiveHistoryEntry) {
        runCatching {
            synchronized(lock) {
                lastReported = entry
                write(entry)
            }
        }
    }

    /**
     * Call when a go-live path puts a song row on screen. [source] is `manual`, `schedule` or
     * `remote`, and is written on the first line of that song. If the song's section is already the
     * last line (the section went out before the row was reported), that line is written again with
     * the song's identity.
     */
    fun noteLiveSong(songId: String, songbook: String, songNumber: Int, title: String, source: String) {
        runCatching {
            synchronized(lock) {
                val song = NotedSong(songId, songbook, songNumber, title, source, sourcePending = true)
                notedSong = song
                lastReported?.takeIf(song::matches)?.let(::write)
            }
        }
    }

    private fun write(entry: LiveHistoryEntry) {
        val song = notedSong?.takeIf { it.matches(entry) }
        val stamped = if (song == null) entry else entry.copy(songId = song.songId, songbook = song.songbook)
        TrainingDataLogger.cleanupOldLogsOnce()
        val file = File(path())
        // An empty or missing file starts over: it gets its header, and its first line whatever it is.
        val fresh = file.length() == 0L
        if (!fresh && lastWritten[file.path] == stamped) return
        val source = song?.takeIf { it.sourcePending }?.source
        if (fresh) file.appendText(sessionHeader() + "\n", Charsets.UTF_8)
        file.appendText(lineOf(stamped, source) + "\n", Charsets.UTF_8)
        lastWritten[file.path] = stamped
        if (source != null) notedSong = song.copy(sourcePending = false)
    }

    /** The first line of every file: which session its rows belong to, so it stands on its own. */
    private fun sessionHeader(): String = buildString {
        append("{\"type\":\"session\"")
        append(",\"ts_ms\":").append(System.currentTimeMillis())
        appendSessionId()
        append("}")
    }

    private fun lineOf(e: LiveHistoryEntry, source: String?): String = buildString {
        append("{\"ts_ms\":").append(System.currentTimeMillis())
        appendSessionId()
        appendField("contentType", e.contentType)
        appendField("source", source)
        appendField("songId", e.songId)
        appendField("songbook", e.songbook)
        appendField("songNumber", e.songNumber)
        appendField("songTitle", e.songTitle)
        appendField("sectionIndex", e.sectionIndex)
        appendField("sectionType", e.sectionType)
        appendField("lineIndex", e.lineIndex)
        appendField("verseCode", e.verseCode)
        appendField("reference", e.reference)
        appendField("fileName", e.fileName)
        appendField("slideIndex", e.slideIndex)
        append("}")
    }

    private fun StringBuilder.appendSessionId() {
        val id = TrainingDataLogger.sessionId
        if (id != null) append(",\"sessionId\":\"").append(esc(id)).append("\"")
        else append(",\"sessionId\":null")
    }

    /** `,"key":value` — a string quoted and escaped, a number bare; nothing at all when null. */
    private fun StringBuilder.appendField(key: String, value: Any?) {
        when (value) {
            null -> return
            is Number -> append(",\"").append(key).append("\":").append(value)
            else -> append(",\"").append(key).append("\":\"").append(esc(value.toString())).append("\"")
        }
    }

    /** Escapes quotes and backslashes; any control character (a tab in a title) becomes a space. */
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when {
            c == '\\' -> append("\\\\")
            c == '"' -> append("\\\"")
            c < ' ' -> append(' ')
            else -> append(c)
        }
    }
}
