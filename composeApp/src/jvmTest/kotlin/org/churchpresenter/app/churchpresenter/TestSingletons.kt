package org.churchpresenter.app.churchpresenter

import org.churchpresenter.companionserver.InstanceLinkLogSide
import org.churchpresenter.companionserver.InstanceLinkLogger

/**
 * Pins JVM-wide lazies that read a system property, forcing them to resolve against the real value
 * before any test swaps that property.
 *
 * Two properties are covered — `user.home` ([latchToTestHome]) and `os.name` ([latchSkikoHostOs]).
 * The failure mode is the same for both: a `by lazy` that resolves once per JVM keeps whatever the
 * property said at the moment it was first touched, so a test that swaps the property and happens
 * to be the one that triggers the lazy poisons every later test in that JVM — and which test that
 * is depends on execution order, so it passes on one machine and fails on another.
 *
 * [InstanceLinkLogger] resolves its log directory in a `by lazy`, so it keeps whatever `user.home`
 * pointed at the *first* time anything logged, for the rest of the JVM. A test class that swaps
 * `user.home` to a temp dir and then exercises code that logs — `ScheduleViewModel.applyRemoteSchedule`,
 * the Bible/Songs follower paths, `InstanceLinkClient` — latches the logger onto that temp dir. The
 * dir is deleted in teardown, every later write fails silently (the logger is best-effort), and
 * `InstanceLinkLoggerTest` then counts lines in a file nothing is writing to any more. The failure
 * lands in a class that did nothing wrong, which is what makes it expensive to diagnose.
 *
 * `CrashReporter` used to belong on this list too. It now resolves its four paths on every access
 * instead of caching them in fields, so it follows `user.home` rather than needing to be pinned
 * ahead of a swap — see the comment on `CrashReporter.appDir`.
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

    @Volatile private var skikoLatched = false

    /**
     * Forces skiko to resolve its host OS against the real `os.name`, before any test fakes it.
     *
     * `org.jetbrains.skiko.hostOs` is a `by lazy` that maps `os.name` onto a known OS and throws
     * `Error: Unknown OS <name>` for anything it does not recognise. `org.jetbrains.skia.Surface`
     * resolves it in its static initializer, on the way to loading the native library, and every
     * `runComposeUiTest` touches `Surface`. So a Compose test composed inside a fake `os.name` — as
     * every [org.churchpresenter.app.churchpresenter.composables.withOsName] caller does, naming an
     * OS no enumerator matches precisely so the panel spawns no processes — makes that the *first*
     * touch, and skiko throws. `Surface` is then permanently uninitialisable and every later Compose
     * test in the JVM dies with `NoClassDefFoundError: Could not initialize class
     * org.jetbrains.skia.Surface`, pointing at whichever innocent class ran next.
     *
     * Whether it bites is pure execution order: if any Compose test runs before the first faking
     * one, the lazy is already resolved and the fake is harmless. That is why CI stayed green while
     * running a single faking class on its own reproduces it every time.
     *
     * Loading `Surface` is what a Compose test does anyway, so this only moves that cost earlier.
     */
    fun latchSkikoHostOs() {
        if (skikoLatched) return
        synchronized(this) {
            if (skikoLatched) return
            // Initialising the class runs the static load that resolves skiko's hostOs lazy.
            Class.forName("org.jetbrains.skia.Surface")
            skikoLatched = true
        }
    }

    /**
     * Forces skiko to unpack and load its native library against the real `user.home`, before any
     * test swaps that property.
     *
     * `org.jetbrains.skiko.Library.unpackIfNeeded` extracts the native binary into a cache directory
     * under `user.home` and `Files.move`s it into place, once per JVM. A test that swaps `user.home`
     * to a temp dir and is the first to touch skia unpacks into that dir; the dir is deleted in
     * teardown, and every later skia class in the JVM fails with `NoClassDefFoundError: Could not
     * initialize class org.jetbrains.skia.Image` — in whichever innocent class ran next. It surfaces
     * as an `Error`, so the `catch (e: Exception)` around a thumbnail decode does not contain it and
     * the whole coroutine dies.
     *
     * Same one-time class load as [latchSkikoHostOs] — either call pins both properties — but the
     * two failures are unrelated, so a caller isolating only `user.home` says so by name.
     *
     * Call as the FIRST line of `@BeforeTest`, before `System.setProperty("user.home", …)`.
     */
    fun latchSkikoNativeLibrary() = latchSkikoHostOs()
}
