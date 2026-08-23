package org.churchpresenter.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Running an external command and reading what it said.
 *
 * The device pickers shell out to the platform's own enumerators, so this is the one place in the
 * widget library that starts a process. Its contract is that it never throws: a missing binary, a
 * non-zero exit and a command that hangs all have to come back as a [CommandResult] the caller can
 * read, because the alternative is a settings tab that crashes on a machine without the tool.
 *
 * Only commands present on every POSIX host are used, and each is bounded — nothing here waits on
 * a real timeout expiring.
 */
class CommandOutputTest {

    @Test
    fun `a successful command reports exit zero and its output`() {
        val result = readCommandOutput(listOf("echo", "hello"), timeoutSeconds = 5)
        assertEquals(0, result.exitCode)
        assertEquals("hello", result.output.trim())
    }

    @Test
    fun `stderr is folded into the output`() {
        val result = readCommandOutput(listOf("sh", "-c", "echo oops 1>&2"), timeoutSeconds = 5)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("oops"), "the stream is redirected, so stderr must appear")
    }

    @Test
    fun `a non-zero exit is reported rather than thrown`() {
        val result = readCommandOutput(listOf("sh", "-c", "exit 3"), timeoutSeconds = 5)
        assertEquals(3, result.exitCode)
    }

    @Test
    fun `a command that does not exist comes back as a failure, not an exception`() {
        val result = readCommandOutput(listOf("definitely-not-a-real-binary-xyz"), timeoutSeconds = 5)
        assertEquals(-1, result.exitCode)
        assertEquals("", result.output)
    }

    @Test
    fun `a zero timeout waits for the command instead of killing it`() {
        val result = readCommandOutput(listOf("echo", "waited"), timeoutSeconds = 0)
        assertEquals(0, result.exitCode)
        assertEquals("waited", result.output.trim())
    }

    @Test
    fun `output larger than a pipe buffer is read in full`() {
        // Read-before-wait is what makes this work: a command that fills the pipe would otherwise
        // block for ever waiting for someone to drain it.
        val result = readCommandOutput(listOf("sh", "-c", "seq 1 20000"), timeoutSeconds = 20)
        assertEquals(0, result.exitCode)
        assertEquals(20_000, result.output.trim().lines().size, "nothing may be lost to the buffer")
    }

    @Test
    fun `an empty command list fails rather than throwing`() {
        val result = readCommandOutput(emptyList(), timeoutSeconds = 5)
        assertEquals(-1, result.exitCode)
    }

    @Test
    fun `a result carries its two fields by value`() {
        assertEquals(CommandResult(0, "out"), CommandResult(0, "out"))
        assertEquals(2, CommandResult(2, "x").exitCode)
        assertEquals("x", CommandResult(2, "x").output)
    }
}
