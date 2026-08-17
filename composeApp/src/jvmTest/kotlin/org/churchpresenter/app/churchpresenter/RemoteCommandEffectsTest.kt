package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.viewmodel.BibleViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
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
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RemoteCommandEffectsTest {

    private lateinit var dir: File
    private lateinit var pictures: PicturesViewModel
    private lateinit var presentations: PresentationViewModel
    private lateinit var bible: BibleViewModel
    private lateinit var presenter: PresenterManager

    private val selectedTabs = mutableListOf<Tabs>()
    private val songsSelected = mutableListOf<ScheduleItem.SongItem>()
    private val picturesSelected = mutableListOf<ScheduleItem.PictureItem>()
    private val presentationsSelected = mutableListOf<ScheduleItem.PresentationItem>()
    private var settings = AppSettings()
    private var songVersionBumps = 0
    private var slidePushes = 0

    private class Flows {
        val playPause = MutableSharedFlow<Unit>()
        val loopToggle = MutableSharedFlow<Unit>()
        val goto = MutableSharedFlow<Int>()
        val selectPicture = MutableSharedFlow<Pair<String, Int>>()
        val nextPicture = MutableSharedFlow<Unit>()
        val previousPicture = MutableSharedFlow<Unit>()
        val nextSlide = MutableSharedFlow<Unit>()
        val previousSlide = MutableSharedFlow<Unit>()
        val selectSlide = MutableSharedFlow<Pair<String, Int>>()
        val selectVerse = MutableSharedFlow<SelectBibleVerseRequest>()
        val selectSong = MutableSharedFlow<ScheduleItem.SongItem>()
        val selectPictureItem = MutableSharedFlow<ScheduleItem.PictureItem>()
        val selectPresentation = MutableSharedFlow<ScheduleItem.PresentationItem>()
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
    ) {
        setContent {
            RemoteCommandEffects(
                appSettings = settings,
                picturesViewModel = pictures,
                presentationViewModel = presentations,
                bibleViewModel = bible,
                presenterManager = presenter,
                onSongItemVersionBump = { songVersionBumps++ },
                resolveImageFile = resolveImageFile,
                onSettingsChange = { transform -> settings = transform(settings) },
                onSongItemSelected = { songsSelected.add(it) },
                onPictureItemSelected = { picturesSelected.add(it) },
                onPresentationItemSelected = { presentationsSelected.add(it) },
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

        assertEquals(Presenting.NONE, presenter.presentingMode.value)
        assertTrue(selectedTabs.isEmpty())
        assertNull(presenter.selectedImagePath.value)
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
        assertEquals(Presenting.NONE, presenter.presentingMode.value)
    }

    @Test
    fun `a remote picture selection resolved through the server goes live`() = runComposeUiTest {
        val files = images("a.jpg", "b.jpg", "c.jpg")
        pictures.loadImagesFromFolder(dir)
        val flows = Flows()
        effects(flows, resolveImageFile = { _, index -> files.getOrNull(index) })

        emit(flows.selectPicture, "folder-1" to 1)

        assertEquals(files[1].absolutePath, presenter.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, presenter.presentingMode.value)
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
        assertEquals(Presenting.PICTURES, presenter.presentingMode.value)
    }

    @Test
    fun `a picture that is nowhere at all leaves the screen alone`() = runComposeUiTest {
        val flows = Flows()
        effects(flows, resolveImageFile = { _, _ -> File(dir, "never-written.jpg") })

        emit(flows.selectPicture, "folder-1" to 0)

        assertNull(presenter.selectedImagePath.value)
        assertEquals(Presenting.NONE, presenter.presentingMode.value)
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

        assertEquals(Presenting.BIBLE, presenter.presentingMode.value)
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

        emit(flows.selectSong, song)

        assertEquals(listOf(song), songsSelected)
        assertEquals(listOf(Tabs.SONGS), selectedTabs)
        assertEquals(1, songVersionBumps, "the row has to redraw or the selection is invisible")
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

    @Test
    fun `a remote command never opens a tab nobody asked for`() = runComposeUiTest {
        val flows = Flows()
        effects(flows)

        emit(flows.playPause, Unit)

        assertFalse(selectedTabs.isNotEmpty(), "transport is not a reason to move the operator's view")
    }
}
