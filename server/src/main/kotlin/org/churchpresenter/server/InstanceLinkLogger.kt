package org.churchpresenter.server

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Which side of an Instance Link connection a log line describes. A single running instance can log
 *  both in the same file if it's simultaneously serving followers (PRIMARY) and following someone
 *  else (FOLLOWER). */
enum class InstanceLinkLogSide { PRIMARY, FOLLOWER }

/**
 * Best-effort JSONL diagnostic logger for Instance Link (this app's follow-another-instance feature)
 * — writes one run-stamped file per launch to ~/.churchpresenter/instance-link/logs/.
 *
 * Intended use: open the primary's and a follower's log files side by side (or diff them by ts_ms) to
 * see exactly what was broadcast/served vs. received/applied — Instance Link's REST fetches and
 * remote-state application are otherwise silent on failure (`runCatching { }.getOrNull()` paths
 * throughout `InstanceLinkClient`/`applyRemoteLiveState`), which makes sync bugs hard to diagnose
 * without re-instrumenting by hand each time.
 *
 * Same shape as CrashReporter/TrainingDataLogger: hand-rolled JSONL (no serialization library),
 * best-effort (every write wrapped in `runCatching`), and files older than [MAX_AGE_DAYS] deleted
 * once per process. A line is built where [log] is called, its timestamp with it, and written by one
 * thread of the logger's own: the primary logs from the event thread while something is live, and a
 * call never waits on the disk. A line still queued when the process is killed is lost.
 *
 * This codebase has no app-wide file-logging framework wired up (Logback is console/Sentry-only),
 * so this mirrors the existing hand-rolled convention rather than introducing a new one.
 */
object InstanceLinkLogger {

    private const val MAX_AGE_DAYS = 30L
    private const val FILE_PREFIX = "instance-link_"

    private val cleanedUp = AtomicBoolean(false)

    /** Every write, in the order the lines were logged. */
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "instance-link-log").apply { isDaemon = true }
    }

    // Frozen at first use (≈ app start), down to the second so quick restarts never share a file.
    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    // Resolved on the first caller's thread, which touches no disk -- a test fixes the path to its own
    // user.home by logging once -- and created on [writer].
    private val logDir by lazy { File(System.getProperty("user.home"), ".churchpresenter/instance-link/logs") }

    private val logFile by lazy { File(logDir, "$FILE_PREFIX$runStamp.jsonl") }

    /**
     * Appends one JSONL line. [event] is a short snake_case name (e.g. "broadcast", "fetch_result",
     * "apply_live_state") shared between primary and follower call sites so the two logs line up for
     * diffing. [fields] values may be String/Int/Long/Boolean/Double/null — anything else is
     * stringified via `toString()`.
     */
    fun log(side: InstanceLinkLogSide, event: String, fields: Map<String, Any?> = emptyMap()) {
        val file = logFile
        val line = runCatching {
            buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                append(",\"side\":\"").append(side.name.lowercase()).append('"')
                append(",\"event\":\"").append(esc(event)).append('"')
                for ((key, value) in fields) {
                    append(",\"").append(key).append("\":")
                    appendJsonValue(value)
                }
                append('}')
            }
        }.getOrNull() ?: return
        writer.execute {
            cleanupOldLogsOnce()
            runCatching {
                logDir.mkdirs()
                file.appendText(line + "\n", Charsets.UTF_8)
            }
        }
    }

    /** Waits until every line logged so far is on disk. */
    internal fun flush() {
        writer.submit { }.get()
    }

    private fun StringBuilder.appendJsonValue(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean -> append(value)
            is Int -> append(value)
            is Long -> append(value)
            is Double -> append(value)
            else -> append('"').append(esc(value.toString())).append('"')
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

    /**
     * Deletes instance-link log files older than [MAX_AGE_DAYS]. Runs at most once per process (first
     * write triggers it). Mirrors CrashReporter's/TrainingDataLogger's age-based cleanup. Best-effort.
     */
    private fun cleanupOldLogsOnce() {
        if (!cleanedUp.compareAndSet(false, true)) return
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * 24 * 60 * 60 * 1000
            logDir.listFiles()?.forEach { file ->
                val dated = file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".jsonl")
                if (dated && file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
