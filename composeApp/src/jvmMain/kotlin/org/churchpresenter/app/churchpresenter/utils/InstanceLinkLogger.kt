package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
 * best-effort (every write wrapped in `runCatching`), thread-safe via a private lock, and files older
 * than [MAX_AGE_DAYS] deleted once per process — this codebase has no app-wide file-logging framework
 * wired up (Logback is console/Sentry-only), so this mirrors the existing hand-rolled convention
 * rather than introducing a new one.
 */
object InstanceLinkLogger {

    private const val MAX_AGE_DAYS = 30L
    private const val FILE_PREFIX = "instance-link_"

    private val lock = Any()
    private val cleanedUp = AtomicBoolean(false)

    // Frozen at first use (≈ app start), down to the second so quick restarts never share a file.
    private val runStamp: String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))

    private val logDir by lazy {
        File(System.getProperty("user.home"), ".churchpresenter/instance-link/logs").also { it.mkdirs() }
    }

    private val logFile by lazy { File(logDir, "$FILE_PREFIX$runStamp.jsonl") }

    /**
     * Appends one JSONL line. [event] is a short snake_case name (e.g. "broadcast", "fetch_result",
     * "apply_live_state") shared between primary and follower call sites so the two logs line up for
     * diffing. [fields] values may be String/Int/Long/Boolean/Double/null — anything else is
     * stringified via `toString()`.
     */
    fun log(side: InstanceLinkLogSide, event: String, fields: Map<String, Any?> = emptyMap()) {
        cleanupOldLogsOnce()
        runCatching {
            val line = buildString {
                append("{\"ts_ms\":").append(System.currentTimeMillis())
                append(",\"side\":\"").append(side.name.lowercase()).append('"')
                append(",\"event\":\"").append(esc(event)).append('"')
                for ((key, value) in fields) {
                    append(",\"").append(key).append("\":")
                    appendJsonValue(value)
                }
                append('}')
            }
            synchronized(lock) { logFile.appendText(line + "\n", Charsets.UTF_8) }
        }
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
