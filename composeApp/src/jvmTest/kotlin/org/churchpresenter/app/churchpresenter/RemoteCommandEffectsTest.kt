package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.statistics.withStatsHome
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.bibletab.bibleFixture
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.liveoutput.PresenterManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RemoteCommandEffectsTest {

    private lateinit var dir: File
    private lateinit var pictures: PicturesViewModel
    private lateinit var presentations: PresentationViewModel
    private lateinit var bible: BibleViewModel
    private lateinit var presenter: PresenterManager

    private val selectedTabs = mutableListOf<Tabs>()
    private val songsSelected = mutableListOf<RemoteSongSelection>()
    private val picturesSelected = mutableListOf<ScheduleItem.PictureItem>()
    private val presentationsSelected = mutableListOf<ScheduleItem.PresentationItem>()
    private val mediaSelected = mutableListOf<ScheduleItem.MediaItem>()
    private var settings = AppSettings()
    private var slidePushes = 0

    /**
     * Each with room for one value, so [emit] hands it over without waiting for the collector: the
     * collector runs on the composition's test dispatcher, which cannot run while [emit] blocks the
     * test thread, and an unbuffered emit would wait for it forever.
     */
    private class Flows {
        val playPause = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val loopToggle = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val goto = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val selectPicture = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
        val nextPicture = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val previousPicture = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val nextSlide = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val previousSlide = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val selectSlide = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
        val selectVerse = MutableSharedFlow<SelectBibleVerseRequest>(extraBufferCapacity = 1)
        val selectSong = MutableSharedFlow<RemoteSongSelection>(extraBufferCapacity = 1)
        val selectPictureItem = MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = 1)
        val selectPresentation = MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = 1)
        val selectMedia = MutableSharedFlow<ScheduleItem.MediaItem>(extraBufferCapacity = 1)
    }

    @BeforeTest
    fun create() {
        dir = Files.createTempDirectory("cp-remote-effects").toFile()
        pictures = PicturesViewModel()
        presentations = PresentationViewModel()
        bible = BibleViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        presenter = PresenterManager()
    }

    @AfterTest
    fun cleanUp() {
        runCatching { pictures.dispose() }
        runCatching { bible.dispose() }
        dir.deleteRecursively()
    }

    private val pngBytes: ByteArray by lazy {
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }

    private fun images(vararg names: String): List<File> =
        names.map { File(dir, it).apply { writeBytes(pngBytes) } }

    private fun ComposeUiTest.effects(
        flows: Flows? = null,
        resolveImageFile: ((String, Int) -> File?)? = null,
        statisticsManager: StatisticsManager? = null,
    ) = effectsWired(mutableStateOf(flows), resolveImageFile, statisticsManager = statisticsManager)

    private fun ComposeUiTest.effectsWired(
        wired: State<Flows?>,
        resolveImageFile: ((String, Int) -> File?)? = null,
        appSettings: State<AppSettings>? = null,
        statisticsManager: StatisticsManager? = null,
    ) {
        setContent {
            val flows = wired.value
            RemoteCommandEffects(
                appSettings = appSettings?.value ?: settings,
                picturesViewModel = pictures,
                presentationViewModel = presentations,
                bibleViewModel = bible,
                presenterManager = presenter,
                resolveImageFile = resolveImageFile,
                onSettingsChange = { transform -> settings = transform(settings) },
                onSongItemSelected = { songsSelected.add(it) },
                onPictureItemSelected = { picturesSelected.add(it) },
                onPresentationItemSelected = { presentationsSelected.add(it) },
                onMediaItemSelected = { mediaSelected.add(it) },
                onSelectTab = { selectedTabs.add(it) },
                pushCurrentSlideIfLive = { slidePushes++ },
                remotePresentationPlayPauseFlow = flows?.playPause,
                remotePresentationLoopToggleFlow = flows?.loopToggle,
                remotePresentationGotoFlow = flows?.goto,
                selectPictureImageFlow = flows?.selectPicture,
                nextPictureFlow = flows?.nextPicture,
                previousPictureFlow = flows?.previousPicture,
                nextSlideFlow = flows?.nextSlide,
                previousSlideFlow = flows?.previousSlide,
                selectSlideFlow = flows?.selectSlide,
                selectBibleVerseFlow = flows?.selectVerse,
                remoteSelectSongFlow = flows?.selectSong,
                remoteSelectPictureFlow = flows?.selectPictureItem,
                remoteSelectPresentationFlow = flows?.selectPresentation,
                remoteSelectMediaFlow = flows?.selectMedia,
                statisticsManager = statisticsManager,
            )
        }
        waitForIdle()
    }

    private fun <T> ComposeUiTest.emit(flow: MutableSharedFlow<T>, value: T) {
        waitUntil("the effect subscribed") { flow.subscriptionCount.value > 0 }
        runBlocking { flow.emit(value) }
        waitForIdle()
    }

    @Test
    fun `with no remote wired at all nothing is driven`() = runComposeUiTest {
        effects(flows = null)

        assertEquals(Presenting.NONE, presenter.slideContent.value)
        assertTrue(selectedTabs.isEmpty())
        assertNull(presenter.selectedImagePath.value)
    }

    @Test
    fun `a remote re-wired to new flows listens to the new ones and lets the old ones go`() = runComposeUiTest {
        val first = Flows()
        val wired = mutableStateOf<Flows?>(first)
        val appSettings = mutableStateOf(AppSettings())
        effectsWired(wired, appSettings = appSettings)
        emit(first.nextSlide, Unit)

        val second = Flows()
        wired.value = second
        appSettings.value = AppSettings(presentationSettings = settings.presentationSettings.copy(isLooping = true))
        waitForIdle()
        emit(second.nextSlide, Unit)
        emit(second.previousSlide, Unit)

        assertEquals(0, first.nextSlide.subscriptionCount.value, "the old remote is no longer heard")
        assertEquals(3, slidePushes)
    }

    @Test
    fun `unwiring the remote stops every command from reaching the app`() = runComposeUiTest {
        val flows = Flows()
        val wired = mutableStateOf<Flows?>(flows)
        effectsWired(wired)
        waitUntil("the effect subscribed") { flows.nextSlide.subscriptionCount.value > 0 }

        wired.value = null
        waitForIdle()

        assertEquals(0, flows.nextSlide.subscriptionCount.value)
        assertEquals(0, flows.selectSong.subscriptionCount.value)
    }

    @Test
    fun `a remote play-pause toggles playback`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val before = presentations.isPlaying

        emit(flows.playPause, Unit)

        assertEquals(!before, presentations.isPlaying)
    }

    @Test
    fun `a remote loop toggle is written back to the settings`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val before = presentations.isLooping

        emit(flows.loopToggle, Unit)

        assertEquals(!before, presentations.isLooping)
        assertEquals(presentations.isLooping, settings.presentationSettings.isLooping)
    }

    @Test
    fun `a goto for a slide that does not exist is ignored`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.goto, 7)

        assertEquals(0, presentations.selectedSlideIndex, "there is no deck loaded to go to")
    }

    @Test
    fun `next and previous slide both ask for the live slide to be pushed`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.nextSlide, Unit)
        emit(flows.previousSlide, Unit)

        assertEquals(2, slidePushes)
    }

    @Test
    fun `selecting a slide index the deck does not have does nothing`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.selectSlide, "deck" to 4)

        assertNull(presenter.selectedSlide.value)
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }

    @Test
    fun `selecting a slide stages it, its successor and its notes, and takes the deck live`() = runComposeUiTest {
        presentations.slideFiles.addAll(images("s1.png", "s2.png", "s3.png"))
        val flows = Flows()
        effects(flows)

        emit(flows.selectSlide, "deck" to 1)
        // The slides are decoded off the main thread, then staged, then the deck goes live -- that
        // last step is the signal the whole selection has landed.
        waitUntil("the deck went live") { presenter.slideContent.value == Presenting.PRESENTATION }

        assertEquals(1, presentations.selectedSlideIndex)
        assertNotNull(presenter.nextSlide.value, "and the one after it is staged for the stage monitor")
        assertEquals("", presenter.presenterNotes.value, "a deck with no notes has none to show")
        assertEquals(Presenting.PRESENTATION, presenter.slideContent.value)
        assertTrue(presenter.showPresenterWindow.value)
    }

    @Test
    fun `selecting the last slide stages nothing after it`() = runComposeUiTest {
        presentations.slideFiles.addAll(images("s1.png", "s2.png"))
        val flows = Flows()
        effects(flows)

        emit(flows.selectSlide, "deck" to 1)
        waitUntil("the slide decoded") { presenter.selectedSlide.value != null }

        assertNull(presenter.nextSlide.value)
    }

    @Test
    fun `selecting a slide while the deck is already live leaves the output where it is`() = runComposeUiTest {
        presentations.slideFiles.addAll(images("s1.png", "s2.png"))
        presenter.setPresentingMode(Presenting.PRESENTATION)
        presenter.setShowPresenterWindow(false)
        val flows = Flows()
        effects(flows)

        emit(flows.selectSlide, "deck" to 0)
        waitUntil("the slide decoded") { presenter.selectedSlide.value != null }

        assertEquals(0, presentations.selectedSlideIndex)
        assertEquals(Presenting.PRESENTATION, presenter.slideContent.value)
        assertFalse(presenter.showPresenterWindow.value, "an already-live deck is not re-opened on screen")
    }

    @Test
    fun `a goto for a slide the deck has selects it`() = runComposeUiTest {
        presentations.slideFiles.addAll(images("s1.png", "s2.png"))
        val flows = Flows()
        effects(flows)

        emit(flows.goto, 1)

        assertEquals(1, presentations.selectedSlideIndex)
    }

    @Test
    fun `a remote picture selection resolved through the server goes live`() = runComposeUiTest {
        val files = images("a.jpg", "b.jpg", "c.jpg")
        pictures.loadImagesFromFolder(dir)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, index -> files.getOrNull(index) })

        emit(flows.selectPicture, "folder-1" to 1)

        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, presenter.slideContent.value)
        assertTrue(presenter.showPresenterWindow.value)
    }

    @Test
    fun `the next picture is staged alongside the one going live`() = runComposeUiTest {
        val files = images("a.jpg", "b.jpg", "c.jpg")
        pictures.loadImagesFromFolder(dir)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, index -> files.getOrNull(index) })

        emit(flows.selectPicture, "folder-1" to 0)

        assertEquals(files[1].absolutePath, presenter.nextImagePath.value)
    }

    @Test
    fun `a picture the server cannot resolve falls back to the loaded folder`() = runComposeUiTest {
        val files = images("a.jpg", "b.jpg")
        pictures.loadImagesFromFolder(dir)
        val flows = Flows()
        effects(flows, resolveImageFile = null)

        emit(flows.selectPicture, "folder-1" to 1)

        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, presenter.slideContent.value)
    }

    @Test
    fun `a picture that is nowhere at all leaves the screen alone`() = runComposeUiTest {
        val flows = Flows()
        effects(flows, resolveImageFile = { _, _ -> File(dir, "never-written.jpg") })

        emit(flows.selectPicture, "folder-1" to 0)

        assertNull(presenter.selectedImagePath.value)
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }

    @Test
    fun `next and previous picture both survive an empty folder`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.nextPicture, Unit)
        emit(flows.previousPicture, Unit)

        assertNull(presenter.selectedImagePath.value)
    }

    @Test
    fun `the next picture command advances the live picture`() = runComposeUiTest {
        val files = images("a.jpg", "b.jpg")
        pictures.loadImagesFromFolder(dir)
        pictures.selectedImageIndex = 0
        presenter.setPresentingMode(Presenting.PICTURES)
        val flows = Flows()
        effects(flows)

        emit(flows.nextPicture, Unit)

        assertEquals(1, pictures.selectedImageIndex)
        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value)
    }

    @Test
    fun `a remote verse goes live even with no bible loaded to resolve it against`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(
            flows.selectVerse,
            SelectBibleVerseRequest(
                bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "For God so loved the world",
            ),
        )

        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
        assertTrue(presenter.showPresenterWindow.value)
        assertEquals("For God so loved the world", presenter.selectedVerses.value.single().verseText)
    }

    @Test
    fun `a remote passage keeps the range it was sent with`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(
            flows.selectVerse,
            SelectBibleVerseRequest(
                bookName = "John", chapter = 3, verseNumber = 16,
                verseText = "…", verseRange = "16-18",
            ),
        )

        assertEquals("16-18", presenter.selectedVerses.value.single().verseRange)
    }

    @Test
    fun `a remote song selection opens the songs tab`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val song = ScheduleItem.SongItem(id = "1", songNumber = 42, title = "Amazing Grace", songbook = "Hymns")
        val selection = RemoteSongSelection(song, goLive = true, source = "remote")

        emit(flows.selectSong, selection)

        assertEquals(listOf(selection), songsSelected, "the whole hand-over reaches the app, go-live included")
        assertEquals(listOf(Tabs.SONGS), selectedTabs)
    }

    @Test
    fun `a remote picture-folder selection opens the pictures tab`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val item = ScheduleItem.PictureItem(
            id = "1",
            folderPath = dir.absolutePath,
            folderName = "Easter",
            imageCount = 0,
        )

        emit(flows.selectPictureItem, item)

        assertEquals(listOf(item), picturesSelected)
        assertEquals(listOf(Tabs.PICTURES), selectedTabs)
    }

    @Test
    fun `a remote presentation selection opens the presentation tab`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val item = ScheduleItem.PresentationItem(
            id = "1", filePath = "/decks/sermon.pptx", fileName = "sermon.pptx", slideCount = 3, fileType = "pptx",
        )

        emit(flows.selectPresentation, item)

        assertEquals(listOf(item), presentationsSelected)
        assertEquals(listOf(Tabs.PRESENTATION), selectedTabs)
    }

    /**
     * A clip has to reach the Media tab, which is the only thing that loads and plays one.
     *
     * Media was missing from this dispatch entirely, so a projected video left the presenter in
     * MEDIA mode with nothing loaded -- a black output while everything else looked right.
     */
    @Test
    fun `a remote media selection opens the media tab`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)
        val item = ScheduleItem.MediaItem(
            id = "1", mediaUrl = "/clips/welcome.mp4", mediaTitle = "welcome", mediaType = "local",
        )

        emit(flows.selectMedia, item)

        assertEquals(listOf(item), mediaSelected)
        assertEquals(listOf(Tabs.MEDIA), selectedTabs)
    }

    @Test
    fun `a remote command never opens a tab nobody asked for`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.playPause, Unit)

        assertFalse(selectedTabs.isNotEmpty(), "transport is not a reason to move the operator's view")
    }

    // ── A picture chosen from a folder this machine is or is not showing ────────

    @Test
    fun `a picture from another folder loads that folder before it goes live`() = runComposeUiTest {
        val shown = File(dir, "shown").apply { mkdirs() }
        File(shown, "x.png").writeBytes(pngBytes)
        val uploads = File(dir, "uploads").apply { mkdirs() }
        val uploaded = listOf("u1.png", "u2.png").map { File(uploads, it).apply { writeBytes(pngBytes) } }
        pictures.selectFolder(shown)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, index -> uploaded.getOrNull(index) })

        emit(flows.selectPicture, "device_uploads" to 1)

        assertEquals(uploads.absolutePath, pictures.selectedFolder?.absolutePath, "the phone's folder is loaded")
        assertEquals(1, pictures.selectedImageIndex)
        assertEquals(uploaded[1].absolutePath, presenter.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, presenter.slideContent.value)
    }

    @Test
    fun `a picture from the folder already shown is selected without reloading it`() = runComposeUiTest {
        val files = images("a.png", "b.png")
        pictures.selectFolder(dir)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, index -> files.getOrNull(index) })

        emit(flows.selectPicture, stableFileId(dir) to 1)

        assertEquals(dir.absolutePath, pictures.selectedFolder?.absolutePath)
        assertEquals(1, pictures.selectedImageIndex)
        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value)
        assertNull(presenter.nextImagePath.value, "nothing is staged after the last picture")
    }

    @Test
    fun `a resolved picture past the end of the loaded folder goes live without moving the selection`() =
        runComposeUiTest {
            val files = images("a.png", "b.png")
            pictures.selectFolder(dir)
            val flows = Flows()
            // The server's map knows more images than this folder holds -- a stale upload index.
            effects(flows, resolveImageFile = { _, _ -> files[0] })

            emit(flows.selectPicture, stableFileId(dir) to 5)

            assertEquals(0, pictures.selectedImageIndex, "there is no sixth image to select here")
            assertEquals(files[0].absolutePath, presenter.selectedImagePath.value)
            assertEquals(Presenting.PICTURES, presenter.slideContent.value)
        }

    @Test
    fun `a negative index never selects a picture or a slide`() = runComposeUiTest {
        val files = images("a.png", "b.png")
        pictures.selectFolder(dir)
        presentations.slideFiles.addAll(files)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, _ -> files[1] })

        emit(flows.selectPicture, stableFileId(dir) to -1)
        emit(flows.selectSlide, "deck" to -1)

        assertEquals(0, pictures.selectedImageIndex, "the selection stays where it was")
        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value, "the resolved file still goes up")
        assertEquals(0, presentations.selectedSlideIndex)
        assertNull(presenter.selectedSlide.value, "no slide is staged for an index the deck cannot have")
    }

    @Test
    fun `a negative index with nothing resolved leaves the screen alone`() = runComposeUiTest {
        images("a.png")
        pictures.loadImagesFromFolder(dir)
        val flows = Flows()
        effects(flows)

        emit(flows.selectPicture, "folder-1" to -1)

        assertNull(presenter.selectedImagePath.value)
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }

    // ── A verse resolved against this machine's own Bible ──────────────────────

    /** Swaps in a [BibleViewModel] over a real module, loaded before this returns. */
    private fun loadBible(): AppSettings {
        SpbFixture.spbFile(dir, content = bibleFixture)
        val loaded = AppSettings(
            bibleSettings = BibleSettings(
                storageDirectory = dir.absolutePath,
                primaryBible = "test.spb",
                translations = listOf(BibleTranslationSettings(fileName = "test.spb")),
            ),
        )
        runCatching { bible.dispose() }
        bible = BibleViewModel(loaded, dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)
        settings = loaded
        return loaded
    }

    @Test
    fun `a remote verse is shown in this machine's own wording and counted for each verse of its range`() {
        TestSingletons.latchToTestHome()
        withStatsHome {
            loadBible()
            val statistics = StatisticsManager()
            runComposeUiTest {
                val flows = Flows()
                effects(flows, statisticsManager = statistics)

                emit(
                    flows.selectVerse,
                    SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = 16, verseRange = "16-17"),
                )

                val shown = presenter.selectedVerses.value.single()
                assertEquals("For God so loved the world.", shown.verseText, "looked up locally")
                assertEquals(Presenting.BIBLE, presenter.slideContent.value)
                val counted = statistics.getAllVersesInRange(0L, Long.MAX_VALUE)
                assertEquals(listOf(16, 17), counted.map { it.verseNumber }.sorted())
                assertTrue(counted.all { it.bookName == "John" && it.chapter == 3 })
            }
        }
    }

    @Test
    fun `a remote verse in a book this Bible lacks still goes up as the phone sent it`() {
        TestSingletons.latchToTestHome()
        withStatsHome {
            loadBible()
            val statistics = StatisticsManager()
            runComposeUiTest {
                val flows = Flows()
                effects(flows, statisticsManager = statistics)

                emit(
                    flows.selectVerse,
                    SelectBibleVerseRequest(bookName = "Jude", chapter = 1, verseNumber = 3, verseText = "Contend"),
                )

                val shown = presenter.selectedVerses.value.single()
                assertEquals("Contend", shown.verseText)
                assertEquals("Jude", shown.bookName)
                assertEquals(listOf(3), statistics.getAllVersesInRange(0L, Long.MAX_VALUE).map { it.verseNumber })
            }
        }
    }
}
