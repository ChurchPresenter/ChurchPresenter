package org.churchpresenter.diagnostics

import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * What one launch measured: how long from the JVM starting to `main` running, and to the main
 * window's first frame, and the memory the app sits at once it has settled.
 */
data class StartupTimes(
    val toMainMs: Long,
    val toFirstFrameMs: Long,
    /** Java heap in use after a full collection, [StartupProbe.idleSeconds] after the first frame. */
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
 * How long the app takes to start, for the startup budget in `composeApp/benchmarks/budgets.md`.
 *
 * Off unless `-Dchurchpresenter.startupProbe=<file>` is set; then [mainStarted] and [firstFrame]
 * record when each happened, measured from the JVM's own start, and after [idleSeconds] of the
 * app sitting idle the heap and resident memory are sampled, [StartupTimes] goes to the file and
 * [exit] is called. `./gradlew :composeApp:startupBenchmark` launches the app that way several
 * times. Every step but the clock and the exit is a plain function a test drives.
 */
object StartupProbe {

    const val PROPERTY = "churchpresenter.startupProbe"
    const val IDLE_PROPERTY = "churchpresenter.startupProbe.idleSeconds"
    private const val DEFAULT_IDLE_SECONDS = 30L
    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val KB_PER_MB = 1024.0
    private const val MILLIS_PER_SECOND = 1000L

    /** Where the result goes, or null when the probe is off. */
    val target: File? get() = System.getProperty(PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File)

    val idleSeconds: Long get() = System.getProperty(IDLE_PROPERTY)?.toLongOrNull() ?: DEFAULT_IDLE_SECONDS

    @Volatile private var toMainMs: Long? = null
    @Volatile private var finishing = false

    /** Milliseconds since this JVM started. */
    fun sinceJvmStart(): Long = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime

    /** `main` is running. */
    fun mainStarted(now: Long = sinceJvmStart()) {
        if (target != null) toMainMs = now
    }

    /**
     * The main window drew its first frame. With the probe on, starts the idle wait, the sample and
     * the write on a thread of its own, then calls [exit] -- the app's own way out; a second call
     * does nothing.
     */
    fun firstFrame(exit: () -> Unit, now: Long = sinceJvmStart()) {
        val file = target ?: return
        if (finishing) return
        finishing = true
        Thread({
            try {
                Thread.sleep(idleSeconds * MILLIS_PER_SECOND)
            } catch (_: InterruptedException) {
                return@Thread
            }
            write(file, times(now))
            exit()
        }, "startup-probe").apply { isDaemon = true }.start()
    }

    /** The launch so far, with the memory sampled now. */
    fun times(firstFrameMs: Long): StartupTimes {
        @Suppress("ExplicitGarbageCollectionCall") // idle memory is what survives a collection
        System.gc()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used / BYTES_PER_MB
        return StartupTimes(toMainMs ?: -1, firstFrameMs, heap, residentMb())
    }

    /** [times] to [file], creating its directory. */
    fun write(file: File, times: StartupTimes) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(times.toJson() + "\n")
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
}
