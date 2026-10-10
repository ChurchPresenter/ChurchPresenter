package org.churchpresenter.server

/**
 * The port a suite binds, given the base it was written against. `:server` runs its suite in one JVM,
 * so no two suites ever bind at once and the base is the port -- the number in the source is the
 * number in the log.
 */
internal fun testPort(base: Int): Int = base

/** The once-per-JVM paths that must resolve against the suite's own home before a test swaps it. */
internal object TestSingletons {
    @Volatile private var latched = false

    @Volatile private var skikoLatched = false

    /** Forces [InstanceLinkLogger] to resolve its log directory now, before any `user.home` swap. */
    fun latchToTestHome() {
        if (latched) return
        synchronized(this) {
            if (latched) return
            InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "test_home_latch")
            latched = true
        }
    }

    /**
     * Unpacks skia's native library into the suite's own home, before a test points `user.home` at a
     * temp dir it later deletes -- otherwise every later skia class in the JVM fails to initialise.
     */
    fun latchSkikoNativeLibrary() {
        if (skikoLatched) return
        synchronized(this) {
            if (skikoLatched) return
            Class.forName("org.jetbrains.skia.Surface")
            skikoLatched = true
        }
    }
}
