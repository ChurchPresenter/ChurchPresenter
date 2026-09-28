package org.churchpresenter.app.churchpresenter.composables

/**
 * [script] as a command line for the host's own shell: `cmd /c` on Windows, `sh -c` elsewhere.
 *
 * Windows has no `sh`, `true` or `echo` program -- `echo` is built into cmd, and the others exist
 * only inside Git for Windows -- so a test spelling them out passed when Gradle was started from
 * Git Bash and failed from cmd. Scripts passed here must mean the same thing to both shells:
 * `&&` to chain, `>&2` to write to stderr, `exit N` for an exit code.
 */
internal fun shellCommand(script: String): List<String> =
    if (System.getProperty("os.name").lowercase().contains("win")) listOf("cmd", "/c", script)
    else listOf("sh", "-c", script)
