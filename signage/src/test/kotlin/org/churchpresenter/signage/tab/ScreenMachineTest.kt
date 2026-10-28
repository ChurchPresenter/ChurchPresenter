package org.churchpresenter.signage.tab

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.KioskPin
import org.churchpresenter.signage.core.PowerAction
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.ScreenSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tab's controls for the machine and the touchscreen: restart, shut down and the TV go only to a
 * player that can, Update updates the machine alone when there is no newer player, and the PIN (only
 * its hash) and the corner gesture reach the screen's manifest.
 */
class ScreenMachineTest {
    private val world = SignageWorld()
    private val controller = world.controller
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private suspend fun published(until: (ScreenSettings) -> Boolean): ScreenSettings =
        withTimeout(SignageWorld.WAIT_MS) {
            var screen: ScreenSettings? = null
            while (screen == null) {
                screen = hub.published("player-1")?.manifest?.screen?.takeIf(until)
                if (screen == null) yield()
            }
            screen
        }

    @Test
    fun restartShutDownAndTheTvGoOnlyToAPlayerThatCan() = runBlocking {
        world.pairScreen(features = listOf(Features.POWER, Features.TV))
        val restart = with(world) { expect { it as? ServerMessage.Power } }
        assertTrue(controller.power.run("player-1", PowerAction.RESTART))
        assertEquals(PowerAction.RESTART, restart.await().action)
        val tv = with(world) { expect { it as? ServerMessage.Tv } }
        assertTrue(controller.power.tv("player-1", on = false))
        assertFalse(tv.await().on)
        world.pairScreen(id = "player-2", features = listOf(Features.WIPE))
        assertFalse(controller.power.run("player-2", PowerAction.SHUT_DOWN))
        assertFalse(controller.power.tv("player-2", on = true))
        assertFalse(controller.power.run("away", PowerAction.RESTART))
        assertFalse(controller.power.tv("away", on = true))
    }

    @Test
    fun updateWithNoNewerPlayerUpdatesTheMachineAlone() = runBlocking {
        world.pairScreen(version = "99.0.0", features = listOf(Features.SYSTEM_UPDATE))
        val update = with(world) { expect { it as? ServerMessage.UpdateSystem } }
        controller.controls.update("player-1")
        update.await()
    }

    @Test
    fun thePinIsSentOnlyAsItsHashAndTheGestureWithIt() = runBlocking {
        world.file("welcome.jpg")
        world.startAndPairScreen()
        controller.plans.setKioskPin("player-1", "2468")
        val withPin = published { it.kioskPinHash != null }
        assertTrue(KioskPin.matches("2468", withPin.kioskPinHash!!))
        controller.plans.setGesture("player-1", on = false, playlistId = "link:s")
        val gesture = published { !it.kioskGesture }
        assertEquals("link:s", gesture.kioskGesturePlaylistId)
        controller.plans.setKioskPin("player-1", null)
        assertNull(published { it.kioskPinHash == null }.kioskPinHash)
        // A PIN that is not 4 to 8 digits leaves the screen with none.
        controller.plans.setKioskPin("player-1", "1357")
        published { it.kioskPinHash != null }
        controller.plans.setKioskPin("player-1", "12")
        assertNull(published { it.kioskPinHash == null }.kioskPinHash)
    }
}
