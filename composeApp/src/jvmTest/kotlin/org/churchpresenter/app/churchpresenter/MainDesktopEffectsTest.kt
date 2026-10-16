@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.runBlocking
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainDesktopEffectsTest : MainDesktopComposeHarness() {

    private fun pictureItem(folder: File) = ScheduleItem.PictureItem(
        id = "p",
        folderPath = folder.absolutePath,
        folderName = folder.name,
        imageCount = 3,
    )

    @Test
    fun `a remote tab request moves the main window to that tab`() {
        val flows = Flows()
        val wiring = Wiring()
        root(flows = flows, wiring = wiring) {
            val before = wiring.tabChanges.last()
            runBlocking { flows.selectTab.emit(Tabs.PICTURES) }
            waitUntil(timeoutMillis = 5_000) { wiring.tabChanges.last() != before }
            assertEquals(Tabs.PICTURES.ordinal, wiring.tabChanges.last())
        }
    }

    @Test
    fun `the helper's show reference opens the Bible tab before sending the verse`() {
        val flows = Flows()
        val wiring = Wiring()
        root(flows = flows, wiring = wiring) {
            runBlocking { flows.selectTab.emit(Tabs.SONGS) }
            waitUntil(timeoutMillis = 5_000) { wiring.tabChanges.last() == Tabs.SONGS.ordinal }
            runBlocking { flows.showReference.emit("John 3:16") }
            waitUntil(timeoutMillis = 5_000) { wiring.tabChanges.last() == Tabs.BIBLE.ordinal }
            waitForIdle()
        }
    }

    @Test
    fun `a remote section change on live lyrics reaches the songs tab and leaves the output alone`() {
        val presenter = PresenterManager()
        root(appSettings = withOneSong(), presenterManager = presenter) {
            presenter.setPresentingMode(Presenting.LYRICS)
            presenter.setSongDisplaySectionIndex(2)
            waitForIdle()
            assertEquals(Presenting.LYRICS, presenter.slideContent.value)
            assertEquals(2, presenter.songDisplaySectionIndex.value)
        }
    }

    @Test
    fun `a picture row for a real folder publishes the folder once it loads`() {
        val flows = Flows()
        val wiring = Wiring()
        val folder = pictureFolder()
        root(flows = flows, wiring = wiring) {
            runBlocking { flows.remoteSelectPicture.emit(pictureItem(folder)) }
            waitUntil(timeoutMillis = 5_000) { wiring.picturesLoaded.isNotEmpty() }
            assertEquals(stableFileId(folder), wiring.picturesLoaded.last())
        }
    }

    @Test
    fun `a picture row for a folder that is gone loads nothing`() {
        val flows = Flows()
        val wiring = Wiring()
        root(flows = flows, wiring = wiring) {
            runBlocking { flows.remoteSelectPicture.emit(pictureItem(File(dir, "missing"))) }
            waitForIdle()
            assertTrue(wiring.picturesLoaded.isEmpty())
        }
    }

    private fun ComposeUiTest.composeWith(
        settings: MutableState<AppSettings>,
        tabChanges: MutableList<Int>,
        slideChanges: MutableList<String>,
        flows: Flows = Flows(),
    ) {
        setContent {
            CompositionLocalProvider(
                LocalShortcuts provides ShortcutMap.from(settings.value.keyboardShortcutSettings),
                LocalMediaViewModel provides null,
            ) {
                MaterialTheme {
                    MainDesktop(
                        appSettings = settings.value,
                        presenterManager = PresenterManager(),
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        live = LiveOutputCallbacks(presenting = {}, onVerseSelected = {}, onSongItemSelected = {}),
                        publish = MainDesktopPublishers(
                            onTabChange = { tabChanges += it },
                            onSlideChanged = { id, _, _, _ -> slideChanges += id },
                        ),
                        flows = RemoteControlFlows(selectTabFlow = flows.selectTab),
                    )
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `hiding the open tab clamps the selection back onto a visible one`() = runComposeUiTest {
        val settings = mutableStateOf(settings())
        val tabChanges = mutableListOf<Int>()
        val flows = Flows()
        composeWith(settings, tabChanges, mutableListOf(), flows)
        runBlocking { flows.selectTab.emit(Tabs.CANVAS) }
        waitUntil(timeoutMillis = 5_000) { tabChanges.last() != 0 }
        settings.value = showingOnly(Tabs.BIBLE)
        waitForIdle()
        assertEquals(0, tabChanges.last())
    }

    @Test
    fun `turning remote control on with no deck loaded publishes nothing`() = runComposeUiTest {
        val settings = mutableStateOf(settings())
        val slideChanges = mutableListOf<String>()
        composeWith(settings, mutableListOf(), slideChanges)
        settings.value = settings.value.copy(
            presentationRemoteSettings = settings.value.presentationRemoteSettings.copy(remoteControlEnabled = true),
        )
        waitForIdle()
        assertTrue(slideChanges.isEmpty())
    }
}
