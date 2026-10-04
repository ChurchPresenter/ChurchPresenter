@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.schedule

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.testing.renderedText

class ScheduleTabDefaultsTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a tab on its own view model reports every change to the host`() = runComposeUiTest {
        var actions: ScheduleTabActions? = null
        val changes = mutableListOf<List<ScheduleItem>>()
        setContent {
            MaterialTheme {
                ScheduleTab(onActionsReady = { actions = it }, onScheduleChanged = { changes += it })
            }
        }
        waitForIdle()

        actions!!.addSong(42, "Amazing Grace", "Hymnal", "")
        waitForIdle()

        assertEquals(listOf("Amazing Grace"), changes.last().map { (it as ScheduleItem.SongItem).title })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `with no host callbacks a row can still be clicked, zoomed and sent live`() = runComposeUiTest {
        var actions: ScheduleTabActions? = null
        setContent { MaterialTheme { ScheduleTab(onActionsReady = { actions = it }) } }
        waitForIdle()
        actions!!.addSong(42, "Amazing Grace", "Hymnal", "")
        actions!!.addLabel("Welcome", "#FFFFFF", "#000000")
        waitForIdle()

        onNodeWithText("Amazing Grace", substring = true).performClick()
        button(ScheduleLabel.GO_LIVE).performClick()
        button(ScheduleLabel.EDIT_LABEL).performClick()
        button(ScheduleLabel.ZOOM_IN).performClick()
        waitForIdle()

        assertTrue(renderedText().any { it.contains("Amazing Grace") })
    }
}
