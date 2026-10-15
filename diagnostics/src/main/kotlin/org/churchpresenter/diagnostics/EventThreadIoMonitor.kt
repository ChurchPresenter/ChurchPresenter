package org.churchpresenter.diagnostics

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingStream
import java.io.File

/**
 * One blocking call the AWT event thread made: what kind ([kind]: a file read or write, a socket
 * read or write, a sleep), on what ([target]: a path or a host, empty for a sleep), how long it
 * took, and the event thread's frames at the time, the top first.
 */
data class BlockedCall(val kind: String, val target: String, val durationMs: Double, val frames: List<String>) {
    /** The frames that are the app's own, the part that says which code to change. */
    val appFrames: List<String> get() = frames.filter { it.startsWith(APP_PACKAGE) }

    /** The frame that names the place: the first of the app's own, or the top one. */
    fun place(): String = appFrames.firstOrNull() ?: frames.firstOrNull() ?: "an unknown frame"

    /** One line for a log or a report: the kind, the target, how long and where. */
    fun describe(): String =
        "$kind${if (target.isEmpty()) "" else " $target"} for ${"%.1f".format(durationMs)} ms at ${place()}"
}

/**
 * Watches the AWT event thread, through JFR, for the blocking work a source rule cannot see: a file
 * read or write, a socket read or write, a sleep -- however deep in a helper it happens
 * (`docs/SHOW_CONTROL.md`, Output isolation; AGENT.md, "Live-safe output paths"). Every output
 * window composes on that thread, so each one froze the projector for its duration.
 *
 * Reading the app's own code -- a jar, a class file, anything under the class path or the JDK -- is
 * class loading, not the app's work, and is never reported; nor is the kernel's random device, which
 * a UUID reads and which never waits on a disk, nor the console a log line is written to. Each
 * place -- its kind, target and first app frame -- is reported once to [onBlocked], on JFR's own
 * thread. The soak test fails on them; a debug build asked to (`DevFlags.watchEventThreadIo`) logs them.
 *
 * Its tests drive [accept] with an event's fields: a real JFR stream delivers its events about a
 * second after they happen, past what a unit test may cost, so [start] and the reading of a
 * recorded event are covered by the soak test instead.
 */
class EventThreadIoMonitor(
    private val onBlocked: (BlockedCall) -> Unit,
    private val codeRoots: List<String> = defaultCodeRoots(),
) : AutoCloseable {

    private val seen = mutableSetOf<String>()
    private var stream: RecordingStream? = null

    /** Starts listening; the first events arrive once JFR has started, a moment later. */
    @Synchronized
    fun start(): EventThreadIoMonitor {
        if (stream != null) return this
        stream = RecordingStream().apply {
            EVENTS.forEach { name -> enable(name).withThreshold(java.time.Duration.ZERO).withStackTrace() }
            EVENTS.forEach { name -> onEvent(name, ::accept) }
            startAsync()
        }
        return this
    }

    /** Stops listening. */
    @Synchronized
    override fun close() {
        stream?.close()
        stream = null
    }

    /**
     * One JFR event, as its fields: reported when it ran on the event thread, did work of the app's,
     * and is a place not yet reported. Everything [start] hands over comes through here.
     */
    internal fun accept(thread: String?, eventType: String, target: String, durationNanos: Long, frames: List<String>) {
        if (thread?.startsWith(EVENT_THREAD_PREFIX) != true || isHarmless(target)) return
        // A file event with no path is the console -- a log line going out -- not a file.
        if (target.isEmpty() && eventType in FILE_EVENTS) return
        val call = BlockedCall(KINDS.getValue(eventType), target, durationNanos / NANOS_PER_MS, frames)
        val first = synchronized(seen) { seen.add(call.describe().substringBefore(" for ") + call.place()) }
        if (first) onBlocked(call)
    }

    private fun accept(event: RecordedEvent) {
        val target = when {
            event.hasField("path") -> event.getString("path").orEmpty()
            event.hasField("host") -> event.getString("host").orEmpty()
            else -> ""
        }
        val frames = event.stackTrace?.frames.orEmpty().map { frame ->
            "${frame.method.type.name}.${frame.method.name}(line ${frame.lineNumber})"
        }
        accept(event.thread?.javaName, event.eventType.name, target, event.duration.toNanos(), frames)
    }

    /**
     * Whether reading [path] is no work of the app's: its own or the JDK's code, read to load a class
     * or a resource, or the kernel's random device.
     */
    internal fun isHarmless(path: String): Boolean =
        path in RANDOM_DEVICES || path.endsWith(".jar") || path.endsWith(".class") ||
            codeRoots.any { path.startsWith(it) }

    companion object {
        private val KINDS = mapOf(
            "jdk.FileRead" to "file read",
            "jdk.FileWrite" to "file write",
            "jdk.SocketRead" to "socket read",
            "jdk.SocketWrite" to "socket write",
            "jdk.ThreadSleep" to "sleep",
        )
        private val EVENTS = KINDS.keys
        private val FILE_EVENTS = setOf("jdk.FileRead", "jdk.FileWrite")
        private const val EVENT_THREAD_PREFIX = "AWT-EventQueue"
        private val RANDOM_DEVICES = setOf("/dev/urandom", "/dev/random")
        private const val NANOS_PER_MS = 1_000_000.0

        /**
         * Logs every blocking call the event thread makes, each place once, through [Log] -- a
         * debug build's view of the same check the soak test fails on, when asked for: the
         * recording costs a launch about 150 ms and 140 MB. JFR is started on a thread of its own,
         * so startup does not wait for it.
         */
        fun startLogging() {
            Thread({ EventThreadIoMonitor(onBlocked = { Log.warn(TAG, it.describe()) }).start() }, "event-thread-io")
                .apply { isDaemon = true }
                .start()
        }

        private const val TAG = "EventThreadIo"

        /** The class path's directories and the JDK's home: what class loading reads. */
        fun defaultCodeRoots(): List<String> =
            System.getProperty("java.class.path").orEmpty().split(File.pathSeparator)
                .filter { it.isNotEmpty() && File(it).isDirectory } + listOfNotNull(System.getProperty("java.home"))
    }
}

private const val APP_PACKAGE = "org.churchpresenter."
