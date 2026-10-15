package org.churchpresenter.outputguard

import org.churchpresenter.diagnostics.Log
import java.awt.EventQueue
import java.awt.Rectangle
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The app's side of its output guard (`docs/SHOW_CONTROL.md`, Output isolation): it starts the guard
 * process, beats a heartbeat from a thread of its own and a tick from the event thread, tells the
 * guard where the app's outputs are, hands it a still of each, and starts it again if it dies.
 * [stop] tells the guard the app is quitting on purpose, so it leaves without showing anything.
 *
 * [startGuard] starts the guard process for a folder and [relaunchCommand] is how the guard would
 * start this app again; both are the caller's, since only the app knows how it was launched. The
 * heartbeat writes every [beatMs].
 */
class OutputGuardLink(
    private val files: GuardFiles,
    private val startGuard: (GuardFiles) -> Process?,
    relaunchCommand: List<String>,
    private val pid: Long = ProcessHandle.current().pid(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val beatMs: Long = BEAT_MS,
) {
    /** An output the guard follows: its id, and how to ask whether it shows and where it is. */
    private class Watched(val id: Int, val isShowing: () -> Boolean, val bounds: () -> Rectangle)

    private val state = GuardState.open(files.state)
    private val watched = mutableMapOf<Any, Watched>()
    private val seqs = mutableMapOf<Int, Long>()
    private val nextId = AtomicInteger(1)
    private val stopped = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val tickPending = AtomicBoolean(false)
    private val lastGuardStart = AtomicLong(NEVER)

    @Volatile private var guard: Process? = null

    private val retired = ConcurrentLinkedQueue<Int>()
    private val heartbeat = Thread(::beat, "output-guard-heartbeat").apply { isDaemon = true }

    init {
        state.reset(pid, clock())
        files.writeRelaunchCommand(relaunchCommand)
    }

    /** Starts the guard and the heartbeat, once, and never after [stop]. */
    fun start(): OutputGuardLink {
        if (stopped.get() || !started.compareAndSet(false, true)) return this
        launchGuard()
        heartbeat.start()
        return this
    }

    /** Whether the guard covers a hung UI as well as a dead app. */
    var takeOverOnHang: Boolean
        get() = state.takeOverOnHang
        set(on) {
            state.takeOverOnHang = on
        }

    /** Whether any output shows content; the guard covers a hang only while one does. */
    var live: Boolean
        get() = state.live
        set(isLive) {
            state.live = isLive
        }

    /**
     * An output is up, under [key]: the guard follows it while [isShowing] says so, over [bounds].
     * Registering the same key again keeps its id.
     */
    fun register(key: Any, isShowing: () -> Boolean, bounds: () -> Rectangle): Int = synchronized(watched) {
        watched.getOrPut(key) { Watched(nextId.getAndIncrement(), isShowing, bounds) }.id
    }

    /** The output under [key] is gone; its still is deleted on the heartbeat thread, not the caller's. */
    fun unregister(key: Any) {
        val id = synchronized(watched) { watched.remove(key) }?.id ?: return
        synchronized(seqs) { seqs.remove(id) }
        retired.add(id)
    }

    /** What the guard follows, by id: the keys the outputs were registered under -- the app's windows. */
    fun outputs(): Map<Int, Any> = synchronized(watched) {
        watched.entries.associate { (key, output) -> output.id to key }
    }

    /** Stores the still of output [id] -- BGRA, [width] by [height] -- and tells the guard it is there. */
    fun snapshot(id: Int, width: Int, height: Int, bgra: ByteArray) {
        if (stopped.get()) return
        files.writeSnapshot(id, width, height, bgra)
        synchronized(seqs) { seqs[id] = (seqs[id] ?: 0L) + 1 }
    }

    /** Stores what is on air, for an app the guard relaunches to put back. */
    fun saveLiveState(text: String) {
        if (!stopped.get()) files.writeLiveState(text)
    }

    /** The app is quitting, or the guard was switched off: the guard leaves and shows nothing. */
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        state.closing = true
        heartbeat.interrupt()
    }

    private fun beat() {
        try {
            while (!stopped.get()) {
                state.heartbeatAt = clock()
                generateSequence { retired.poll() }.forEach(files::deleteSnapshot)
                // Where the showing outputs are, and their latest stills; up once there is one.
                val showing = synchronized(watched) { watched.values.filter { it.isShowing() } }
                state.outputs = showing.map { output ->
                    GuardedOutput(output.id, output.bounds(), synchronized(seqs) { seqs[output.id] ?: 0L })
                }
                if (showing.isNotEmpty()) state.outputsUp = true
                // One tick at a time through the event queue: when it runs is when the event thread
                // last answered.
                if (tickPending.compareAndSet(false, true)) {
                    EventQueue.invokeLater {
                        state.uiTickAt = clock()
                        tickPending.set(false)
                    }
                }
                if (guard?.isAlive == false) launchGuard()
                Thread.sleep(beatMs)
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }

    /** Starts the guard, at most once every few seconds should it keep dying. */
    private fun launchGuard() {
        val now = clock()
        val last = lastGuardStart.get()
        if (stopped.get() || (last != NEVER && now - last < RESTART_GAP_MS)) return
        lastGuardStart.set(now)
        guard = runCatching { startGuard(files) }
            .onFailure { Log.warn(TAG, "The output guard did not start: ${it.message}") }
            .getOrNull()
    }

    private companion object {
        const val BEAT_MS = 250L
        const val RESTART_GAP_MS = 5_000L
        const val NEVER = Long.MIN_VALUE
        const val TAG = "OutputGuard"
    }
}
