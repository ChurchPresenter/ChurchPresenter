package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.server.SongDetailDto
import org.churchpresenter.server.SongDto
import org.churchpresenter.server.SongSectionDto
import org.churchpresenter.server.SongbookEntry
import org.churchpresenter.sharedui.models.Tabs
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InstanceLinkMirrorWiringTest : MainDesktopComposeHarness() {

    private val catalog = SongCatalogResponse(
        songBook = listOf(
            SongbookEntry(
                bookName = "Primary",
                songTotal = 2,
                songs = listOf(
                    SongDto(number = "7", title = "Mirrored Hymn"),
                    SongDto(number = "8", title = "Missing Hymn"),
                ),
            ),
        ),
        songBooks = 1,
        total = 2,
    )

    private fun detail(number: String) = SongDetailDto(
        number = number,
        title = "Mirrored Hymn",
        songbook = "Primary",
        tune = "",
        author = "",
        composer = "",
        sectionTotal = 1,
        sections = listOf(SongSectionDto("verse", listOf("mirrored line"))),
    )

    @Test
    fun `a followed song's lyrics are fetched from the primary when it is picked`() = runComposeUiTest {
        val asked = Collections.synchronizedList(mutableListOf<String>())
        var bibleSignal by mutableStateOf(0)
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = showingOnly(Tabs.SONGS),
                    presenterManager = PresenterManager(),
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    live = LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {}),
                    link = InstanceLinkBridge(
                        connectionStatus = InstanceLinkStatus.CONNECTED,
                        role = InstanceLinkRole.CONTROLLED,
                        followingHost = "10.0.0.9",
                        remoteSongCatalog = catalog,
                        fetchSongDetail = { number, _ ->
                            asked += number
                            if (number == "7") detail(number) else null
                        },
                        fetchBibleFile = { null },
                        bibleUpdatedSignal = bibleSignal,
                    ),
                )
            }
        }
        waitForIdle()

        waitUntil { onAllNodesWithText("Mirrored Hymn", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Mirrored Hymn", substring = true)[0].performClick()
        waitUntil { "7" in asked }
        onAllNodesWithText("Missing Hymn", substring = true)[0].performClick()
        waitUntil { "8" in asked }

        bibleSignal++
        waitForIdle()
        assertEquals(listOf("7", "8"), asked.distinct())
    }
}
