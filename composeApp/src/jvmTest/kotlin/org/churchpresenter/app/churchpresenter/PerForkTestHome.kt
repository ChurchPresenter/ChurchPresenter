package org.churchpresenter.app.churchpresenter

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import java.io.File

/**
 * Gives every test fork its own `user.home` and its own port band, before any test class loads.
 *
 * `maxParallelForks` starts N JVMs from ONE Gradle task, and a Test task's system properties are
 * fixed per *task*, not per fork — so without this every fork would share `build/test-home`. That is
 * not survivable here. Roughly two dozen suites deliberately read and write concrete files inside
 * that shared home instead of swapping `user.home` to a temp dir, and several of them DELETE a
 * shared directory in `@BeforeTest`: [org.churchpresenter.app.churchpresenter.utils.TrainingDataLoggerTest]
 * and `BibleViewModelTrainingLogTest` both wipe `bible-stt-logs/`, `RecentColorsTest` deletes
 * `recent_colors.json`, `ScheduleTabTestSupport.plantAutoSave` plants `autosave_schedule.tmp` for
 * seven classes, and [CrashReportSweep] deletes crash reports by timestamp. Two forks doing any of
 * that at once is order-dependent failure, which this project does not allow. Skiko is the same
 * hazard one layer down: it unpacks its native library into `user.home` once per JVM, so N forks
 * would race one cache directory.
 *
 * `launcherSessionOpened` is the earliest hook the JUnit Platform offers — it runs before discovery,
 * so before a single test class is loaded and before any `by lazy` or object initialiser has had the
 * chance to latch a path. That timing is the entire reason the build runs on the platform launcher
 * rather than the bare JUnit 4 runner; the tests themselves are still JUnit 4, on junit-vintage.
 *
 * Registered by ServiceLoader — see
 * `src/jvmTest/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener`.
 *
 * @see TestPorts.testPort for the port half of the same problem.
 */
class PerForkTestHome : LauncherSessionListener {

    override fun launcherSessionOpened(session: LauncherSession) {
        // Absent when the suite runs outside Gradle (an IDE run). The inherited `user.home` is then
        // whatever the Test task set, exactly as it was before this class existed.
        val base = System.getProperty(HOME_BASE_PROPERTY) ?: return

        // Gradle sets this in every test JVM it forks, and with no `forkEvery` a fork lives for the
        // whole task -- so the N forks of one run get N consecutive ids. The counter does NOT reset
        // between builds, though: a warm daemon was handing out worker-9 after three runs. Left raw
        // that grows without limit, and offset * band would eventually push a port past 65535 (and
        // leave a new home directory behind every run). Folding it into SLOTS keeps both bounded,
        // and stays collision-free because the ids within a run are consecutive and there are never
        // more forks than slots -- maxParallelForks is capped at 4.
        val worker = System.getProperty(GRADLE_WORKER_PROPERTY)?.toIntOrNull() ?: 1
        val slot = (worker - 1).mod(SLOTS)

        val home = File(base, "worker-$slot")
        home.mkdirs()
        System.setProperty("user.home", home.absolutePath)

        System.setProperty(PORT_OFFSET_PROPERTY, (slot * PORT_BAND).toString())
    }

    companion object {
        /** Set by the Test task; the parent directory the per-fork homes are created under. */
        const val HOME_BASE_PROPERTY = "churchpresenter.test.homeBase"

        /** Read by [testPort]. Zero outside Gradle, so a single IDE run uses the base ports. */
        const val PORT_OFFSET_PROPERTY = "churchpresenter.test.portOffset"

        private const val GRADLE_WORKER_PROPERTY = "org.gradle.test.worker"

        /** Wide enough to clear the whole 39_5xx-39_8xx range the server suites bind. */
        private const val PORT_BAND = 1_000

        /** Comfortably above `maxParallelForks`, and low enough that slot * band stays a valid port. */
        private const val SLOTS = 8
    }
}
