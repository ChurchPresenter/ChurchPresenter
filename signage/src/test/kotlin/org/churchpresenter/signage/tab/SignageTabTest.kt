@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.ServerMessage
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.signage.hub.PairingDecision
import org.churchpresenter.signage.hub.PendingScreen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Signage tab, driven as an operator drives it: every control pressed, and its effect read off
 * the hub (what the screen is sent) or the tab itself.
 */
class SignageTabTest {
    private val world = SignageWorld()
    private val hub = world.hub

    @AfterTest
    fun cleanUp() = world.close()

    private fun connected() = runBlocking { world.startAndPairScreen() }

    private fun ComposeUiTest.waitFor(condition: () -> Boolean) =
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) { condition() }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) =
        waitFor { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun withNoScreensItSaysHowToAddOne() = signageTab(world) {
        onNodeWithText("No screens yet").assertExists()
    }

    @Test
    fun withTheServerOffItOffersToTurnItOn() {
        var turnedOn = false
        signageTab(world, serverRunning = false, onTurnOnServer = { turnedOn = true }) {
            onNodeWithText("Turn on server").performClick()
            assertTrue(turnedOn)
        }
    }

    @Test
    fun aWaitingScreenIsApprovedWithItsCode() = signageTab(world) {
        runBlocking { hub.registry.load() }
        val decision = world.scope.async { hub.pairing.await(hello(), "10.0.0.5") }
        waitForText("4821")
        onNodeWithText("Approve").performClick()
        waitFor { decision.isCompleted }
        assertIs<PairingDecision.Approved>(runBlocking { decision.await() })
    }

    @Test
    fun aWaitingScreenCanBeDeclined() = signageTab(world) {
        val decision = world.scope.async { hub.pairing.await(hello(), "10.0.0.5") }
        waitForText("Decline")
        onNodeWithText("Decline").performClick()
        waitFor { decision.isCompleted }
        assertEquals(PairingDecision.Rejected, runBlocking { decision.await() })
    }

    @Test
    fun aConnectedScreenShowsWhatItReports() = signageTab(world) {
        connected()
        hub.links.reported(
            "player-1",
            PlayerMessage.Status(
                lastSyncMs = 1,
                freeDiskBytes = 2_000_000_000,
                cec = CecState.WORKING,
                errors = listOf("Disk nearly full"),
            ),
        )
        waitForText("TV on/off works", substring = true)
        onNodeWithText("Disk nearly full").assertExists()
        onNodeWithText("Player 26.0.8", substring = true).assertExists()
        hub.links.reported("player-1", PlayerMessage.Status(cec = CecState.UNAVAILABLE))
        waitForText("No TV on/off", substring = true)
    }

    @Test
    fun anOfflineScreenCannotBeIdentifiedOrWiped() = signageTab(world) {
        connected()
        hub.links.closed("player-1", hub.links.links.value.getValue("player-1").session)
        waitForText("Offline", substring = true)
        onNodeWithContentDescription("Show its name on the TV").assertIsNotEnabled()
        onNodeWithContentDescription("Wipe content").assertIsNotEnabled()
    }

    @Test
    fun identifyingShowsTheNameOnTheTv() = signageTab(world) {
        connected()
        val identify = with(world) { world.scope.expect { it as? ServerMessage.Identify } }
        onNodeWithContentDescription("Show its name on the TV").assertIsEnabled().performClick()
        runBlocking { identify.await() }
    }

    @Test
    fun aScreenIsRenamed() = signageTab(world) {
        connected()
        waitForText("Lobby")
        onNodeWithContentDescription("Rename").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithContentDescription("Rename").performClick()
        onNode(hasSetTextAction() and hasText("Lobby")).performTextReplacement("Front door")
        onNodeWithText("Save").performClick()
        waitFor { hub.registry.screen("player-1")?.name == "Front door" }
    }

    @Test
    fun wipingAsksThenEmptiesTheScreen() = signageTab(world) {
        connected()
        val wiped = with(world) { world.scope.expect { if (it == ServerMessage.Wipe) it else null } }
        onNodeWithContentDescription("Wipe content").performClick()
        onNodeWithText("Wipe Lobby?").assertExists()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Wipe Lobby?").assertDoesNotExist()
        onNodeWithContentDescription("Wipe content").performClick()
        onNodeWithText("Wipe").performClick()
        runBlocking { wiped.await() }
        assertEquals(ScreenPlan.NOTHING, world.controller.state.value.settings.plan("player-1").playlistId)
    }

    @Test
    fun aFactoryResetReleasesTheScreenAndTakesItOffTheList() = signageTab(world) {
        connected()
        val released = with(world) { world.scope.expect { it as? ServerMessage.Released } }
        onNodeWithContentDescription("Factory reset").performClick()
        onNodeWithText("Factory reset Lobby?").assertExists()
        // The dialog's confirm button: the card's own button is an icon, named but not labelled.
        onNodeWithText("Factory reset").performClick()
        runBlocking { released.await() }
        waitForText("No screens yet")
        assertNull(hub.registry.screen("player-1"))
    }

    @Test
    fun theScreensPictureShowsAndOpensLarger() = signageTab(world) {
        connected()
        onNodeWithText("No picture right now").assertExists()
        hub.links.reported("player-1", PlayerMessage.Preview(jpegBase64(), 480, 270, 1))
        waitFor { onAllNodesWithText("No picture right now").fetchSemanticsNodes().isEmpty() }
        val sharper = with(world) {
            world.scope.expect { (it as? ServerMessage.PreviewRequest)?.takeIf { request -> request.maxWidth > 480 } }
        }
        onNodeWithContentDescription("What the TV shows. Click to see it larger.").performClick()
        onNodeWithText("What Lobby shows").assertExists()
        runBlocking { sharper.await() }
        onNodeWithText("Close").performClick()
        onNodeWithText("What Lobby shows").assertDoesNotExist()
    }

    @Test
    fun aPictureThatStoppedComingIsNotShown() = signageTab(world) {
        connected()
        world.now = System.currentTimeMillis() - STALE_MS
        hub.links.reported("player-1", PlayerMessage.Preview(jpegBase64(), 480, 270, 1))
        waitForIdle()
        onNodeWithText("No picture right now").assertExists()
    }

    @Test
    fun aNewerPlayerIsOfferedAndSent() = signageTab(world) {
        connected()
        waitForText("Update to 26.0.9")
        val offer = with(world) { world.scope.expect { it as? ServerMessage.UpdateAvailable } }
        onNodeWithText("Update to 26.0.9").performClick()
        assertEquals("26.0.9", runBlocking { offer.await() }.agentVersion)
    }

    @Test
    fun anUpdateShowsEachStepAndWhatWentWrong() = signageTab(world) {
        connected()
        hub.links.reported("player-1", PlayerMessage.UpdateProgress("26.0.9", UpdateState.DOWNLOADING, "40%"))
        waitForText("Updating to 26.0.9: downloading 40%")
        val steps = listOf(
            UpdateState.CHECKING to "checking",
            UpdateState.INSTALLING to "installing",
            UpdateState.RESTARTING to "restarting",
        )
        for ((state, word) in steps) {
            hub.links.reported("player-1", PlayerMessage.UpdateProgress("26.0.9", state))
            waitForText("Updating to 26.0.9: $word")
        }
        hub.links.reported("player-1", PlayerMessage.UpdateProgress("26.0.9", UpdateState.FAILED, "no space"))
        waitForText("Update to 26.0.9 failed: no space")
        hub.links.reported("player-1", PlayerMessage.UpdateProgress("26.0.9", UpdateState.ROLLED_BACK, "no start"))
        waitForText("didn't start, so the screen went back", substring = true)
        waitForText("Update to 26.0.9")
    }

    @Test
    fun aScreenPlaysAPlaylistOrNothing() {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        signageTab(world) {
            connected()
            waitForText("Events")
            fun pick(option: String) {
                onAllNodesWithText("PLAYS").onFirst().performClick()
                waitForIdle()
                val options = onAllNodesWithText(option)
                options[options.fetchSemanticsNodes().size - 1].performClick()
                waitForIdle()
            }
            pick("Nothing (idle card)")
            waitFor { world.controller.state.value.settings.plan("player-1").playlistId == ScreenPlan.NOTHING }
            pick("Events")
            waitFor { world.controller.state.value.settings.plan("player-1").playlistId == "folder:Events" }
        }
    }

    @Test
    fun theCardSaysWhichPlaylistIsShowing() {
        world.file("Events/flyer.png")
        signageTab(world) {
            connected()
            waitForText("Showing the idle card", substring = true)
            val showing = PlayerMessage.Status(showingPlaylistId = "folder:Events", cec = CecState.UNKNOWN)
            hub.links.reported("player-1", showing)
            waitForText("Showing Events", substring = true)
            onNodeWithText("TV on/off", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun eachKindOfPlayerIsNamed() = signageTab(world) {
        runBlocking {
            hub.registry.load()
            hub.registry.pair(PendingScreen("pi", "1", Platform.LINUX_ARM64, "26.0.8", 3840, 2160, "a"), "Hall")
            hub.registry.pair(PendingScreen("win", "2", Platform.WINDOWS_X64, "26.0.8", 1920, 1080, "b"), "Office")
        }
        waitForText("Linux (ARM)", substring = true)
        onNodeWithText("Windows PC", substring = true).assertExists()
        // Neither is connected, so neither offers to wipe, preview or update.
        onAllNodesWithText("Offline", substring = true).fetchSemanticsNodes().size.let { assertEquals(2, it) }
        onNodeWithText("No picture right now").assertDoesNotExist()
    }

    @Test
    fun withAnEmptyFolderAScreenCanStillBeSetToNothing() = signageTab(world) {
        connected()
        waitForText("Lobby")
        onNodeWithText("PLAYS").assertDoesNotExist()
        world.controller.play("player-1", ScreenPlan.NOTHING)
        waitForText("PLAYS")
        onNodeWithText("Nothing (idle card)").assertExists()
    }

    @Test
    fun theTabFollowsAnotherController() {
        val other = SignageWorld()
        try {
            runBlocking {
                other.hub.registry.load()
                val chapel = PendingScreen("x", "1", Platform.LINUX_X64, "26.0.8", 1920, 1080, "a")
                other.hub.registry.pair(chapel, "Chapel")
            }
            runComposeUiTest {
                var controller by mutableStateOf(world.controller)
                val desktop = FakeDesktop()
                setContent {
                    ChurchPresenterTheme(ThemeMode.LIGHT) {
                        SignageTab(controller, true, {}, Modifier.fillMaxSize(), desktop.desktop)
                    }
                }
                onNodeWithText("No screens yet").assertExists()
                controller = other.controller
                waitUntil(timeoutMillis = SignageWorld.WAIT_MS) {
                    onAllNodesWithText("Chapel").fetchSemanticsNodes().isNotEmpty()
                }
            }
        } finally {
            other.close()
        }
    }

    private fun hello() = PlayerMessage.Hello(
        "player-2", "26.0.8", 1, 1, null, 1920, 1080, Platform.LINUX_ARM64, "4821",
    )

    private companion object {
        const val STALE_MS = 60_000L
    }
}
