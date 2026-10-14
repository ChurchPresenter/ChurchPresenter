package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import org.churchpresenter.statistics.StatisticsManager
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
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
class RemoteCommandFlowLifecycleTest {

    private lateinit var dir: File
    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var pictures: PicturesViewModel
    private lateinit var presentations: PresentationViewModel
    private lateinit var bible: BibleViewModel
    private lateinit var presenter: PresenterManager
    private val tabs = mutableListOf<Tabs>()
    private var pushes = 0
    private var settings = AppSettings()
    private var lastGeneration = -1

    @BeforeTest
    fun setUp() {
        TestSingletons.latchToTestHome()
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-remote-branches-home").toFile()
        System.setProperty("user.home", home.absolutePath)
        dir = Files.createTempDirectory("cp-remote-branches").toFile()
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
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
        dir.deleteRecursively()
    }

    private val png: ByteArray by lazy {
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()
    }

    private fun imagesIn(folder: File, vararg names: String): List<File> {
        folder.mkdirs()
        return names.map { File(folder, it).apply { writeBytes(png) } }
    }

    private class Wired(
        val playPause: Flow<Unit>? = null,
        val loop: Flow<Unit>? = null,
        val goto: Flow<Int>? = null,
        val selectPicture: Flow<Pair<String, Int>>? = null,
        val nextPicture: Flow<Unit>? = null,
        val previousPicture: Flow<Unit>? = null,
        val nextSlide: Flow<Unit>? = null,
        val previousSlide: Flow<Unit>? = null,
        val selectSlide: Flow<Pair<String, Int>>? = null,
        val verse: Flow<SelectBibleVerseRequest>? = null,
        val song: Flow<RemoteSongSelection>? = null,
        val picture: Flow<ScheduleItem.PictureItem>? = null,
        val presentation: Flow<ScheduleItem.PresentationItem>? = null,
        val media: Flow<ScheduleItem.MediaItem>? = null,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.compose(
        wired: () -> Wired,
        resolve: ((String, Int) -> File?)? = null,
        statistics: StatisticsManager? = null,
        generation: () -> Int = { 0 },
    ) {
        setContent {
            val w = wired()
            val gen = generation()
            RemoteCommandEffects(
                appSettings = settings,
                picturesViewModel = pictures,
                presentationViewModel = presentations,
                bibleViewModel = bible,
                presenterManager = presenter,
                resolveImageFile = resolve?.let { r -> { id, i -> r(id, i).also { lastGeneration = gen } } },
                onSettingsChange = { transform ->
                    settings = transform(settings)
                    lastGeneration = gen
                },
                onSongItemSelected = { lastGeneration = gen },
                onPictureItemSelected = {},
                onPresentationItemSelected = {},
                onMediaItemSelected = {},
                onSelectTab = { tabs += it },
                pushCurrentSlideIfLive = { yield(); pushes++ },
                remotePresentationPlayPauseFlow = w.playPause,
                remotePresentationLoopToggleFlow = w.loop,
                remotePresentationGotoFlow = w.goto,
                selectPictureImageFlow = w.selectPicture,
                nextPictureFlow = w.nextPicture,
                previousPictureFlow = w.previousPicture,
                nextSlideFlow = w.nextSlide,
                previousSlideFlow = w.previousSlide,
                selectSlideFlow = w.selectSlide,
                selectBibleVerseFlow = w.verse,
                remoteSelectSongFlow = w.song,
                remoteSelectPictureFlow = w.picture,
                remoteSelectPresentationFlow = w.presentation,
                remoteSelectMediaFlow = w.media,
                statisticsManager = statistics,
            )
        }
        waitForIdle()
    }

    @Test
    fun `flows that finish after their values leave every command handled`() = runComposeUiTest {
        val wired = Wired(
            playPause = flowOf(Unit, Unit),
            loop = flowOf(Unit),
            goto = flowOf(-1, 99),
            nextPicture = flowOf(Unit),
            previousPicture = flowOf(Unit),
            nextSlide = flowOf(Unit, Unit),
            previousSlide = flowOf(Unit),
            selectSlide = flowOf("deck" to 3),
            song = flowOf(
                RemoteSongSelection(
                    ScheduleItem.SongItem(id = "s", songNumber = 1, title = "A", songbook = "B"),
                    goLive = false,
                    source = "remote",
                ),
            ),
            picture = flowOf(ScheduleItem.PictureItem(id = "p", folderPath = "/x", folderName = "x", imageCount = 0)),
            presentation = flowOf(
                ScheduleItem.PresentationItem(
                    id = "d", filePath = "/d", fileName = "d", slideCount = 0, fileType = "pdf",
                ),
            ),
            media = flowOf(ScheduleItem.MediaItem(id = "m", mediaUrl = "u", mediaTitle = "t", mediaType = "local")),
        )
        compose({ wired })

        assertEquals(3, pushes, "both next slides and the previous one asked for a push, after suspending")
        assertEquals(listOf(Tabs.SONGS, Tabs.PICTURES, Tabs.PRESENTATION, Tabs.MEDIA), tabs)
        assertEquals(presentations.isLooping, settings.presentationSettings.isLooping)
    }

    @Test
    fun `swapping and dropping the flows cancels the old collectors`() = runComposeUiTest {
        val first = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val second = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var current by mutableStateOf<Wired>(Wired(nextSlide = first, previousSlide = first, playPause = first))
        compose({ current })
        waitUntil { first.subscriptionCount.value == 3 }

        current = Wired(nextSlide = second, previousSlide = emptyFlow(), playPause = null)
        waitForIdle()
        waitUntil { first.subscriptionCount.value == 0 && second.subscriptionCount.value == 1 }

        assertTrue(first.tryEmit(Unit))
        assertTrue(second.tryEmit(Unit))
        waitForIdle()
        waitUntil { pushes == 1 }
        assertEquals(1, pushes, "only the new flow's command is handled")
    }

    @Test
    fun `a picture from another folder loads that folder and goes live`() = runComposeUiTest {
        val other = imagesIn(File(dir, "uploads"), "a.png", "b.png")
        imagesIn(File(dir, "loaded"), "x.png")
        pictures.selectFolder(File(dir, "loaded"))
        compose({ Wired(selectPicture = flowOf("elsewhere" to 1)) }, resolve = { _, i -> other[i] })

        assertEquals(other[1].absolutePath, presenter.selectedImagePath.value)
        assertEquals(Presenting.PICTURES, presenter.slideContent.value)
        assertEquals(File(dir, "uploads").absolutePath, pictures.selectedFolder?.absolutePath)
    }

    @Test
    fun `a resolved picture past the folder's end still goes live without moving the selection`() =
        runComposeUiTest {
            val loaded = imagesIn(File(dir, "loaded"), "x.png")
            pictures.selectFolder(File(dir, "loaded"))
            val folderId = stableFileId(File(dir, "loaded"))
            compose({ Wired(selectPicture = flowOf(folderId to 5)) }, resolve = { _, _ -> loaded[0] })

            assertEquals(loaded[0].absolutePath, presenter.selectedImagePath.value)
            assertEquals(0, pictures.selectedImageIndex)
        }

    @Test
    fun `an unresolved picture past the loaded folder leaves the output alone`() = runComposeUiTest {
        imagesIn(File(dir, "loaded"), "x.png")
        pictures.selectFolder(File(dir, "loaded"))
        compose({ Wired(selectPicture = flowOf("any" to 4)) }, resolve = { _, _ -> null })

        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }

    @Test
    fun `a remote verse is counted for the statistics once per verse shown`() = runComposeUiTest {
        val statistics = StatisticsManager()
        compose(
            {
                Wired(
                    verse = flowOf(
                        SelectBibleVerseRequest(
                            bookName = "John", chapter = 3, verseNumber = 16, verseText = "For God",
                        ),
                    ),
                )
            },
            statistics = statistics,
        )

        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
        assertTrue(statistics.getAllVersesInRange(0L, Long.MAX_VALUE).isNotEmpty())
    }

    @Test
    fun `a remote verse with no statistics manager still goes live`() = runComposeUiTest {
        compose({ Wired(verse = flowOf(SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = 16))) })

        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
    }

    @Test
    fun `flows that suspend before they finish are still handled`() = runComposeUiTest {
        fun <T> later(vararg values: T): Flow<T> = flow {
            for (v in values) {
                yield()
                emit(v)
            }
        }
        val wired = Wired(
            playPause = later(Unit),
            loop = later(Unit),
            goto = later(99),
            selectPicture = later("none" to -1),
            nextPicture = later(Unit),
            previousPicture = later(Unit),
            nextSlide = later(Unit),
            previousSlide = later(Unit),
            selectSlide = later("deck" to 3),
            verse = later(SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = 16)),
            song = later(
                RemoteSongSelection(
                    ScheduleItem.SongItem(id = "s", songNumber = 1, title = "A", songbook = "B"),
                    goLive = false,
                    source = "remote",
                ),
            ),
            picture = later(ScheduleItem.PictureItem(id = "p", folderPath = "/x", folderName = "x", imageCount = 0)),
            presentation = later(
                ScheduleItem.PresentationItem(
                    id = "d", filePath = "/d", fileName = "d", slideCount = 0, fileType = "pdf",
                ),
            ),
            media = later(ScheduleItem.MediaItem(id = "m", mediaUrl = "u", mediaTitle = "t", mediaType = "local")),
        )
        compose({ wired }, resolve = { _, _ -> null })
        waitUntil { tabs.size == 4 && pushes == 2 }

        assertEquals(listOf(Tabs.SONGS, Tabs.PICTURES, Tabs.PRESENTATION, Tabs.MEDIA), tabs)
        assertEquals(Presenting.BIBLE, presenter.slideContent.value)
        assertEquals(presentations.isLooping, settings.presentationSettings.isLooping)
    }

    @Test
    fun `new callbacks with the same flows keep every collector listening`() = runComposeUiTest {
        val unit = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val loop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val song = MutableSharedFlow<RemoteSongSelection>(extraBufferCapacity = 1)
        val picture = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
        val wired = Wired(
            playPause = unit,
            loop = loop,
            goto = MutableSharedFlow(),
            selectPicture = picture,
            nextPicture = unit,
            previousPicture = unit,
            nextSlide = unit,
            previousSlide = unit,
            selectSlide = MutableSharedFlow(),
            song = song,
            picture = MutableSharedFlow(),
            presentation = MutableSharedFlow(),
            media = MutableSharedFlow(),
        )
        var generation by mutableStateOf(0)
        compose({ wired }, resolve = { _, _ -> null }, generation = { generation })
        waitUntil { loop.subscriptionCount.value == 1 && unit.subscriptionCount.value == 5 }

        generation = 1
        waitForIdle()

        assertEquals(5, unit.subscriptionCount.value, "the collectors outlive the recomposition")
        assertTrue(loop.tryEmit(Unit))
        waitUntil { lastGeneration != -1 }
        assertEquals(presentations.isLooping, settings.presentationSettings.isLooping)
        lastGeneration = -1
        val selection = RemoteSongSelection(
            ScheduleItem.SongItem(id = "s", songNumber = 1, title = "A", songbook = "B"),
            goLive = false,
            source = "remote",
        )
        assertTrue(song.tryEmit(selection))
        waitUntil { lastGeneration != -1 }
        lastGeneration = -1
        assertTrue(picture.tryEmit("any" to 0))
        waitUntil { lastGeneration != -1 }
        assertEquals(Tabs.SONGS, tabs.single())
    }
}
