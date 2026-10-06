package org.churchpresenter.diagnostics

import java.lang.management.ManagementFactory

/**
 * A dump of every thread in this JVM, with who owns which monitor -- what a hang or a stall is
 * diagnosed from. Shared by the test suite's hung-test reporter and the live UI watchdog.
 */
object ThreadDump {

    /** How many frames of each thread's stack a dump shows. */
    const val STACK_DEPTH = 25

    /** The lock section, then every thread's stack in name order, under [header]. */
    fun text(header: String): String = buildString {
        appendLine()
        appendLine(header)
        appendLockInfo(this)
        Thread.getAllStackTraces().toSortedMap(compareBy { it.name }).forEach { (thread, stack) ->
            appendLine()
            appendLine("--- \"${thread.name}\" ${thread.state}${if (thread.isDaemon) " (daemon)" else ""}")
            stack.take(STACK_DEPTH).forEach { appendLine("        at $it") }
        }
    }

    /** The current stack of the thread called [name], top frame first, or null when there is none. */
    fun stackOf(name: String, depth: Int = STACK_DEPTH): List<StackTraceElement>? =
        Thread.getAllStackTraces().entries.firstOrNull { it.key.name == name }?.value?.take(depth)

    /**
     * Who owns which monitor, which the stacks alone cannot say.
     *
     * `Thread.getAllStackTraces` returns frames and nothing else, so two threads `BLOCKED` in the
     * same method prove they are both waiting and not what they wait on or who holds it.
     * [java.lang.management.ThreadMXBean.findDeadlockedThreads] answers it outright when the cycle is
     * monitors or owned synchronizers, and `getThreadInfo` with both flags names each lock and its
     * owner. Best effort: a JVM may refuse either, and a hang that is not a deadlock reports no
     * cycle, so the plain stacks stay the primary record.
     */
    internal fun appendLockInfo(out: StringBuilder) {
        runCatching {
            val bean = ManagementFactory.getThreadMXBean()
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
}
