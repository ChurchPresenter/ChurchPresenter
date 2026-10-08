@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.AnnouncementsSettings
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveoutput.preview.LivePreviewPanel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.setPropOn
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test

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
}
