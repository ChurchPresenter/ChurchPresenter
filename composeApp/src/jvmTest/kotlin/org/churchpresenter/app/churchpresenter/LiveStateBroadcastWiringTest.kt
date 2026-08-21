package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.CompanionServer
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
}
