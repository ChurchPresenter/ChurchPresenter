@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The Signage tab's states, in both themes: before any screen, with everything it can show at once
 * (a player waiting, a connected screen with its picture and an update on offer, playlists), and
 * with the companion server off.
 */
@OptIn(ExperimentalPathApi::class)
class SignageTabScreenshotTest {
    // The tab shows the signage folder's path, so it is pinned rather than a fresh temp folder.
    private val world = SignageWorld(Path("/tmp/churchpresenter-signage-screenshot").also { it.deleteRecursively() })

    @AfterTest
    fun cleanUp() = world.close()

    private fun shoot(name: String, serverRunning: Boolean = true, vararg waitFor: String) =
        stackedThemes(SECTION, name) { mode, file ->
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                setContent {
                    PinnedFonts {
                        ChurchPresenterTheme(mode) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                SignageTab(
                                    world.controller, serverRunning, {},
                                    modifier = Modifier.fillMaxSize(),
                                    desktop = FakeDesktop().desktop,
                                )
                            }
                        }
                    }
                }
                waitForTexts(*waitFor)
                captureTo(file)
            }
        }

    private fun ComposeUiTest.waitForTexts(vararg texts: String) {
        waitUntil(timeoutMillis = SignageWorld.WAIT_MS) {
            texts.all { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
        waitForIdle()
    }

    @Test
    fun `no screens yet`() {
        world.controller.start()
        shoot("empty", waitFor = arrayOf("No pictures or videos here yet"))
    }

    @Test
    fun `a waiting player, a connected screen with an update, and playlists`() {
        world.file("welcome.jpg")
        world.file("Events/flyer.png")
        runBlocking { world.startAndPairScreen() }
        world.scope.async {
            world.hub.pairing.await(
                PlayerMessage.Hello("waiting", "26.0.8", 1, 1, null, 1920, 1080, Platform.LINUX_ARM64, "4821"),
                "10.0.0.7",
            )
        }
        world.hub.links.reported("player-1", PlayerMessage.Preview(jpegBase64(), 480, 270, 1))
        shoot("everything", waitFor = arrayOf("4821", "Events", "Update to 26.0.9"))
    }

    @Test
    fun `the companion server is off`() {
        world.controller.start()
        shoot("server_off", serverRunning = false)
    }

    private companion object {
        const val SECTION = "signage"
        const val WIDTH = 1100
        const val HEIGHT = 760
    }
}
