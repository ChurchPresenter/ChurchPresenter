package org.churchpresenter.ui

import java.util.concurrent.TimeUnit

/** What an external command printed — stdout and stderr merged — together with how it ended. */
data class CommandResult(val exitCode: Int, val output: String)

/**
 * Runs a command and returns what it printed.
 *
 * The camera, window and format listings take one of these as a parameter rather than calling
 * [readCommandOutput] directly, so a test can supply the output instead of the machine.
 */
typealias CommandRunner = (command: List<String>, timeoutSeconds: Long) -> CommandResult

/**
 * Runs one external command and hands back its combined stdout and stderr.
 *
 * This is the single unreachable step shared by the camera, window and format listings in
 * `SourcePropertiesPanel` and `SceneSourceRenderer`: each needs the machine's own `xprop`,
 * `wmctrl`, `osascript`, `system_profiler`, `powershell` or `ffmpeg`, and the hardware behind them.
 * Isolating it here leaves the sequence around it — which command is built, how the output is read,
 * what happens when it comes back empty or non-zero, which fallback runs next — exercised for real
 * against a stand-in runner, and collapses ten near-identical `ProcessBuilder` blocks into one.
 *
 * A command that cannot be started at all is reported as exit code -1 with empty output, which is
 * how every caller already treated the exception it used to throw. [timeoutSeconds] above zero
 * force-kills a command that outlives it; zero waits for it indefinitely.
 */
fun readCommandOutput(command: List<String>, timeoutSeconds: Long): CommandResult {
    return try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        // Read before waiting: a command that fills the pipe buffer never exits otherwise.
        val output = process.inputStream.bufferedReader().readText()
        if (timeoutSeconds > 0) {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) process.destroyForcibly()
        } else {
            process.waitFor()
        }
        val exitCode = try {
            process.exitValue()
        } catch (_: IllegalThreadStateException) {
            -1 // force-killed on timeout and not yet reaped
        }
        CommandResult(exitCode, output)
    } catch (_: Exception) {
        CommandResult(-1, "")
    }
}
