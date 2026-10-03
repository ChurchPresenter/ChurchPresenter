@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.planningcenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.planningcenter.PcoItemType
import org.churchpresenter.planningcenter.PlanningCenterAuthServer
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.PlanningCenterSettings
import org.churchpresenter.settings.SettingsManager
import org.churchpresenter.sharedui.utils.UrlOpener
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The import's entry point, with the host's window stood in for by one that draws its content in
 * place: which window it asks for, connecting first when there is no session, and a plan song the
 * library lacks going through the host's song editor and coming back matched.
 */
class PlanningCenterImportDialogTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-dialog-entry").toFile()
        System.setProperty("user.home", home.absolutePath)
        val songs = File(home, "songs").also { it.mkdirs() }
        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().let { s ->
                s.copy(songSettings = s.songSettings.copy(storageDirectory = songs.absolutePath))
            },
        )
        mockkObject(PlanningCenterClient, PlanningCenterAuthServer)
        coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.Success(
                listOf(PlanningCenterClient.ServiceType("st-1", "Sunday Morning")),
            )
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(
                listOf(
                    PlanningCenterClient.Plan("p1", "Easter", "Apr 5"),
                    PlanningCenterClient.Plan("p2", "Undated", ""),
                ),
            )
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(
                listOf(
                    PlanningCenterClient.PlanItem(
                        id = "s1", title = "New Song", description = "Line one", itemType = PcoItemType.SONG,
                        sequence = 0,
                    ),
                ),
            )
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PlanningCenterClient, PlanningCenterAuthServer)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private val connected = PlanningCenterSettings(
        accessToken = "token",
        refreshToken = "refresh",
        tokenExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
        importSongbookName = "Imported",
    )

    /** What the dialog asked of its host while it was up. */
    private class Host {
        val windows = mutableListOf<PlanningCenterWindowSpec>()
        var editing: SongItem? = null
        var songbook: String? = null
        var save: ((SongItem) -> Unit)? = null
        var dismissEditor: (() -> Unit)? = null
        var dismissed = 0
    }

    private fun show(
        settings: PlanningCenterSettings,
        isVisible: Boolean = true,
        check: ComposeUiTest.(Host) -> Unit,
    ) {
        val host = Host()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    PlanningCenterImportDialog(
                        isVisible = isVisible,
                        settings = settings,
                        services = PlanningCenterImportServices("client", "secret"),
                        window = { spec, content ->
                            host.windows += spec
                            content()
                        },
                        editSong = { song, songbook, onDismiss, onSave ->
                            host.editing = song
                            host.songbook = songbook
                            host.dismissEditor = onDismiss
                            host.save = onSave
                        },
                        onDismiss = { host.dismissed++ },
                        onTokensRefreshed = { _, _, _ -> },
                        onAddSong = { _, _, _, _ -> },
                        onAddLabel = { _, _, _ -> },
                        onAddPresentation = { _, _, _, _ -> },
                        onAddPicture = { _, _, _ -> },
                        onAddMedia = { _, _, _ -> },
                        onAddAnnouncement = {},
                        onAddBibleVerse = { _, _, _, _, _, _ -> },
                        onConnected = { _, _, _, _ -> },
                        onDisconnect = {},
                    )
                }
            }
            check(host)
        }
    }

    private fun ComposeUiTest.awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            repeat(3) { SwingUtilities.invokeAndWait { } }
            waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what")
    }

    private fun ComposeUiTest.shows(text: String) =
        runCatching { onNodeWithText(text, substring = true, useUnmergedTree = true).assertExists() }.isSuccess

    @Test
    fun `a hidden dialog asks for no window`() = show(connected, isVisible = false) { host ->
        waitForIdle()
        assertTrue(host.windows.isEmpty())
        assertNull(host.save, "nor for the song editor")
    }

    @Test
    fun `with no session it asks for the small connect window, and a failed sign-in says why`() {
        coEvery { PlanningCenterAuthServer.awaitAuthorizationCode() } returns
            PlanningCenterAuthServer.CallbackResult.Timeout
        mockkObject(UrlOpener)
        every { UrlOpener.open(any(), any(), any(), any(), any()) } returns true
        try {
            show(PlanningCenterSettings()) { host ->
                waitForIdle()
                val window = host.windows.last()
                assertEquals(460.dp to 260.dp, window.width to window.height)
                assertEquals(false, window.resizable)
                window.onClose()
                assertEquals(1, host.dismissed, "closing the window dismisses the dialog")
                onNodeWithText("Connect to Planning Center", useUnmergedTree = true).performClick()
                awaitUntil("the sign-in error") { shows("Timed out waiting for browser sign-in") }
            }
        } finally {
            unmockkObject(UrlOpener)
        }
    }

    @Test
    fun `with a session it opens the import window on the nearest plan, named with its date`() =
        show(connected.copy(connectedPersonName = "")) { host ->
            awaitUntil("the plan to load") { shows("New Song") }
            val window = host.windows.last()
            assertEquals(900.dp to 750.dp, window.width to window.height)
            assertTrue(window.resizable)
            assertTrue(shows("Easter — Apr 5"), "a dated plan shows its date")
            assertTrue(shows("Connected as ?"), "no name on file reads as a question mark")
        }

    @Test
    fun `a song the library lacks goes through the host's editor and comes back matched`() =
        show(connected) { host ->
            awaitUntil("the plan to load") { shows("Add Song") }
            assertNull(host.editing, "the editor stays closed until asked")
            onNodeWithText("Add Song", substring = true, useUnmergedTree = true).performClick()
            awaitUntil("the editor to open") { host.editing != null }
            val prefill = assertNotNull(host.editing)
            assertEquals("New Song", prefill.title)
            assertEquals(listOf("Line one"), prefill.lyrics)
            assertEquals("Imported", host.songbook)

            assertNotNull(host.save)(prefill.copy(number = "0042", songbook = "Imported"))
            awaitUntil("the song to be matched") { shows("Matched") }
            assertNull(host.editing, "saving closes the editor")
            assertTrue(File(home, "songs/Imported/0042 - New Song.song").exists(), "the song is written to the library")
        }

    @Test
    fun `closing the editor without saving leaves the song unmatched`() = show(connected) { host ->
        awaitUntil("the plan to load") { shows("Add Song") }
        onNodeWithText("Add Song", substring = true, useUnmergedTree = true).performClick()
        awaitUntil("the editor to open") { host.editing != null }
        assertNotNull(host.dismissEditor)()
        awaitUntil("the editor to close") { host.editing == null }
        assertTrue(shows("Add Song"), "still offered")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
