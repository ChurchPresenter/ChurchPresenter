package org.churchpresenter.canvas

/**
 * Runs [block] with `os.name` reporting [name], restoring the real value afterwards whatever happens.
 *
 * The camera and screen-capture panels both branch on `os.name` to decide *how* to enumerate the
 * machine — `/dev/video*`, DirectShow, AVFoundation, `xprop`, `osascript`. Every one of those either
 * spawns a process or, on macOS, can raise an accessibility prompt that blocks the run. Naming an OS
 * the panel has no enumerator for sends it down its own empty-list path with no process spawned at
 * all, which is what makes the surrounding controls — the mode dropdown, the Refresh button, the
 * "nothing found" message — testable at all.
 *
 * Skiko reads `os.name` through a JVM-wide `by lazy` and throws on a name it does not know, so the
 * latch below has to happen before the swap — see [TestSingletons.latchSkikoHostOs] for what breaks
 * without it, and why it breaks only on some machines.
 */
fun <T> withOsName(name: String, block: () -> T): T {
    TestSingletons.latchSkikoHostOs()
    val previous = System.getProperty("os.name")
    System.setProperty("os.name", name)
    return try {
        block()
    } finally {
        System.setProperty("os.name", previous)
    }
}

/** An OS no branch of the panel claims to enumerate, so every listing returns empty immediately. */
const val OS_WITHOUT_ENUMERATOR = "TestOS"
