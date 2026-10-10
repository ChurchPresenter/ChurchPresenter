package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.liveoutput.LiveSlide
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.slides.data.RecentPresentationFiles
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RemoteCommandOutcomesTest {

    private lateinit var dir: File
    private lateinit var pictures: PicturesViewModel
    private lateinit var presentations: PresentationViewModel
    private lateinit var bible: BibleViewModel
    private lateinit var presenter: PresenterManager
    private val tabs = mutableListOf<Tabs>()
    private var settings = AppSettings()

    private val loopToggle = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val selectSlide = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    private val selectPicture = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    private val upload = MutableSharedFlow<File>(extraBufferCapacity = 1)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-remote-outcomes").toFile()
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
    fun tearDown() {
        runCatching { pictures.dispose() }
        runCatching { bible.dispose() }
        dir.deleteRecursively()
    }

    private val png: ByteArray by lazy {
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }

    private fun images(vararg names: String): List<File> = names.map { File(dir, it).apply { writeBytes(png) } }

    private fun ComposeUiTest.compose() {
        setContent {
            RemoteCommandEffects(
                appSettings = settings,
                picturesViewModel = pictures,
                presentationViewModel = presentations,
                bibleViewModel = bible,
                presenterManager = presenter,
                resolveImageFile = null,
                onSettingsChange = { transform -> settings = transform(settings) },
                onSongItemSelected = {},
                onPictureItemSelected = {},
                onPresentationItemSelected = {},
                onMediaItemSelected = {},
                onSelectTab = { tabs += it },
                pushCurrentSlideIfLive = {},
                remotePresentationLoopToggleFlow = loopToggle,
                selectPictureImageFlow = selectPicture,
                selectSlideFlow = selectSlide,
                uploadPresentationFlow = upload,
            )
        }
        waitForIdle()
    }

    private fun <T> ComposeUiTest.emit(flow: MutableSharedFlow<T>, value: T) {
        waitUntil("the effect subscribed") { flow.subscriptionCount.value > 0 }
        runBlocking { flow.emit(value) }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitLoadSettled() {
        waitUntil("the deck load settled", timeoutMillis = 5_000) {
            presentations.loadError != null && !presentations.isLoading
        }
    }

    @Test
    fun `a second loop toggle puts looping back and the settings follow both times`() = runComposeUiTest {
        compose()
        val before = presentations.isLooping

        emit(loopToggle, Unit)
        assertEquals(!before, presentations.isLooping)
        assertEquals(!before, settings.presentationSettings.isLooping)

        emit(loopToggle, Unit)
        assertEquals(before, presentations.isLooping)
        assertEquals(before, settings.presentationSettings.isLooping)
    }

    @Test
    fun `a slide chosen on a deck mirrored from a primary is named on the live output`() = runComposeUiTest {
        val deckPath = File(dir, "Sunday.pptx").absolutePath
        presentations.loadPresentationFromRemote("item-1", deckPath, slideCount = 0) { null }
        awaitLoadSettled()
        presentations.slideFiles.addAll(images("s1.png", "s2.png"))
        compose()

        emit(selectSlide, "deck" to 1)
        waitUntil("the deck went live") { presenter.slideContent.value == Presenting.PRESENTATION }

        assertEquals(LiveSlide("Sunday.pptx", 1), presenter.liveSlide.value)
    }

    @Test
    fun `a deck uploaded from a phone is opened, remembered and brought forward`() = runComposeUiTest {
        val deck = File(dir, "uploaded.pptx").apply { writeText("not really a deck") }
        try {
            compose()

            emit(upload, deck)
            awaitLoadSettled()

            assertEquals(deck.absolutePath, presentations.selectedPresentation?.absolutePath)
            assertTrue(presentations.presentations.any { it.absolutePath == deck.absolutePath })
            assertEquals(deck.absolutePath, RecentPresentationFiles.files.first())
            assertEquals(listOf(Tabs.PRESENTATION), tabs)
        } finally {
            RecentPresentationFiles.files.remove(deck.absolutePath)
        }
    }

    @Test
    fun `a picture chosen without a resolver stages the one after it from the loaded folder`() =
        runComposeUiTest {
            val files = images("a.png", "b.png")
            pictures.loadImagesFromFolder(dir)
            compose()

            emit(selectPicture, "folder-1" to 0)

            assertEquals(files[0].absolutePath, presenter.selectedImagePath.value)
            assertEquals(files[1].absolutePath, presenter.nextImagePath.value)
            assertEquals(Presenting.PICTURES, presenter.slideContent.value)
        }
}
