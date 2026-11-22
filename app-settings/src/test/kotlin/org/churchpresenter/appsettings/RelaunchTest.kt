package org.churchpresenter.appsettings

import java.time.Duration
import java.time.Instant
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class RelaunchTest {
    private class Info(private val command: String?, private val arguments: List<String>?) : ProcessHandle.Info {
        override fun command(): Optional<String> = Optional.ofNullable(command)
        override fun commandLine(): Optional<String> = Optional.empty()
        override fun arguments(): Optional<Array<String>> = Optional.ofNullable(arguments?.toTypedArray())
        override fun startInstant(): Optional<Instant> = Optional.empty()
        override fun totalCpuDuration(): Optional<Duration> = Optional.empty()
        override fun user(): Optional<String> = Optional.empty()
    }

    @Test
    fun `a packaged app starts its own launcher and waits for this process`() {
        val info = Info("C:\\Program Files\\ChurchPresenter\\runtime\\bin\\java.exe", null)

        assertEquals(
            listOf("C:\\Program Files\\ChurchPresenter\\ChurchPresenter.exe", "${REPLACING_PID_ARGUMENT}42"),
            relaunchCommand(appPath = "C:\\Program Files\\ChurchPresenter\\ChurchPresenter.exe", info = info, pid = 42),
        )
    }

    @Test
    fun `an unpackaged app starts this JVM's command line again`() {
        val info = Info("/jdk/bin/java", listOf("-cp", "app.jar", "MainKt"))

        assertEquals(
            listOf("/jdk/bin/java", "-cp", "app.jar", "MainKt", "${REPLACING_PID_ARGUMENT}7"),
            relaunchCommand(appPath = null, info = info, pid = 7),
        )
    }

    @Test
    fun `a relaunched app relaunched again waits only for the newest`() {
        val info = Info("/jdk/bin/java", listOf("MainKt", "${REPLACING_PID_ARGUMENT}3"))

        assertEquals(
            listOf("/jdk/bin/java", "MainKt", "${REPLACING_PID_ARGUMENT}9"),
            relaunchCommand(appPath = null, info = info, pid = 9),
        )
    }
}
