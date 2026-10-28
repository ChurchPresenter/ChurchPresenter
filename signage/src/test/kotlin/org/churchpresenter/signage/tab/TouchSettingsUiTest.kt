package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.CecState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Orientation
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The touchscreen's controls in the tab: a link made into a touch page with its reset time and sites,
 * a screen's corner gesture, its PIN, its TV and power in its settings, and Update on its card.
 */
@OptIn(ExperimentalTestApi::class)
class TouchSettingsUiTest {
    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    private val pages = listOf(
        PlaylistSummary("link:signup", "Event sign-up", kind = SourceKind.WEB),
        PlaylistSummary("link:checkin", "Check-in", kind = SourceKind.WEB),
    )

    @Test
    fun aLinkBecomesATouchPageWithItsResetTimeAndSites() = runComposeUiTest {
        val added = mutableListOf<NewLink>()
        show { LinkDialog(onAdd = { added += it }, onDismiss = {}) }
        field(LINK_ADDRESS_TAG).performTextInput("signup.example.org/retreat")
        onNodeWithText("People use it on the touchscreen").performClick()
        // A touch page neither moves smoothly nor reloads.
        onNodeWithText("Smooth motion").assertDoesNotExist()
        onNode(hasSetTextAction() and hasText("90")).performTextReplacement("120")
        onNodeWithText("It may open any site").performClick()
        field(LINK_SITES_TAG).performTextInput("pay.example.com, maps.example.org")
        onAllNodesWithText("Add").onLast().performClick()
        assertEquals(
            listOf(
                NewLink(
                    "signup.example.org", "https://www.signup.example.org/retreat", LinkKind.WEB,
                    interactive = InteractiveLink(120, listOf("pay.example.com", "maps.example.org")),
                ),
            ),
            added,
        )
    }

    @Test
    fun aTouchPageOpensWithItsSitesAndCanGoAnywhereAgain() = runComposeUiTest {
        val saved = mutableListOf<NewLink>()
        val page = LinkPlaylist(
            "link:s", "Sign-up", "https://signup.example.org", LinkKind.WEB,
            interactive = InteractiveLink(60, listOf("pay.example.com")),
        )
        show { LinkDialog(onAdd = { saved += it }, onDismiss = {}, initial = page) }
        field(LINK_SITES_TAG).assertExists()
        onNodeWithText("It may open any site").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(InteractiveLink(60, listOf(InteractiveLink.ANY_HOST)), saved.single().interactive)
    }

    private fun ComposeUiTest.settings(
        initial: ScreenSettingsInput,
        saved: MutableList<ScreenSettingsEdit>,
        asked: MutableList<CardQuestion> = mutableListOf(),
        tv: MutableList<Boolean> = mutableListOf(),
    ) = show {
        ScreenSettingsDialog(
            initial = initial,
            onSave = { saved += it },
            onAsk = { asked += it },
            onDismiss = {},
            onTv = { tv += it },
            deviceInfo = { Text("Address: 10.0.0.5") },
        )
    }

    @Test
    fun theCornerSwitchesToTheTouchPageChosenWithANewPin() = runComposeUiTest {
        val saved = mutableListOf<ScreenSettingsEdit>()
        settings(ScreenSettingsInput("Lobby", Orientation.LANDSCAPE, touchPlaylists = pages), saved)
        onNodeWithText("No PIN: holding the corner switches at once.", substring = true).assertExists()
        onNodeWithText("Event sign-up").performClick()
        onAllNodesWithText("Check-in").onLast().performClick()
        field(SCREEN_PIN_TAG).performTextInput("12a")
        onNodeWithText("Save").assertIsNotEnabled()
        field(SCREEN_PIN_TAG).performTextInput("34")
        onNodeWithText("Save").performClick()
        val edit = saved.single()
        assertEquals(PinChange.Set("1234"), edit.pin)
        assertEquals(true to "link:checkin", edit.gesture to edit.gesturePlaylistId)
    }

    @Test
    fun aPinIsRemovedAndTheCornerTurnedOff() = runComposeUiTest {
        val saved = mutableListOf<ScreenSettingsEdit>()
        settings(ScreenSettingsInput("Lobby", Orientation.LANDSCAPE, hasPin = true, touchPlaylists = pages), saved)
        onNodeWithText("Remove PIN").performClick()
        onNodeWithText("The PIN is removed when you save.", substring = true).assertExists()
        onNodeWithText("Holding the top-left corner switches to a touch page").performClick()
        // Off, there is nothing to switch to and no PIN to ask for.
        onNodeWithText("Event sign-up").assertDoesNotExist()
        field(SCREEN_PIN_TAG).assertDoesNotExist()
        onNodeWithText("Save").performClick()
        assertEquals(PinChange.Remove to false, saved.single().pin to saved.single().gesture)
    }

    @Test
    fun typingANewPinAfterRemoveSetsItInstead() = runComposeUiTest {
        val saved = mutableListOf<ScreenSettingsEdit>()
        settings(ScreenSettingsInput("Lobby", Orientation.LANDSCAPE, hasPin = true, touchPlaylists = pages), saved)
        onNodeWithText("Remove PIN").performClick()
        field(SCREEN_PIN_TAG).performTextInput("9876")
        onNodeWithText("A PIN is set", substring = true).assertExists()
        onNodeWithText("MOUNTED").performClick()
        waitForIdle()
        onNodeWithText("Portrait").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(PinChange.Set("9876") to Orientation.PORTRAIT, saved.single().pin to saved.single().orientation)
    }

    @Test
    fun withNoTouchPageItSaysSo() = runComposeUiTest {
        settings(ScreenSettingsInput("Lobby", Orientation.LANDSCAPE), mutableListOf())
        onNodeWithText("No playlist has a touch page yet.").assertExists()
    }

    @Test
    fun theTvRestartAndShutDownAreThereOnlyWhereTheyWork() = runComposeUiTest {
        val asked = mutableListOf<CardQuestion>()
        val tv = mutableListOf<Boolean>()
        settings(
            ScreenSettingsInput("Lobby", Orientation.LANDSCAPE, canPower = true, canTv = true),
            mutableListOf(), asked, tv,
        )
        onNodeWithText("Address: 10.0.0.5").assertExists()
        onNodeWithText("TV off").performClick()
        onNodeWithText("TV on").performClick()
        assertEquals(listOf(false, true), tv)
        onNodeWithText("Restart").performClick()
        assertEquals(listOf(CardQuestion.RESTART), asked)
    }

    @Test
    fun withoutCecOrPowerThoseControlsAreAbsentOrOff() = runComposeUiTest {
        settings(ScreenSettingsInput("Lobby", Orientation.LANDSCAPE), mutableListOf())
        onNodeWithText("TV on").assertDoesNotExist()
        onNodeWithText("Shut down").assertIsNotEnabled()
    }

    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_ARM64, "26.0.41", 1920, 1080, 0)
    private val folder = SignageFolderState(Path("/s"), pages, SignageSettings(), ready = true)

    @Test
    fun aScreenWithNoNewerPlayerStillUpdatesItsSystem() = runComposeUiTest {
        var updates = 0
        val link = ScreenLink("10.0.0.5", 0, "v", session = 1, features = listOf(Features.SYSTEM_UPDATE))
        var row by mutableStateOf(ScreenRowState(screen, link, folder))
        show { UpdateButton(row) { updates++ } }
        onNodeWithText("Update").performClick()
        assertEquals(1, updates)
        // While the system updates, the card says so and offers no second update.
        val progress = PlayerMessage.UpdateProgress("26.0.41", UpdateState.INSTALLING, "system")
        row = row.copy(link = link.copy(update = progress))
        waitForIdle()
        onNodeWithText("Update").assertDoesNotExist()
    }

    @Test
    fun theDeviceSaysWhereItIsAndWhatItKnows() = runComposeUiTest {
        var row by mutableStateOf(ScreenRowState(screen, null, folder))
        show { Column { DeviceDetails(row) } }
        onNodeWithText("Not connected").assertExists()
        onNodeWithText("Player 26.0.41", substring = true).assertExists()
        val status = PlayerMessage.Status(lastSyncMs = 1, freeDiskBytes = 3_000_000_000, cec = CecState.UNAVAILABLE)
        row = ScreenRowState(screen, ScreenLink("::ffff:10.0.0.5", 0, "v", session = 1, status = status), folder)
        waitForIdle()
        onNodeWithText("Address: 10.0.0.5").assertExists()
        onNodeWithText("3.0 GB free", substring = true).assertExists()
        onNodeWithText("No TV on/off").assertExists()
        row = row.copy(link = row.link!!.copy(status = status.copy(cec = CecState.UNKNOWN)))
        waitForIdle()
        onNodeWithText("No TV on/off").assertDoesNotExist()
    }

    @Test
    fun theCardSaysTheSystemIsUpdating() = runComposeUiTest {
        val progress = PlayerMessage.UpdateProgress("26.0.41", UpdateState.INSTALLING, "system")
        val link = ScreenLink("10.0.0.5", 0, "v", session = 1, update = progress)
        show { UpdateLine(ScreenRowState(screen, link, folder)) }
        onNodeWithText("Updating the system", substring = true).assertExists()
    }
}
