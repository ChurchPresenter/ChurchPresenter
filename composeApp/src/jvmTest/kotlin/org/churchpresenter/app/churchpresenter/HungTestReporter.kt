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
 * test passes, [THRESHOLD_MS] is minutes past anything this suite legitimately does (`AGENT.md`
 * budgets ~1s a test, and the slowest screenshot class is ~33s for all of its tests together), and
 * a test that trips it was never going to finish. Halting rather than merely reporting is the point
 * -- the fork stops in five minutes with a diagnosis instead of being killed at thirty with none.
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
