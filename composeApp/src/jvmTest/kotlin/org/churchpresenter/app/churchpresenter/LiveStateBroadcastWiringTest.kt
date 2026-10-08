package org.churchpresenter.app.churchpresenter

import org.churchpresenter.liveoutput.setPropOn
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveoutput.showMessage
import org.churchpresenter.liveoutput.clearMessage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.CompanionServer
import org.churchpresenter.liveoutput.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class LiveStateBroadcastWiringTest {

    private fun compose(
        test: ComposeUiTest,
        presenterManager: PresenterManager,
        server: CompanionServer = CompanionServer(),
        settings: AppSettings = AppSettings(),
        screens: Int = 1,
        deckLinks: Int = 0,
    ) = test.setContent {
        LiveStateBroadcastWiring(
            appSettings = { settings },
            primaryBible = { null },
            presenterManager = presenterManager,
            companionServer = server,
            screenCountForUsage = screens,
            deckLinkCountForUsage = deckLinks,
        )
    }

    @Test
    fun `composing installs the live-state callback on the presenter`() = runComposeUiTest {
        val manager = PresenterManager()
        compose(this, manager)
        waitForIdle()
        assertNotNull(manager.onLiveStateChanged, "nothing would ever reach a follower without it")
    }

    @Test
    fun `the installed callback survives recomposition as a single registration`() = runComposeUiTest {
        val manager = PresenterManager()
        compose(this, manager)
        waitForIdle()
        val first = manager.onLiveStateChanged
        waitForIdle()
        assertEquals(first, manager.onLiveStateChanged)
    }

    @Test
    fun `going live drives the callback without throwing`() = runComposeUiTest {
        val manager = PresenterManager()
        compose(this, manager)
        waitForIdle()
        manager.setPresentingMode(Presenting.BIBLE)
        manager.onLiveStateChanged?.invoke(manager, Presenting.BIBLE)
    }

    @Test
    fun `the callback tolerates being driven with nothing live`() = runComposeUiTest {
        val manager = PresenterManager()
        compose(this, manager)
        waitForIdle()
        manager.setPresentingMode(Presenting.NONE)
        manager.onLiveStateChanged?.invoke(manager, Presenting.BIBLE)
    }

    @Test
    fun `an install with no audience output still handles a live change`() = runComposeUiTest {
        val manager = PresenterManager()
        compose(this, manager, screens = 0, deckLinks = 0)
        waitForIdle()
        manager.setPresentingMode(Presenting.LYRICS)
        manager.onLiveStateChanged?.invoke(manager, Presenting.BIBLE)
    }

    @Test
    fun `the broadcast says what is on air, the slide and the overlays over it`() = runComposeUiTest {
        val manager = PresenterManager()
        val server = CompanionServer()
        compose(this, manager, server)
        waitForIdle()
        manager.setPresentingMode(Presenting.LYRICS)
        manager.setPresentingMode(Presenting.LOWER_THIRD)
        manager.onLiveStateChanged?.invoke(manager, Presenting.LOWER_THIRD)
        val sent = assertNotNull(server.liveState.value)
        assertEquals(Presenting.LYRICS.name, sent.liveSlide)
        assertEquals(listOf(Presenting.LOWER_THIRD.name), sent.overlays)
    }

    @Test
    fun `the broadcast carries the message up, and none once it is down`() = runComposeUiTest {
        val manager = PresenterManager()
        val server = CompanionServer()
        compose(this, manager, server)
        waitForIdle()
        manager.showMessage(Cue.Message("Nursery #4", durationSeconds = 60))
        assertEquals("Nursery #4", server.liveState.value?.message)
        assertEquals(60, server.liveState.value?.messageDurationSeconds)
        manager.clearMessage()
        assertEquals(null, server.liveState.value?.message)
    }

    @Test
    fun `the broadcast carries the props up`() = runComposeUiTest {
        val manager = PresenterManager()
        val server = CompanionServer()
        compose(this, manager, server)
        waitForIdle()
        manager.setPropOn("logo", true)
        assertEquals(listOf("logo"), server.liveState.value?.props)
    }
}
