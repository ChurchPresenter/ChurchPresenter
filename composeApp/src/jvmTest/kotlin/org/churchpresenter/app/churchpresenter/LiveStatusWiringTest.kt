package org.churchpresenter.app.churchpresenter

import org.churchpresenter.server.browserSourceOutput
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.offersTranspose
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.CompanionServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LiveStatusWiringTest {

    private fun settingsWithOutputs(vararg outputs: ScreenAssignment) = AppSettings().let {
        it.copy(projectionSettings = it.projectionSettings.copy(browserSourceOutputs = outputs.toList()))
    }

    @Test
    fun `a live presentation is reported to connected companions`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.PRESENTATION) }
        waitForIdle()
        assertTrue(server.presentationIsLive)
    }

    @Test
    fun `content that is not a presentation is not reported as one`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.BIBLE) }
        waitForIdle()
        assertFalse(server.presentationIsLive)
    }

    @Test
    fun `nothing live is not reported as a live presentation`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.NONE) }
        waitForIdle()
        assertFalse(server.presentationIsLive)
    }

    @Test
    fun `the configured browser source outputs are published`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        val first = ScreenAssignment(browserSourceEnabled = true)
        val second = ScreenAssignment(browserSourceEnabled = false)
        setContent { LiveStatusWiring(settingsWithOutputs(first, second), server, Presenting.NONE) }
        waitForIdle()
        assertEquals(first, server.browserSourceOutput(0))
        assertEquals(second, server.browserSourceOutput(1))
    }

    @Test
    fun `no configured outputs means none are served`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.NONE) }
        waitForIdle()
        assertNull(server.browserSourceOutput(0))
    }

    // ── The musicians' transpose buttons (issue #649) ─────────────────────────

    private val stage = OutputProfile(
        id = "stage",
        displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
        showChords = true,
    )
    private val musicians = stage.copy(id = "musicians", showTransposeControls = true)

    private fun projection(vararg profileIds: String) = ProjectionSettings(
        outputProfiles = listOf(stage, musicians),
        browserSourceOutputs = profileIds.map { ScreenAssignment(browserSourceEnabled = true, activeProfileId = it) },
    )

    @Test
    fun `only a stage monitor drawing chords, with the switch on, offers the buttons`() {
        assertTrue(musicians.offersTranspose())
        assertFalse(stage.offersTranspose(), "the switch is off on an ordinary stage profile")
        assertFalse(musicians.copy(showChords = false).offersTranspose(), "no chords, nothing to move")
        assertFalse(
            musicians.copy(displayMode = Constants.DISPLAY_MODE_FULLSCREEN).offersTranspose(),
            "a full-screen output draws no chord chart",
        )
    }

    @Test
    fun `the outputs offering the buttons are the ones on a profile that turns them on`() {
        // Browser Source 1 is the OBS feed on the ordinary profile; 2 is the tablets'.
        assertEquals(setOf(1), transposeControlOutputs(projection("stage", "musicians")))
        assertEquals(emptySet(), transposeControlOutputs(projection("stage", "missing-profile")))
    }

    @Test
    fun `which outputs offer the buttons is published to the server`() = runComposeUiTest {
        val server = CompanionServer(shutdownGraceMs = 0)
        val settings = AppSettings().copy(projectionSettings = projection("musicians", "stage"))
        setContent { LiveStatusWiring(settings, server, Presenting.NONE) }
        waitForIdle()
        assertTrue(server.offersTranspose(0))
        assertFalse(server.offersTranspose(1))
    }
}
