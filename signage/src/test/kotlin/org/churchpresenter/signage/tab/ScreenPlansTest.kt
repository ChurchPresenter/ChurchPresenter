package org.churchpresenter.signage.tab

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.Orientation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a screen is mounted reaches its manifest and outlives a wipe; a file playlist points at another file. */
class ScreenPlansTest {
    private val world = SignageWorld(folders = emptyList())
    private val controller = world.controller
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun publishedOrientation(wanted: Orientation) = withTimeout(SignageWorld.WAIT_MS) {
        while (hub.published("player-1")?.manifest?.screen?.orientation != wanted) yield()
    }

    @Test
    fun aScreenMountedOnItsSideIsSentSoAndAWipeKeepsIt() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        publishedOrientation(Orientation.LANDSCAPE)
        controller.plans.setOrientation("player-1", Orientation.PORTRAIT)
        publishedOrientation(Orientation.PORTRAIT)
        controller.wipe("player-1")
        withTimeout(SignageWorld.WAIT_MS) {
            controller.state.first { it.settings.plan("player-1").playlistId == ScreenPlan.NOTHING }
        }
        assertEquals(Orientation.PORTRAIT, controller.state.value.settings.plan("player-1").orientation)
        // The empty manifest a wipe sends is still turned.
        publishedOrientation(Orientation.PORTRAIT)
    }
}
