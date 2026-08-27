package org.churchpresenter.app.churchpresenter

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import java.util.concurrent.atomic.AtomicReference

/**
 * Kills a fork that has stopped making progress, and says what it was doing when it stopped.
 *
 * The suite has hung outright three times (2026-07-30, then twice on 2026-08-21), each time ending
 * as a timeout rather than a failure. PR #361's per-class `START` line finally named the class --
 * `LowerThirdTabTest` -- but a class name is not a cause: the one stack captured shows the hang
 * inside `runComposeUiTest` teardown, blocked in `SwingUtilities.invokeAndWait` on the AWT event
 * queue, which means the thread that had to answer was not answering. **Which thread, and why, is
 * not in the class name.** A dump of every thread is, and that is what this writes.
 *
 * It is deliberately NOT a per-test timeout in the assertion sense: nothing here decides whether a
 * test passes, the threshold is far past anything this suite legitimately does (`AGENT.md` budgets
 * ~1s a test, and the slowest class is 37.1s for all of its tests together), and a test that trips
 * it was never going to finish. Halting rather than merely reporting is the point -- the fork stops
 * with a diagnosis instead of being killed at thirty minutes with none.
 *
 * ## What has been ruled out
 *
 * The hang has not been reproduced locally, on macOS, as of 2026-08-22. Recorded here so the next
 * attempt starts further along than this one did:
 *
 * - **`LowerThirdTabTest` alone does not do it.** Six consecutive runs of just that class under
 *   six-way CPU load, all green in ~82s. Note that passing `--tests` both stands the serial-class
 *   exclusion down *and* drops `jvmTest` to a single fork, so an isolated run is not the shape CI
 *   hangs in; do not read a green isolated run as evidence.
 * - **The full suite on four forks under load does not do it either** -- one clean run.
 * - **It is not the frozen test clock.** Twenty-four sites across the suite set
 *   `mainClock.autoAdvance = false` and none restore it, including the last test in
 *   `LowerThirdTabTest`, which leaves an animation live with the clock stopped. That looked like
 *   the answer and is not: a probe that tears down with (a) a ten-minute animation running and the
 *   clock frozen, (b) an unbounded `delay` loop with the clock frozen, and (c) the same loop with
 *   the clock auto-advancing, completes all three in under three seconds.
 *
 * ## The current lead (2026-08-27, occurrence four)
 *
 * That dump answered "what was the event queue busy with". It hung `LowerThirdFolderTest` -- a
 * different class from the three before, which is itself the point: the class is not the property
 * that matters. Two threads were `BLOCKED`, both inside `SnapshotStateObserver.drainChanges`:
 *
 * - `AWT-EventQueue-0`, in Compose Foundation's desktop scrollbar (`Scrollbar.skiko.kt` ->
 *   `getThumbPixelRange` -> `getAverageVisibleLineSize`) reading a `DerivedSnapshotState`, which
 *   calls `notifyObjectsInitialized` -> `advanceGlobalSnapshot` -> `drainChanges`.
 * - `DefaultDispatcher-worker-4`, in `LowerThirdOffscreenRenderer.withSession` calling
 *   `Snapshot.sendApplyNotifications()` per frame from `LottieRenderCache.renderToFile`. It is
 *   already inside an outer `drainChanges` and blocks entering a second.
 *
 * `advanceGlobalSnapshot` runs *every* registered apply observer, so a background off-screen scene's
 * snapshot advance reaches into the AWT scene's observer and back. `LottieRenderCache` is an
 * `object` holding its own `CoroutineScope(Dispatchers.Default)`, so a pre-render started by an
 * **earlier** test is still running during a later one -- which is why an isolated `--tests` run is
 * green (nothing started the pre-render) and why the hanging class keeps changing.
 *
 * **This is a hypothesis, not a finding.** `Thread.getAllStackTraces` carries no monitor ownership,
 * so two threads blocked in one method is consistent with a lock-order inversion and does not
 * demonstrate one. Do not re-architect off-screen rendering on the strength of it. [appendLockInfo]
 * was added for exactly this: the next occurrence prints the deadlock cycle and the lock owners, and
 * that either proves the inversion or kills it. Fix what that dump shows.
 *
 * The same shape is worth holding in mind while reading it: `ComposeScenePump` builds its
 * `ImageComposeScene` and calls `sendApplyNotifications` on `Dispatchers.Default` too, and
 * `BrowserSourceVideoRenderer` -- which rides that pump -- is the culprit on an open production
 * `ArrayIndexOutOfBoundsException` raised inside a hash-map resize during scene construction.
 */
class HungTestReporter internal constructor(
    private val thresholdMs: Long,
    private val dumpDir: String?,
) : TestExecutionListener {

    /** The no-arg constructor the service loader uses; both settings come from system properties. */
    constructor() : this(
        System.getProperty(THRESHOLD_PROPERTY)?.toLongOrNull() ?: DEFAULT_THRESHOLD_MS,
        System.getProperty(DUMP_DIR_PROPERTY),
    )

    private val running = AtomicReference<Pair<String, Long>?>(null)

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        val watchdog = Thread {
            while (true) {
                try {
                    Thread.sleep(minOf(POLL_MS, thresholdMs))
                } catch (_: InterruptedException) {
                    return@Thread
                }
                checkOnce { Runtime.getRuntime().halt(it) }
            }
        }
        watchdog.isDaemon = true
        watchdog.name = "hung-test-watchdog"
        watchdog.start()
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) running.set(testIdentifier.displayName to System.currentTimeMillis())
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (testIdentifier.isTest) running.set(null)
    }

    /**
     * One watchdog tick: if the running test has outlived [thresholdMs], writes the dump and calls
     * [halt]. Returns whether it tripped.
     *
     * [halt] is a parameter for one reason -- it is the only step a test cannot execute, since the
     * real one kills the JVM. Everything before it is real: the real elapsed check, the real
     * thread dump, the real file. A test passes a stand-in and exercises the rest.
     */
    internal fun checkOnce(halt: (Int) -> Unit): Boolean {
        val (name, startedAt) = running.get() ?: return false
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed <= thresholdMs) return false
        dump(name, elapsed)
        halt(HUNG_EXIT_CODE)
        return true
    }

    private fun dump(name: String, elapsedMs: Long) {
        val out = StringBuilder()
        out.appendLine()
        out.appendLine("=== HUNG TEST: $name has been running ${elapsedMs / 1000}s ===")
        out.appendLine("=== Every thread in this fork follows. The one blocked in Compose's")
        out.appendLine("=== teardown, and whatever the AWT event queue is doing, are the two to read.")
        appendLockInfo(out)
        Thread.getAllStackTraces().toSortedMap(compareBy { it.name }).forEach { (thread, stack) ->
            out.appendLine()
            out.appendLine("--- \"${thread.name}\" ${thread.state}${if (thread.isDaemon) " (daemon)" else ""}")
            stack.take(STACK_DEPTH).forEach { out.appendLine("        at $it") }
        }
        System.err.println(out)
        System.err.flush()
        // Also to disk, because halting the JVM can lose whatever Gradle had buffered of its
        // output -- and on CI the console is the only other copy. This directory is what the
        // workflow already uploads as `test-reports`, so the dump travels with the run.
        dumpDir?.let { dir ->
            runCatching {
                val file = java.io.File(dir).apply { mkdirs() }.resolve("hung-test-dump.txt")
                file.writeText(out.toString())
                System.err.println("=== the dump above is also at ${file.absolutePath}")
            }
        }
    }

    /**
     * Who owns which monitor, which the stacks alone cannot say.
     *
     * `Thread.getAllStackTraces` returns frames and nothing else, so a dump showing two threads
     * `BLOCKED` inside the same method proves they are both waiting and **not** what they are
     * waiting on or who holds it. The 2026-08-27 dump ended exactly there: `AWT-EventQueue-0` and a
     * `DefaultDispatcher-worker` both blocked in `SnapshotStateObserver.drainChanges`, one of them
     * called from `LowerThirdOffscreenRenderer`'s off-screen render, which *looks* like a lock-order
     * inversion between two Compose scenes on two threads and cannot be shown to be one.
     *
     * [ThreadMXBean.findDeadlockedThreads] answers it outright when the cycle is monitors or owned
     * synchronizers, and [ThreadMXBean.dumpAllThreads] with both flags prints `- locked <id>` and
     * `- waiting to lock <id>` per frame, which settles it when the cycle is something else. Best
     * effort: a JVM may refuse either, and a hang that is not a deadlock reports no cycle, so the
     * plain stacks above stay the primary record.
     */
    private fun appendLockInfo(out: StringBuilder) {
        runCatching {
            val bean = java.lang.management.ManagementFactory.getThreadMXBean()
            val deadlocked = bean.findDeadlockedThreads()
            if (deadlocked == null || deadlocked.isEmpty()) {
                out.appendLine("=== No monitor/synchronizer deadlock cycle found.")
                out.appendLine("=== (So this is a wait or a livelock, not a classic lock cycle.)")
                return@runCatching
            }
            out.appendLine()
            out.appendLine("=== DEADLOCK CYCLE: ${deadlocked.size} threads ===")
            bean.getThreadInfo(deadlocked, true, true).filterNotNull().forEach { info ->
                out.appendLine()
                out.appendLine("--- \"${info.threadName}\" ${info.threadState}")
                info.lockInfo?.let { out.appendLine("        waiting to lock $it") }
                info.lockOwnerName?.let { out.appendLine("        held by \"$it\" (id ${info.lockOwnerId})") }
                info.stackTrace.take(STACK_DEPTH).forEach { out.appendLine("        at $it") }
            }
        }.onFailure { out.appendLine("=== lock info unavailable: $it") }
    }

    internal companion object {
        /**
         * Minutes past anything real, so tripping it means stuck rather than slow.
         *
         * Overridable with `-Dchurchpresenter.test.hangThresholdMs=` so a session chasing a hang
         * can tighten it. A test instead passes the threshold to the internal constructor: setting
         * the property would retune the live watchdog running over the suite itself.
         */
        const val THRESHOLD_PROPERTY = "churchpresenter.test.hangThresholdMs"

        const val DEFAULT_THRESHOLD_MS = 5 * 60 * 1000L
        const val POLL_MS = 10_000L
        const val STACK_DEPTH = 25

        /** Distinctive, so the exit code alone says what happened. */
        const val HUNG_EXIT_CODE = 93

        /** Set by the Test task; where the dump is written so CI uploads it with the results. */
        const val DUMP_DIR_PROPERTY = "churchpresenter.test.hangDumpDir"
    }
}
