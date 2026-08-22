package org.churchpresenter.app.churchpresenter

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.launcher.TestIdentifier
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The test `HungTestReporter`'s own KDoc promised and never had.
 *
 * This class is all that stands between a CI hang and no diagnosis at all, so the thing worth
 * proving is that a tripped watchdog leaves a dump on disk naming the test and the threads. It is
 * driven through the internal constructor's threshold rather than the system property: the live
 * watchdog is listening over this very suite, and retuning it globally would halt the fork.
 *
 * `halt` is the only step stubbed, because the real one kills the JVM. The check, the dump and the
 * file are all real.
 */
class HungTestReporterTest {

    private lateinit var dir: File

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    private class Probe(name: String) : AbstractTestDescriptor(
        UniqueId.forEngine("hung-test-probe").append("test", name),
        name,
    ) {
        override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
    }

    private fun reporter(thresholdMs: Long): HungTestReporter {
        dir = Files.createTempDirectory("cp-hung-dump").toFile()
        return HungTestReporter(thresholdMs = thresholdMs, dumpDir = dir.absolutePath)
    }

    private fun dumpFile() = File(dir, "hung-test-dump.txt")

    @Test
    fun `a test past the threshold is dumped, named and halted`() {
        val reporter = reporter(thresholdMs = -1)
        var haltedWith: Int? = null

        reporter.executionStarted(TestIdentifier.from(Probe("the one that hung")))
        val tripped = reporter.checkOnce { haltedWith = it }

        assertTrue(tripped, "a test past the threshold should trip the watchdog")
        assertEquals(HungTestReporter.HUNG_EXIT_CODE, haltedWith)

        val dump = dumpFile()
        assertTrue(dump.isFile, "no dump was written to ${dump.absolutePath}")
        val text = dump.readText()
        assertContains(text, "the one that hung", message = "the dump does not name the running test")
        // The point of the dump is every thread, not just the stuck one: the recorded hang is one
        // thread waiting on another, which a single stack cannot show.
        val threads = text.lines().count { it.startsWith("--- \"") }
        assertTrue(threads > 1, "the dump held $threads thread(s); it should hold every one")
    }

    /** Regression: the path line was written with an escaped `$`, so it printed its own source. */
    @Test
    fun `the dump reports where it was written`() {
        val reporter = reporter(thresholdMs = -1)
        val printed = captureStdErr {
            reporter.executionStarted(TestIdentifier.from(Probe("noisy")))
            reporter.checkOnce { }
        }
        assertContains(printed, dumpFile().absolutePath)
        assertFalse("\${file.absolutePath}" in printed, "the path was printed as its own source text")
    }

    @Test
    fun `a test inside the threshold is left alone, and so is an idle fork`() {
        val reporter = reporter(thresholdMs = 60_000)
        var halted = false

        assertFalse(reporter.checkOnce { halted = true }, "nothing is running; nothing to trip")

        val probe = TestIdentifier.from(Probe("a normal test"))
        reporter.executionStarted(probe)
        assertFalse(reporter.checkOnce { halted = true }, "a test inside the threshold should be left alone")

        assertFalse(halted)
        assertFalse(dumpFile().exists(), "a fork that never hung should leave no dump")
    }

    private fun captureStdErr(block: () -> Unit): String {
        val original = System.err
        val buffer = java.io.ByteArrayOutputStream()
        System.setErr(java.io.PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return buffer.toString()
    }
}
