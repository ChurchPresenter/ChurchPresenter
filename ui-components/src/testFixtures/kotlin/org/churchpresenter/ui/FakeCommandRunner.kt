package org.churchpresenter.ui


/**
 * A [CommandRunner] that answers from a script instead of the machine, and records what it was asked.
 *
 * Every enumeration in `SourcePropertiesPanel` and `SceneSourceRenderer` takes its runner as a
 * parameter so this can stand in for `xprop`, `ffmpeg`, `osascript` and the rest. Recording the calls
 * matters as much as the answers: several of these paths are defined by *which* command they run
 * next — whether the v4l2-ctl fallback fires, whether the walk stops at the first matching window —
 * and that is only observable from the outside as the sequence of commands issued.
 *
 * [answer] receives the whole command line, so a test matches on however much of it it cares about.
 * Anything unmatched comes back as exit 0 with no output, which is what a tool that ran and found
 * nothing looks like.
 */
class FakeCommandRunner(private val answer: (List<String>) -> CommandResult?) {

    /** Every command line passed to [run], in order. */
    val calls = mutableListOf<List<String>>()

    /** The timeout each call asked for, positionally matching [calls]. */
    val timeouts = mutableListOf<Long>()

    /** The first token of each command, which is usually all a test needs to assert on. */
    val programs: List<String> get() = calls.map { it.firstOrNull() ?: "" }

    fun run(command: List<String>, timeoutSeconds: Long): CommandResult {
        calls += command
        timeouts += timeoutSeconds
        return answer(command) ?: CommandResult(0, "")
    }

    companion object {
        /** A runner where every command succeeds with [output]. */
        fun alwaysReturning(output: String) = FakeCommandRunner { CommandResult(0, output) }

        /** A runner where every command fails the way an absent tool does. */
        fun alwaysFailing() = FakeCommandRunner { CommandResult(-1, "") }
    }
}
