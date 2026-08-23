package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.readCommandOutput

/**
 * [readCommandOutput], the one step the device and window listings cannot do without a real machine.
 *
 * It is tested against real child processes rather than a mock, because what is being asserted *is*
 * the process handling: that stderr arrives merged with stdout, that a non-zero exit is reported
 * rather than swallowed, and that a command which does not exist comes back as a value instead of
 * throwing — every caller relies on that last one, since half these tools are absent on any given
 * platform. The commands used are `echo`, `true`, `false` and short `sh -c` one-liners, so the whole
 * class costs a few milliseconds.
 *
 * Not covered: the timeout actually *expiring*. The parameter is in whole seconds, so provoking it
 * would cost at least a second of wall clock for one branch — more than the entire rest of this
 * file. The success side of the same branch is covered, which is the one that runs in production
 * every time a camera answers.
 */
class CommandOutputTest {

    @Test
    fun `a command's stdout is returned with its exit code`() {
        val result = readCommandOutput(listOf("echo", "hello"), 0L)

        assertEquals(0, result.exitCode)
        assertEquals("hello", result.output.trim())
    }

    @Test
    fun `stderr arrives merged into the same output`() {
        // ffmpeg prints its device and format listings to stderr, so a reader that took only stdout
        // would come back empty from every camera enumeration in the app.
        val result = readCommandOutput(listOf("sh", "-c", "echo oops >&2"), 0L)

        assertEquals("oops", result.output.trim())
    }

    @Test
    fun `stdout and stderr are interleaved rather than one replacing the other`() {
        val result = readCommandOutput(listOf("sh", "-c", "echo out; echo err >&2"), 0L)

        assertTrue("out" in result.output, "stdout must survive the merge")
        assertTrue("err" in result.output, "stderr must survive the merge")
    }

    @Test
    fun `a non-zero exit is reported rather than swallowed`() {
        // The wmctrl fallback distinguishes "ran and found nothing" from "not installed" purely by
        // this number, so it has to be the command's own and not a stand-in.
        val result = readCommandOutput(listOf("sh", "-c", "exit 3"), 0L)

        assertEquals(3, result.exitCode)
    }

    @Test
    fun `a failing command still hands back whatever it printed first`() {
        val result = readCommandOutput(listOf("sh", "-c", "echo partial; exit 1"), 0L)

        assertEquals(1, result.exitCode)
        assertEquals("partial", result.output.trim())
    }

    @Test
    fun `a command that does not exist is a value, not an exception`() {
        // Most of these tools are absent on any given platform — v4l2-ctl off Linux, osascript off
        // macOS. Every caller treats that as "this source knows nothing", so it must not throw.
        val result = readCommandOutput(listOf("definitely-not-a-real-command-xyz"), 0L)

        assertEquals(-1, result.exitCode)
        assertEquals("", result.output)
    }

    @Test
    fun `a command finishing inside its timeout is reported normally`() {
        val result = readCommandOutput(listOf("echo", "quick"), 5L)

        assertEquals(0, result.exitCode)
        assertEquals("quick", result.output.trim())
    }

    @Test
    fun `a command with no output at all succeeds with an empty string`() {
        val result = readCommandOutput(listOf("true"), 0L)

        assertEquals(0, result.exitCode)
        assertEquals("", result.output)
    }

    @Test
    fun `an empty command line is survived like any other failure`() {
        val result = readCommandOutput(emptyList(), 0L)

        assertEquals(-1, result.exitCode)
        assertEquals("", result.output)
    }
}
