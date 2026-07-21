package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger

/**
 * Pins the JVM-wide singletons that resolve a path from `user.home` to the real test home
 * (`build/test-home`) before any test swaps that property.
 *
 * [InstanceLinkLogger] resolves its log directory in a `by lazy`, so it keeps whatever `user.home`
 * pointed at the *first* time anything logged, for the rest of the JVM. A test class that swaps
 * `user.home` to a temp dir and then exercises code that logs — `ScheduleViewModel.applyRemoteSchedule`,
 * the Bible/Songs follower paths, `InstanceLinkClient` — latches the logger onto that temp dir. The
 * dir is deleted in teardown, every later write fails silently (the logger is best-effort), and
 * `InstanceLinkLoggerTest` then counts lines in a file nothing is writing to any more. The failure
 * lands in a class that did nothing wrong, which is what makes it expensive to diagnose.
 *
 * Call [latchToTestHome] as the FIRST line of `@BeforeTest`, before `System.setProperty("user.home", …)`.
 * It is idempotent and costs one appended line per JVM.
 */
object TestSingletons {

    @Volatile private var latched = false

    fun latchToTestHome() {
        if (latched) return
        synchronized(this) {
            if (latched) return
            // The only public entry point that forces the logger's lazy path resolution.
            InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "test_home_latch")
            latched = true
        }
    }
}
