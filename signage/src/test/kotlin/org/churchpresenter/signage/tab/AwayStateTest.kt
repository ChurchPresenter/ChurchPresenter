@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a card says about a screen that is not connected, and the picture box that stays in its place. */
class AwayStateTest {
    private val now = 10_000_000_000L
    private val folder = SignageFolderState(ready = true)

    private fun screen(seenAgoMs: Long?, address: String? = "192.168.2.221") = PairedScreen(
        "p", "Lobby", "h", Platform.LINUX_X64, "26.0.56", 1920, 1080, 0,
        lastAddress = address, lastSeenAtMs = seenAgoMs?.let { now - it },
    )

    private fun row(screen: PairedScreen, lookingAgoMs: Long, link: ScreenLink? = null) =
        ScreenRowState(screen, link, folder, lookingSinceMs = now - lookingAgoMs)

    private val link = ScreenLink("192.168.2.221", 0, "v", session = 1)

    @Test
    fun `a screen is looked for a while, then waited on`() {
        assertNull(awayState(row(screen(null), 0, link), now), "connected")
        assertEquals(AwayState.LOOKING, awayState(row(screen(null), 30_000), now))
        assertEquals(AwayState.WAITING, awayState(row(screen(null), LOOKING_FOR_MS), now))
        assertEquals(AwayState.LOOKING, awayState(row(screen(60_000), LOOKING_FOR_MS * 3), now), "it only just left")
        assertEquals(AwayState.WAITING, awayState(row(screen(LOOKING_FOR_MS * 3), LOOKING_FOR_MS * 3), now))
    }

    private fun lines(vararg rows: Pair<ScreenRowState, AwayState>) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Column {
                        rows.forEach { (row, state) -> Text(awayLine(row, state, now)) }
                    }
                }
            }
        }
        onNodeWithText("Looking for it…").assertExists()
        onNodeWithText("Offline · keeps playing what it has · last seen just now at 192.168.2.221").assertExists()
        onNodeWithText("Offline · keeps playing what it has · last seen 5 min ago at 192.168.2.221").assertExists()
        onNodeWithText("Offline · keeps playing what it has · last seen 3 h ago").assertExists()
        onNodeWithText("Offline · keeps playing what it has · last seen 2 days ago at 192.168.2.221").assertExists()
        onNodeWithText("Offline · keeps playing what it has").assertExists()
    }

    @Test
    fun `the line says how long ago and where, as far as it is known`() = lines(
        row(screen(null), 0) to AwayState.LOOKING,
        row(screen(10_000), 0) to AwayState.WAITING,
        row(screen(5 * 60_000L), 0) to AwayState.WAITING,
        row(screen(3 * 3_600_000L, address = null), 0) to AwayState.WAITING,
        row(screen(2 * 86_400_000L), 0) to AwayState.WAITING,
        row(screen(null), 0) to AwayState.WAITING,
    )

    private fun card(row: ScreenRowState, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ScreenCard(row, ScreenActions({ _, _ -> }, {}, { _, _ -> }, {}))
                }
            }
        }
        block()
    }

    @Test
    fun `an offline card keeps its picture box and, after a while, says what to check`() {
        val fresh = System.currentTimeMillis()
        card(ScreenRowState(screen(null), null, folder, lookingSinceMs = fresh)) {
            onNodeWithText("Not connected").assertExists()
            onNodeWithText("Looking for it…").assertExists()
            onNodeWithText("Check the screen is on and on the same network as this computer.").assertDoesNotExist()
        }
        card(ScreenRowState(screen(null), null, folder, lookingSinceMs = fresh - LOOKING_FOR_MS * 2)) {
            onNodeWithText("Check the screen is on and on the same network as this computer.").assertExists()
        }
    }

    @Test
    fun `a connected player without previews says so in the box`() {
        val noPreview = link.copy(features = listOf(Features.WIPE))
        card(ScreenRowState(screen(null), noPreview, folder)) {
            onNodeWithText("No preview on this player").assertExists()
        }
        card(ScreenRowState(screen(null), link.copy(features = listOf(Features.PREVIEW)), folder)) {
            onNodeWithText("No picture right now").assertExists()
        }
    }
}
