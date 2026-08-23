@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.TestSingletons
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText

class ScheduleTabDefaultsTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-defaults").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the tab stands up on its own defaults with no view model handed to it`() = runComposeUiTest {
        setContent { MaterialTheme { ScheduleTab() } }

        waitForIdle()
        assertTrue(renderedText().isNotEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a tab built on its own defaults names itself and offers a new schedule`() = runComposeUiTest {
        setContent { MaterialTheme { ScheduleTab() } }

        waitForIdle()
        val shown = renderedText()
        assertTrue(shown.any { it.contains(ScheduleLabel.TITLE) }, shown.toString())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a tab built on its own defaults starts with an empty schedule`() = runComposeUiTest {
        setContent { MaterialTheme { ScheduleTab() } }

        waitForIdle()
        val shown = renderedText()
        assertTrue(shown.any { it.contains(ScheduleLabel.DROP_HINT) }, shown.toString())
    }
}
