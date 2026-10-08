package org.churchpresenter.diagnostics

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale

private const val BYTES_PER_MB = 1024.0 * 1024.0
private const val KB_PER_MB = 1024.0
private const val MILLIS_PER_SECOND = 1000L

/**
 * What one launch measured: how long from the JVM starting to `main` running, and to the main
 * window's first frame, and the memory the app sits at once it has settled.
 */
data class StartupTimes(
    val toMainMs: Long,
    val toFirstFrameMs: Long,
    /** Java heap in use after a full collection, once the app has sat idle. */
    val idleHeapMb: Double,
    /** The process's resident set at the same moment, or null where the OS does not say. */
    val idleRssMb: Double?,
) {
    /** One launch as JSON, for `startupBenchmark` to collect. */
    fun toJson(): String {
        val rss = idleRssMb?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "null"
        return "{\"toMainMs\": $toMainMs, \"toFirstFrameMs\": $toFirstFrameMs, " +
            "\"idleHeapMb\": ${String.format(Locale.ROOT, "%.1f", idleHeapMb)}, \"idleRssMb\": $rss}"
    }
}

/**
 * One measured launch, written to [file]: [mainStarted] and [firstFrame] record when each happened;
 * [idleSeconds] after the first frame the memory is sampled, [StartupTimes] is written and the
 * launch exits. One that has not drawn its window [deadlineSeconds] after `main` writes
 * [NO_FIRST_FRAME] and exits instead, so a benchmark never leaves an app open. Each exit is the
 * caller's own, so a test passes one that only records.
 */
class StartupRun(
    private val file: File,
    private val idleSeconds: Long,
    private val deadlineSeconds: Long,
) {
    @Volatile private var toMainMs = -1L
    @Volatile private var finishing = false

    /** `main` is running at [now]; starts the deadline. */
    fun mainStarted(now: Long, exit: () -> Unit) {
        toMainMs = now
        after(deadlineSeconds, "startup-probe-deadline") {
            if (!finishing) {
                write(NO_FIRST_FRAME)
                exit()
            }
        }
    }

    /** The main window drew its first frame at [now]: waits out the idle time, writes, exits. Once. */
    fun firstFrame(now: Long, exit: () -> Unit) {
        if (finishing) return
        finishing = true
        after(idleSeconds, "startup-probe") {
            write(times(now).toJson())
            exit()
        }
    }

    /** The launch so far, with the memory sampled now. */
    fun times(firstFrameMs: Long): StartupTimes {
        @Suppress("ExplicitGarbageCollectionCall") // idle memory is what survives a collection
        System.gc()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / BYTES_PER_MB
        return StartupTimes(toMainMs, firstFrameMs, heap, residentMb())
    }

    private fun write(line: String) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(line + "\n")
    }

    private fun after(seconds: Long, name: String, then: () -> Unit) {
        Thread({
            try {
                Thread.sleep(seconds * MILLIS_PER_SECOND)
            } catch (_: InterruptedException) {
                return@Thread
            }
            then()
        }, name).apply { isDaemon = true }.start()
    }

    companion object {
        /** What a launch that never drew its main window writes instead of [StartupTimes]. */
        const val NO_FIRST_FRAME = "{\"error\": \"no first frame before the deadline\"}"
    }
}

/**
 * How long the app takes to start, for the startup budget in `composeApp/benchmarks/budgets.md`.
 *
 * Off unless `-Dchurchpresenter.startupProbe=<file>` is set; then the app's launch is a [StartupRun]
 * writing there. `./gradlew :composeApp:startupBenchmark` launches the app that way several times.
 */
object StartupProbe {

    const val PROPERTY = "churchpresenter.startupProbe"
    const val IDLE_PROPERTY = "churchpresenter.startupProbe.idleSeconds"
    const val DEADLINE_PROPERTY = "churchpresenter.startupProbe.deadlineSeconds"
    private const val DEFAULT_IDLE_SECONDS = 30L
    private const val DEFAULT_DEADLINE_SECONDS = 180L

    /** The launch the system properties ask for, or null when the probe is off. */
    fun fromProperties(): StartupRun? {
        val file = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        val idle = System.getProperty(IDLE_PROPERTY)?.toLongOrNull() ?: DEFAULT_IDLE_SECONDS
        val deadline = System.getProperty(DEADLINE_PROPERTY)?.toLongOrNull() ?: DEFAULT_DEADLINE_SECONDS
        return StartupRun(file, idle, deadline)
    }

    private val run: StartupRun? by lazy { fromProperties() }

    /** Milliseconds since this JVM started. */
    fun sinceJvmStart(): Long = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime

    /** `main` is running; [exit] is how the app leaves if it never draws its window. */
    fun mainStarted(exit: () -> Unit) {
        run?.mainStarted(sinceJvmStart(), exit)
    }

    /** The main window drew its first frame; [exit] is how the app leaves once it is measured. */
    fun firstFrame(exit: () -> Unit) {
        run?.firstFrame(sinceJvmStart(), exit)
    }
}

/**
 * Resident memory: `/proc/self/status` on Linux, `ps` elsewhere (macOS has no `/proc`). Null on
 * Windows, where neither exists.
 */
internal fun residentMb(
    proc: File = File("/proc/self/status"),
    ps: () -> String? = ::psResidentKb,
): Double? = runCatching {
    if (proc.isFile) {
        proc.readLines().firstOrNull { it.startsWith("VmRSS:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toDouble()?.div(KB_PER_MB)
    } else {
        ps()?.trim()?.toDoubleOrNull()?.div(KB_PER_MB)
    }
}.getOrNull()

internal fun psResidentKb(): String? = runCatching {
    val process = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString())
        .redirectErrorStream(true).start()
    process.inputStream.bufferedReader().readText().also { process.waitFor() }
}.getOrNull()
