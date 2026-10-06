@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.AnnouncementsSettings
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.setPropOn
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.settings.ScreenAssignment
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a prop draws, on an output and as text. */
class PropsContentTest {

    private val badge = PropDefinition("live", "Live", PropKind.BADGE, PropCorner.BOTTOM_LEFT, text = "ON AIR")
    private val countdown = PropDefinition("count", "Start", PropKind.COUNTDOWN, countdownTo = "23:59")

    private fun settings(look: OutputLook = OutputLook()) = AppSettings(
        props = listOf(badge, countdown),
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(OutputProfile(id = "p", look = look)),
            screenAssignments = listOf(ScreenAssignment(activeProfileId = "p")),
        ),
    )

    @Test
    fun `an output draws the props that are up, and only those`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val pm = PresenterManager().apply { setPropOn("live", true) }
        setContent { MaterialTheme { LivePreviewPanel(presenterManager = pm, appSettings = settings()) } }
        mainClock.advanceTimeBy(100)
        onNodeWithText("ON AIR").assertExists()
        onNodeWithText("Start").assertDoesNotExist()
    }

    @Test
    fun `an output whose look leaves props out draws none`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val pm = PresenterManager().apply { setPropOn("live", true) }
        val noProps = settings(OutputLook(props = false))
        setContent { MaterialTheme { LivePreviewPanel(presenterManager = pm, appSettings = noProps) } }
        mainClock.advanceTimeBy(100)
        onNodeWithText("ON AIR").assertDoesNotExist()
    }

    @Test
    fun `a clock on air picks up an edited format`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val clock = PropDefinition("clock", "Clock", PropKind.CLOCK)
        var current by mutableStateOf(
            settings().copy(
                props = listOf(clock),
                announcementsSettings = AnnouncementsSettings(liveClockFormat = "'Alpha'"),
            ),
        )
        val pm = PresenterManager().apply { setPropOn("clock", true) }
        setContent { MaterialTheme { LivePreviewPanel(presenterManager = pm, appSettings = current) } }
        mainClock.advanceTimeBy(100)
        onNodeWithText("Alpha").assertExists()

        current = current.copy(announcementsSettings = AnnouncementsSettings(liveClockFormat = "'Beta'"))
        mainClock.advanceTimeBy(1_100)

        onNodeWithText("Beta").assertExists()
        onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun `a countdown shows what is left, then nothing below zero`() {
        val now = LocalTime.of(9, 30, 0)
        assertEquals("15:00", countdownText(now, "09:45"))
        assertEquals("1:05:30", countdownText(LocalTime.of(9, 0, 0), "10:05:30"))
        assertEquals("0:00", countdownText(now, "09:00"))
        assertEquals("0:00", countdownText(now, "soon"))
    }

    @Test
    fun `the clock follows its pattern, and a broken pattern falls back to hours and minutes`() {
        val now = LocalTime.of(14, 5, 9)
        assertEquals("14:05:09", clockText(now, "HH:mm:ss"))
        assertEquals("14:05", clockText(now, "HH:mm:ss'"))
    }
}
