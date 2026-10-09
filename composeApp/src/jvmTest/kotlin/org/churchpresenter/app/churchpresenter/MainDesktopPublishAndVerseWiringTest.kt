@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.flow.MutableSharedFlow
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import java.awt.Rectangle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two pieces of the root's wiring that only run with real content in place: a deck published to
 * the phones once remote control is switched on ([ContentPublishWiring]), and a schedule verse put
 * live while the Bible tab is hidden, which the root resolves in the tab's place ([BibleWiring]).
 */
class MainDesktopPublishAndVerseWiringTest : MainDesktopComposeHarness() {

    private class Published {
        /** Deck id and how many slides it had loaded when the change was reported. */
        val slideChanges = mutableListOf<Pair<String, Int>>()
        val slidesLoaded = mutableListOf<Pair<String, Int>>()
        val versesLive = mutableListOf<List<SelectedVerse>>()
    }

    private fun ComposeUiTest.compose(
        settings: MutableState<AppSettings>,
        published: Published,
        upload: MutableSharedFlow<File> = MutableSharedFlow(extraBufferCapacity = 4),
        onActions: (ScheduleActions) -> Unit = {},
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
                        live = LiveOutputCallbacks(
                            presenting = {},
                            onVerseSelected = { published.versesLive += it },
                            onSongItemSelected = {},
                            devMode = false,
                        ),
                        publish = MainDesktopPublishers(
                            onScheduleActionsReady = onActions,
                            onSlideChanged = { id, _, total, _ -> published.slideChanges += id to total },
                            onPresentationSlidesLoaded = { id, _, _, _, slides, _ ->
                                published.slidesLoaded += id to slides.size
                            },
                        ),
                        flows = RemoteControlFlows(uploadPresentationFlow = upload),
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun deck(slides: Int): File {
        val file = File(dir, "Sermon.pptx")
        XMLSlideShow().use { ppt ->
            repeat(slides) { index ->
                ppt.createSlide().createTextBox().apply {
                    anchor = Rectangle(50, 50, 500, 120)
                    text = "Slide ${index + 1}"
                }
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    @Test
    fun `a loaded deck reports its slide, and is handed to the phones once remote control is on`() =
        runComposeUiTest {
            val settings = mutableStateOf(settings())
            val published = Published()
            val upload = MutableSharedFlow<File>(extraBufferCapacity = 4)
            compose(settings, published, upload)

            val file = deck(slides = 2)
            upload.tryEmit(file)
            // The slides load one by one, each reported as it lands; wait for the whole deck.
            waitUntil(timeoutMillis = 10_000) { stableFileId(file) to 2 in published.slideChanges }
            assertTrue(published.slidesLoaded.isEmpty(), "remote control is off, so the slides stay here")

            val remote = settings.value.presentationRemoteSettings.copy(remoteControlEnabled = true)
            settings.value = settings.value.copy(presentationRemoteSettings = remote)
            waitUntil(timeoutMillis = 10_000) { published.slidesLoaded.isNotEmpty() }
            assertEquals(stableFileId(file) to 2, published.slidesLoaded.last())
        }

    private fun bibleSettings(): AppSettings {
        SpbFixture.spbFile(
            dir,
            name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Test Bible",
                books = listOf(SpbFixture.Book(1, "Genesis", 1)),
                verses = (1..3).map { SpbFixture.Verse(1, 1, it, "Genesis one verse $it") },
            ),
        )
        return settings().copy(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "test.spb",
                translations = listOf(BibleTranslationSettings(fileName = "test.spb")),
            ),
            hiddenTabs = setOf(Tabs.BIBLE.name),
        )
    }

    @Test
    fun `a schedule verse put live with the Bible tab hidden is resolved by the root`() = runComposeUiTest {
        val settings = mutableStateOf(bibleSettings())
        val published = Published()
        var actions = ScheduleActions()
        compose(settings, published, onActions = { actions = it })

        actions.addBibleVerse("Genesis", 1, 2, "Genesis one verse 2", "", 1)
        waitForIdle()
        takeLive("Genesis")
        waitUntil(timeoutMillis = 10_000) { published.versesLive.isNotEmpty() }
        assertEquals(2, published.versesLive.last().first().verseNumber)
    }

    @Test
    fun `a schedule verse that resolves to nothing is dropped rather than sent live`() = runComposeUiTest {
        val settings = mutableStateOf(settings().copy(hiddenTabs = setOf(Tabs.BIBLE.name)))
        val published = Published()
        var actions = ScheduleActions()
        compose(settings, published, onActions = { actions = it })

        actions.addBibleVerse("Genesis", 1, 2, "Genesis one verse 2", "", 1)
        waitForIdle()
        takeLive("Genesis")
        waitForIdle()
        assertTrue(published.versesLive.isEmpty(), "no Bible is loaded, so there is nothing to put live")
    }
}
