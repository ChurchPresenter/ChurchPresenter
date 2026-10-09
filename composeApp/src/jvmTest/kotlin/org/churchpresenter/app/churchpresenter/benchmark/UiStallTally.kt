package org.churchpresenter.app.churchpresenter.benchmark

/**
 * How the UI thread answered `UiWatchdog`'s pings over one soak window: how many answers came later
 * than [budgetMs], and the latest. The watchdog's thread [record]s each answer; the soak [take]s the
 * window's figures at every sample, which starts the next window.
 */
class UiStallTally(private val budgetMs: Long) {
    private var stalls = 0
    private var longestMs = 0L

    /** A ping was answered [lateMs] after it was posted. */
    @Synchronized
    fun record(lateMs: Long) {
        if (lateMs > budgetMs) stalls++
        longestMs = maxOf(longestMs, lateMs)
    }

    /** This window's stalls and longest answer, in ms, and a fresh window after them. */
    @Synchronized
    fun take(): Pair<Int, Long> = (stalls to longestMs).also {
        stalls = 0
        longestMs = 0
    }
}
