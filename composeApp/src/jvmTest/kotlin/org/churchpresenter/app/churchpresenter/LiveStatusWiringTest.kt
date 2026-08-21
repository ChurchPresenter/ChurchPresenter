package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.CompanionServer
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
        val server = CompanionServer()
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.PRESENTATION) }
        waitForIdle()
        assertTrue(server._presentationIsLive)
    }

    @Test
    fun `content that is not a presentation is not reported as one`() = runComposeUiTest {
        val server = CompanionServer()
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.BIBLE) }
        waitForIdle()
        assertFalse(server._presentationIsLive)
    }

    @Test
    fun `nothing live is not reported as a live presentation`() = runComposeUiTest {
        val server = CompanionServer()
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.NONE) }
        waitForIdle()
        assertFalse(server._presentationIsLive)
    }

    @Test
    fun `the configured browser source outputs are published`() = runComposeUiTest {
        val server = CompanionServer()
        val first = ScreenAssignment(browserSourceEnabled = true)
        val second = ScreenAssignment(browserSourceEnabled = false)
        setContent { LiveStatusWiring(settingsWithOutputs(first, second), server, Presenting.NONE) }
        waitForIdle()
        assertEquals(first, server.browserSourceOutput(0))
        assertEquals(second, server.browserSourceOutput(1))
    }

    @Test
    fun `no configured outputs means none are served`() = runComposeUiTest {
        val server = CompanionServer()
        setContent { LiveStatusWiring(AppSettings(), server, Presenting.NONE) }
        waitForIdle()
        assertNull(server.browserSourceOutput(0))
    }
}
