@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import org.churchpresenter.signage.smugmug.SmugMugProgress
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.churchpresenter.signage.smugmug.SmugMugAlbum
import org.churchpresenter.signage.smugmug.SmugMugSite
import org.churchpresenter.signage.smugmug.SmugMugState
import org.churchpresenter.signage.smugmug.SmugMugStatusException
import org.churchpresenter.signage.smugmug.SmugMugSync
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The SmugMug dialog and a slideshow's row, used as an operator would. */
class SmugMugDialogTest {
    private val site = SmugMugSite(
        "Grace",
        listOf(
            SmugMugAlbum("a1", "Harvest", "Public", imageCount = 120),
            SmugMugAlbum("a2", "Seminar", "Public", imageCount = 80),
            SmugMugAlbum("a3", "Staff only", "Unlisted", imageCount = 9),
        ),
    )

    private fun ComposeUiTest.show(content: @Composable () -> Unit) = setContent {
        ChurchPresenterTheme(ThemeMode.LIGHT) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
    }

    private class Saved {
        val playlists = mutableListOf<SmugMugPlaylist>()
        val keys = mutableListOf<String>()
        val opened = mutableListOf<String>()
        var lookups = 0
    }

    private fun ComposeUiTest.dialog(
        saved: Saved,
        initial: SmugMugPlaylist? = null,
        apiKey: String = "",
        lookUp: suspend (String, String) -> SmugMugSite = { _, _ -> site },
    ) = show {
        SmugMugDialog(
            initial = initial,
            apiKey = apiKey,
            onApiKey = { saved.keys += it },
            lookUp = { s, k ->
                saved.lookups++
                lookUp(s, k)
            },
            onSave = { saved.playlists += it },
            onDismiss = {},
            openUrl = { saved.opened += it },
        )
    }

    @Test
    fun `a site is looked up, a gallery left out, and the key and slideshow saved`() = runComposeUiTest {
        val saved = Saved()
        dialog(saved)
        onNodeWithText("Save").assertIsNotEnabled()
        field(SMUGMUG_SITE_TAG).performTextInput("photos.example.org")
        onNode(hasText("Test") and hasClickAction()).assertIsNotEnabled()
        field(SMUGMUG_KEY_TAG).performTextInput("KEY")
        onNode(hasText("Test") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("Galleries: 2 · photos to pick from: 200").assertExists()
        onNodeWithText("Staff only (9)").assertDoesNotExist()
        onNodeWithText("Seminar (80)").performClick()
        onNodeWithText("Galleries: 1 · photos to pick from: 120").assertExists()
        onNodeWithText("Get a free API key").performClick()
        onNodeWithText("Save").assertIsEnabled().performClick()
        val playlist = saved.playlists.single()
        assertEquals("Grace" to "photos.example.org", playlist.name to playlist.site)
        assertEquals(setOf("a2"), playlist.excludedAlbumKeys)
        assertEquals(listOf("KEY"), saved.keys)
        assertEquals(listOf(API_KEY_URL), saved.opened)
    }

    @Test
    fun `unlisted galleries are offered once asked for`() = runComposeUiTest {
        val saved = Saved()
        dialog(saved, SmugMugPlaylist.create("Photos").copy(site = "photos.example.org"), apiKey = "KEY")
        waitForIdle()
        assertEquals(1, saved.lookups)
        onNodeWithText("Include unlisted galleries").performClick()
        onNodeWithText("Staff only (9)").assertExists()
        onNodeWithText("Save").performClick()
        assertTrue(saved.playlists.single().includeUnlisted)
        assertTrue(saved.keys.isEmpty(), "the key did not change")
    }

    @Test
    fun `it says it is looking, a new site forgets the last lookup, and without one it is named after the site`() =
        runComposeUiTest {
            val saved = Saved()
            val answer = CompletableDeferred<SmugMugSite>()
            dialog(saved, apiKey = "KEY") { _, _ -> answer.await() }
            field(SMUGMUG_SITE_TAG).performTextInput("photos.example.org")
            onNode(hasText("Test") and hasClickAction()).performClick()
            onNodeWithText("Looking the site up…").assertExists()
            answer.complete(SmugMugSite("Grace", listOf(SmugMugAlbum("e", "Empty", "Public"))))
            waitForIdle()
            onNodeWithText("Galleries: 0 · photos to pick from: 0").assertExists()
            onNodeWithText("Galleries").assertDoesNotExist()
            field(SMUGMUG_SITE_TAG).performTextInput("/x")
            onNodeWithTag(SMUGMUG_LOOKUP_TAG).assertDoesNotExist()
            onNodeWithText("Save").performClick()
            assertEquals("photos.example.org/x", saved.playlists.single().name)
        }

    @Test
    fun `a name typed is kept`() = runComposeUiTest {
        val saved = Saved()
        dialog(saved, SmugMugPlaylist.create("").copy(site = "photos.example.org"), apiKey = "KEY")
        waitForIdle()
        field(SMUGMUG_NAME_TAG).performTextInput("Lobby")
        onNodeWithText("Save").performClick()
        assertEquals("Lobby", saved.playlists.single().name)
    }

    @Test
    fun `a wrong key, an unknown site and no internet each say so`() {
        val failures = listOf(
            SmugMugStatusException(401) to "SmugMug did not accept the API key.",
            SmugMugStatusException(404) to "SmugMug does not know that site.",
            IOException("offline") to "Could not reach SmugMug: offline",
        )
        failures.forEach { (failure, said) ->
            runComposeUiTest {
                val initial = SmugMugPlaylist.create("P").copy(site = "x.example.org")
                dialog(Saved(), initial, apiKey = "KEY") { _, _ -> throw failure }
                waitForIdle()
                onNodeWithTag(SMUGMUG_LOOKUP_TAG).assertExists()
                onNodeWithText(said).assertExists()
            }
        }
    }

    @Test
    fun `the add menu opens a new slideshow, and its row's edit opens it with its galleries`() = runComposeUiTest {
        val saved = Saved()
        val slideshow = SmugMugPlaylist.create("Photos").copy(site = "photos.example.org")
        val row = PlaylistSummary(slideshow.id, "Photos", kind = SourceKind.SMUGMUG, detail = "photos.example.org")
        val access = SmugMugAccess(
            apiKey = "KEY",
            onApiKey = { saved.keys += it },
            lookUp = { _, _ ->
                saved.lookups++
                site
            },
            openUrl = { saved.opened += it },
        )
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PanelWithMenu(
                        SignageFolderState(listOf(row), SignageSettings(added = listOf(slideshow)), ready = true),
                        FolderActions(
                            FakeDesktop().desktop,
                            onSaveSmugMug = { saved.playlists += it },
                            smugMug = access,
                        ),
                    )
                }
            }
        }
        onNodeWithText("Add a playlist").performClick()
        onNodeWithText("SmugMug slideshow…").performClick()
        onNodeWithText("Add a SmugMug slideshow").assertExists()
        onNodeWithText("Cancel").performClick()
        onNodeWithContentDescription("Edit SmugMug slideshow").performClick()
        waitForIdle()
        onNodeWithText("Edit SmugMug slideshow").assertExists()
        assertEquals(1, saved.lookups)
        onNodeWithText("Save").performClick()
        assertEquals(slideshow.id, saved.playlists.single().id)
    }

    @Test
    fun `without the app's own the dialog's access has no key and looks nothing up`() = runTest {
        val none = SmugMugAccess()
        assertEquals("", none.apiKey)
        none.onApiKey("ignored")
        assertFailsWith<IOException> { none.lookUp("photos.example.org", "KEY") }
    }

    @Test
    fun `a slideshow's row says how its photos stand, and picks new ones on request`() = runComposeUiTest {
        val picks = mutableListOf<String>()
        val row = PlaylistSummary(
            "smugmug:1", "Photos", pictures = 50, kind = SourceKind.SMUGMUG, detail = "photos.example.org", seconds = 8,
        )
        var state by mutableStateOf(
            SignageFolderState(listOf(row), ready = true, slideshows = mapOf("smugmug:1" to SmugMugState())),
        )
        setContent {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SignageFolderPanel(
                        state,
                        FolderActions(desktop = FakeDesktop().desktop, onPickNewPhotos = { picks += it }),
                        Modifier,
                    )
                }
            }
        }
        onNodeWithText("photos.example.org").assertExists()
        state = state.copy(slideshows = mapOf("smugmug:1" to SmugMugState(error = SmugMugSync.NO_KEY)))
        onNodeWithText("Needs a SmugMug API key: edit it to add one").assertExists()
        state = state.copy(slideshows = mapOf("smugmug:1" to SmugMugState(photos = 50, error = "offline")))
        onNodeWithText("Could not pick new photos (offline); still playing the 50 it has").assertExists()
        state = state.copy(picking = setOf("smugmug:1"))
        onNodeWithText("Picking new photos…").assertExists()
        val listing = SmugMugProgress(SmugMugProgress.Stage.LISTING, 3, 12)
        state = state.copy(pickProgress = mapOf("smugmug:1" to listing))
        onNodeWithText("Listing galleries: 3 of 12").assertExists()
        val downloading = SmugMugProgress(SmugMugProgress.Stage.DOWNLOADING, 23, 50)
        state = state.copy(pickProgress = mapOf("smugmug:1" to downloading))
        onNodeWithText("Downloading photos: 23 of 50").assertExists()
        val picked = SmugMugState(pickedAtMs = 0, photos = 50)
        state = state.copy(picking = emptySet(), pickProgress = emptyMap(), slideshows = mapOf("smugmug:1" to picked))
        onNodeWithText("50 photos · 8 s each", substring = true).assertExists()
        onNodeWithContentDescription("Pick new photos now").performClick()
        assertEquals(listOf("smugmug:1"), picks)
    }
}
