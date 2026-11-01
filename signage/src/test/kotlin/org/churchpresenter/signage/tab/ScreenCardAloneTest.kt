@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onLast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.signage.core.CecState
import org.churchpresenter.signage.core.Features
import org.churchpresenter.signage.core.Platform
import org.churchpresenter.signage.core.PlayerMessage
import org.churchpresenter.signage.core.UpdateState
import org.churchpresenter.signage.hub.PairedScreen
import org.churchpresenter.signage.hub.PendingScreen
import org.churchpresenter.signage.hub.ScreenLink
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * One screen card on its own, its row and its actions swapped between frames as the tab swaps them,
 * and every control pressed: what each hands back is recorded rather than reaching a hub.
 */
class ScreenCardAloneTest {
    private val calls = mutableListOf<String>()

    private fun actions(tag: String = "") = ScreenActions(
        onPlay = { id, playlist -> calls += "$tag play $id $playlist" },
        onIdentify = { calls += "$tag identify $it" },
        onRename = { id, name -> calls += "$tag rename $id $name" },
        onRemove = { calls += "$tag reset $it" },
        onWipe = { calls += "$tag wipe $it" },
        onUpdate = { calls += "$tag update $it" },
        onPreviewOpen = { calls += "$tag preview $it" },
        onOrientation = { id, orientation -> calls += "$tag mounted $id $orientation" },
    )

    private val screen = PairedScreen("p", "Lobby", "h", Platform.LINUX_X64, "26.0.8", 1920, 1080, 0)
    private val folder = SignageFolderState(
        playlists = listOf(
            PlaylistSummary("folder:", "Signage", 2, 1),
            PlaylistSummary("folder:Events", "Events", 1, 0),
        ),
        ready = true,
    )
    private val link = ScreenLink(
        address = "10.0.0.5",
        connectedAtMs = 0,
        manifestVersion = "v",
        session = 1,
        features = listOf(Features.WIPE, Features.PREVIEW),
        status = PlayerMessage.Status(showingPlaylistId = "folder:Events", cec = CecState.WORKING),
        preview = PlayerMessage.Preview(jpegBase64(), 480, 270, 1),
        previewAtMs = System.currentTimeMillis(),
    )

    private fun row(link: ScreenLink?) = ScreenRowState(screen, link, folder)

    private fun updating(version: String, state: UpdateState, message: String? = null) =
        row(link.copy(update = PlayerMessage.UpdateProgress(version, state, message)))

    private fun card(
        first: ScreenRowState,
        block: ComposeUiTest.(swap: (ScreenRowState, ScreenActions) -> Unit) -> Unit,
    ) =
        runComposeUiTest {
            var row by mutableStateOf(first)
            var current by mutableStateOf(actions())
            setContent {
                ChurchPresenterTheme(ThemeMode.DARK) {
                    Surface(color = MaterialTheme.colorScheme.background) { ScreenCard(row, current) }
                }
            }
            block { nextRow, nextActions ->
                row = nextRow
                current = nextActions
                waitForIdle()
            }
        }

    @Test
    fun everyControlHandsBackItsScreen() = card(ScreenRowState(screen, link, folder)) { swap ->
        onNodeWithContentDescription("Show its name on the screen").performClick()
        onNodeWithContentDescription("What the TV shows. Click to see it larger.").performClick()
        onNodeWithText("Close").performClick()
        // The same screen with a new report, then new actions: the card follows both.
        swap(row(link.copy(status = PlayerMessage.Status(cec = CecState.UNAVAILABLE))), actions())
        swap(ScreenRowState(screen, link, folder), actions("second"))
        onNodeWithContentDescription("Show its name on the screen").performClick()
        onNodeWithContentDescription("Wipe content").performClick()
        onNodeWithText("Wipe").performClick()
        // Factory reset is in Screen settings, and still asks.
        onNodeWithContentDescription("Screen settings").performClick()
        onNodeWithText("Factory reset").performClick()
        onNodeWithText("Factory reset").performClick()
        onNodeWithContentDescription("Screen settings").performClick()
        onNode(hasSetTextAction() and hasText("Lobby")).performTextReplacement("Hall")
        onNodeWithText("MOUNTED").performClick()
        waitForIdle()
        onNodeWithText("Portrait").performClick()
        onNodeWithText("Save").performClick()
        onAllNodesWithText("PLAYS")[0].performClick()
        waitForIdle()
        onAllNodesWithText("Signage").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        waitForIdle()
        assertEquals(
            listOf(
                " identify p", " preview p", " preview null",
                "second identify p", "second wipe p", "second reset p",
                "second rename p Hall", "second mounted p PORTRAIT", "second play p folder:",
            ),
            calls,
        )
    }

    @Test
    fun theMachinesUpdateShowsOnlyWhenSomethingIsWaiting() {
        fun waiting(count: Int?) = row(
            link.copy(
                features = link.features + Features.SYSTEM_UPDATE,
                status = link.status?.copy(systemUpdates = count),
            ),
        )
        card(waiting(12)) { swap ->
            onNodeWithText("Update (12)").performClick()
            swap(waiting(0), actions())
            onNodeWithText("Update").assertDoesNotExist()
            onNodeWithText("Update (0)").assertDoesNotExist()
            // A player that cannot count keeps the button it always had.
            swap(waiting(null), actions())
            onNodeWithText("Update").performClick()
        }
        assertEquals(listOf(" update p", " update p"), calls)
    }

    @Test
    fun everyTouchscreenAndDeviceControlHandsBackItsScreen() {
        val touch = LinkPlaylist(
            "link:t", "Sign-up", "https://signup.example.org", LinkKind.WEB, interactive = InteractiveLink(),
        )
        val withTouch = folder.copy(
            playlists = folder.playlists + PlaylistSummary("link:t", "Sign-up", kind = SourceKind.WEB),
            settings = SignageSettings(added = listOf(touch)),
        )
        val able = link.copy(
            features = listOf(Features.POWER, Features.TV, Features.WEB_TEST, Features.SYSTEM_UPDATE),
            status = PlayerMessage.Status(cec = CecState.WORKING),
        )
        val withPin = withTouch.copy(
            settings = withTouch.settings.copy(screens = mapOf("p" to ScreenPlan(kioskPinHash = "pbkdf2:1:00:00"))),
        )
        val all = actions().let {
            ScreenActions(
                onPlay = it.onPlay, onIdentify = it.onIdentify, onRename = it.onRename, onRemove = it.onRemove,
                onUpdate = it.onUpdate,
                onTestWebPages = { id -> calls += "test $id" },
                onSchedule = { id, blocks -> calls += "schedule $id ${blocks.size}" },
                onKioskPin = { id, pin -> calls += "pin $id $pin" },
                onGesture = { id, on, playlist -> calls += "gesture $id $on $playlist" },
                onPower = { id, action -> calls += "power $id $action" },
                onTv = { id, on -> calls += "tv $id $on" },
            )
        }
        card(ScreenRowState(screen, able, withTouch)) { swap ->
            swap(ScreenRowState(screen, able, withTouch), all)
            onNodeWithContentDescription("Run test").performClick()
            onNodeWithContentDescription("Schedule").performClick()
            onNodeWithText("Save").performClick()
            onNodeWithText("Update").performClick()
            // The TV acts at once; Restart and Shut down ask on the card first.
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("TV off").performClick()
            onNodeWithText("TV on").performClick()
            onNodeWithText("Restart").performClick()
            onNodeWithText("Restart Lobby?").assertExists()
            onAllNodesWithText("Restart").onLast().performClick()
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("Shut down").performClick()
            onAllNodesWithText("Shut down").onLast().performClick()
            // The touchscreen: a PIN, the touch page chosen, then the corner off.
            onNodeWithContentDescription("Screen settings").performClick()
            field(SCREEN_PIN_TAG).performTextInput("2468")
            onNodeWithText("Save").performClick()
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("Holding the top-left corner switches to a touch page").performClick()
            onNodeWithText("Save").performClick()
            // With a PIN set, it can be removed; nothing else changed saves nothing else.
            swap(ScreenRowState(screen, able, withPin), all)
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("Remove PIN").performClick()
            onNodeWithText("Save").performClick()
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("Save").performClick()
            waitForIdle()
        }
        assertEquals(
            listOf(
                "test p", "schedule p 0", " update p", "tv p false", "tv p true",
                "power p RESTART", "power p SHUT_DOWN", "pin p 2468", "gesture p false null", "pin p null",
            ),
            calls,
        )
    }

    @Test
    fun theTvIsOfferedOnlyWhereItAnswersAndPowerOnlyWhileConnected() {
        val silentTv =
            link.copy(features = listOf(Features.TV), status = PlayerMessage.Status(cec = CecState.UNAVAILABLE))
        card(row(silentTv)) { swap ->
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("TV on").assertDoesNotExist()
            onNodeWithText("Restart").assertIsNotEnabled()
            onNodeWithText("Cancel").performClick()
            swap(row(null), actions())
            onNodeWithContentDescription("Screen settings").performClick()
            onNodeWithText("Not connected").assertExists()
            onNodeWithText("TV on").assertDoesNotExist()
        }
    }

    @Test
    fun anUpdateLineFollowsEachReport() = card(ScreenRowState(screen, link, folder)) { swap ->
        val same = actions()
        swap(updating("26.0.9", UpdateState.DOWNLOADING), same)
        onNodeWithText("Updating to 26.0.9: downloading").assertExists()
        swap(updating("26.0.9", UpdateState.CHECKING, " "), same)
        onNodeWithText("Updating to 26.0.9: checking").assertExists()
        swap(updating("26.0.9", UpdateState.FAILED), same)
        onNodeWithText("Update to 26.0.9 failed: ").assertExists()
        swap(updating("26.0.9", UpdateState.ROLLED_BACK, " "), same)
        onNodeWithText("didn't start", substring = true).assertExists()
        swap(ScreenRowState(screen, null, folder.copy(playlists = emptyList())), same)
        onNodeWithText("Offline", substring = true).assertExists()
    }

    @Test
    fun anOpenDialogStaysOpenAsReportsArriveAndStillActsOnItsScreen() = card(row(link)) { swap ->
        val busier = ScreenRowState(screen, link.copy(status = PlayerMessage.Status(cec = CecState.UNKNOWN)), folder)
        val reportsArrive = {
            swap(busier, actions("same"))
            swap(ScreenRowState(screen, link, folder), actions("newer"))
        }
        for ((open, confirm) in listOf(
            "Wipe content" to "Wipe",
            "What the TV shows. Click to see it larger." to "Close",
        )) {
            onNodeWithContentDescription(open).performClick()
            reportsArrive()
            onNodeWithText(confirm).performClick()
            waitForIdle()
        }
        onNodeWithContentDescription("Screen settings").performClick()
        onNode(hasSetTextAction() and hasText("Lobby")).performTextReplacement("Hall")
        reportsArrive()
        onNodeWithText("Save").performClick()
        onNodeWithContentDescription("Screen settings").performClick()
        onNodeWithText("Factory reset").performClick()
        reportsArrive()
        onNodeWithText("Factory reset").performClick()
        waitForIdle()
        assertEquals(
            listOf("newer wipe p", "newer preview p", "newer preview null", "newer rename p Hall", "newer reset p"),
            calls.filterNot { it.startsWith("same") },
        )
    }

    @Test
    fun aPlaysChoiceStandsForAPlaylistNothingOrNone() {
        val playlists = folder.playlists
        assertEquals("folder:Events", ScreenPlan.forChoice("Events", "Nothing", playlists))
        assertEquals(ScreenPlan.NOTHING, ScreenPlan.forChoice("Nothing", "Nothing", playlists))
        assertEquals(null, ScreenPlan.forChoice("Gone", "Nothing", playlists))
    }

    @Test
    fun aScreenThatHasNotReportedYetShowsNoDetailsOrPicture() =
        card(ScreenRowState(screen, link.copy(status = null, preview = null), folder)) {
            onNodeWithText("Showing the idle card", substring = true).assertExists()
            onNodeWithText("No picture right now").assertExists()
        }

    @Test
    fun aPlaylistDeletedFromTheFolderReadsAsTheIdleCardAndWipeWaitsForAPlayerThatCan() =
        card(
            ScreenRowState(
                screen,
                link.copy(features = emptyList(), status = PlayerMessage.Status(showingPlaylistId = "folder:Gone")),
                folder,
            ),
        ) {
            onNodeWithText("Showing the idle card", substring = true).assertExists()
            onNodeWithContentDescription("Wipe content").assertIsNotEnabled()
        }

    @Test
    fun aPictureThatWillNotDecodeShowsAsNone() =
        card(ScreenRowState(screen, link.copy(preview = PlayerMessage.Preview("not a picture", 1, 1, 1)), folder)) {
            onNodeWithText("No picture right now").assertExists()
        }

    @Test
    fun theLargePictureEmptiesWhenThePictureStopsComing() = card(ScreenRowState(screen, link, folder)) { swap ->
        onNodeWithContentDescription("What the TV shows. Click to see it larger.").performClick()
        onNodeWithText("What Lobby shows").assertExists()
        swap(ScreenRowState(screen, link.copy(previewAtMs = 0), folder), actions())
        onNodeWithText("What Lobby shows").assertExists()
    }

    @Test
    fun screenSettingsSendOnlyWhatChanged() = card(ScreenRowState(screen, link, folder)) {
        onNodeWithContentDescription("Screen settings").performClick()
        onNodeWithText("MOUNTED").performClick()
        waitForIdle()
        onNodeWithText("Landscape, upside down").performClick()
        onNodeWithText("Save").performClick()
        // Saved again untouched: nothing to send.
        onNodeWithContentDescription("Screen settings").performClick()
        onNodeWithText("Save").performClick()
        assertEquals(listOf(" mounted p LANDSCAPE_FLIPPED"), calls)
    }

    @Test
    fun aBlankNameCannotBeSaved() = card(ScreenRowState(screen, link, folder)) {
        onNodeWithContentDescription("Screen settings").performClick()
        onNode(hasSetTextAction() and hasText("Lobby")).performTextReplacement(" ")
        onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun aWaitingScreenShowsItsCodeAndWhereItIs() = runComposeUiTest {
        var answer = ""
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                PendingScreenCard(
                    PendingScreen("p", "4821", Platform.WINDOWS_X64, "26.0.8", 1920, 1080, "10.0.0.9"),
                    onApprove = { answer = "approve" },
                    onDecline = { answer = "decline" },
                )
            }
        }
        onNodeWithText("Windows PC · 1920×1080 · 10.0.0.9").assertExists()
        onNodeWithText("Decline").performClick()
        assertEquals("decline", answer)
        onNodeWithText("Approve").performClick()
        assertEquals("approve", answer)
    }
}
