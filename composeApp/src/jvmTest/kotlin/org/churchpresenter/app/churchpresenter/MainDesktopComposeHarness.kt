@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.remote.RemoteSongSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.flow.MutableSharedFlow
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.showLowerThird
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap

/**
 * The root composable on a headless test window, with every optional callback counted -- shared by
 * the suites that drive [MainDesktop] whole.
 */
abstract class MainDesktopComposeHarness {
    protected lateinit var dir: File

    @BeforeTest
    fun setUp() {
        // Both latches have to happen against the real user.home, before anything swaps it.
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        clearScheduleAutoSave()
        dir = Files.createTempDirectory("cp-main-desktop-compose").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
        clearScheduleAutoSave()
    }

    /**
     * The fork's home is shared with every other suite, and a fresh autosave left in it — by any
     * Schedule whose autosave loop outlived its test — makes the Schedule tab open on its restore
     * prompt, which takes every key and click these tests send. None of them is about that prompt.
     */
    private fun clearScheduleAutoSave() {
        File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").delete()
    }

    protected fun settings(): AppSettings =
        AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath))

    /** A folder of real images, so the picture paths do their work instead of exiting early. */
    protected fun pictureFolder(): File {
        val folder = File(dir, "Pictures").apply { mkdirs() }
        repeat(3) { i ->
            val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
            ImageIO.write(image, "png", File(folder, "image-$i.png"))
        }
        return folder
    }

    /** Puts one song in the library, so the paths that only run with something loaded are taken. */
    protected fun withOneSong(): AppSettings {
        val book = File(dir, "Hymnal").apply { mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(
                number = "1",
                title = "A Test Song",
                songbook = "Hymnal",
                lyrics = listOf("[Verse 1]", "first line", "second line"),
            ),
            File(book, "1 - A Test Song.song").absolutePath,
        )
        return settings()
    }

    /** Every optional callback the root can be given, so the paths that only run when one is wired are taken. */
    protected class Wiring {
        val songsLoaded = mutableListOf<Int>()
        val scenesChanged = mutableListOf<Int>()
        val scheduleChanged = mutableListOf<Int>()
        val picturesLoaded = mutableListOf<String>()
        val slidesLoaded = mutableListOf<String>()
        val tabChanges = mutableListOf<Int>()
        val quickPicked = mutableListOf<QuickBackground?>()
        val settingsChanges = mutableListOf<(AppSettings) -> AppSettings>()
        var developerUnlocks = 0
        val macrosRun = mutableListOf<Macro>()
    }

    /** Composes the root with [appSettings], then lets everything it launched settle. */
    protected fun root(
        appSettings: AppSettings = settings(),
        flows: Flows = Flows(),
        wiring: Wiring = Wiring(),
        presenterManager: PresenterManager = PresenterManager(),
        devMode: Boolean = true,
        media: MediaViewModel? = null,
        block: ComposeUiTest.(ScheduleActions) -> Unit = {},
    ) = runComposeUiTest {
        var actions = ScheduleActions()
        setContent {
            // The bindings, as MainWindow provides them from the settings.
            val shortcuts = ShortcutMap.from(appSettings.keyboardShortcutSettings)
            CompositionLocalProvider(LocalShortcuts provides shortcuts, LocalMediaViewModel provides media) {
            MaterialTheme {
                MainDesktop(
                    appSettings = appSettings,
                    onQuickBackgroundPicked = { wiring.quickPicked += it },
                    onSettingsChange = { wiring.settingsChanges += it },
                    onRequestDeveloperMenuUnlock = { wiring.developerUnlocks++ },
                    presenterManager = presenterManager,
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    // A test run is a dev build, so the dev mode only features are on unless a test turns them off.
                    live = LiveOutputCallbacks(
                        presenting = {},
                        onVerseSelected = {},
                        onSongItemSelected = {},
                        onRunMacro = { wiring.macrosRun += it },
                        devMode = devMode,
                    ),
                    publish = MainDesktopPublishers(
                        onScheduleActionsReady = { actions = it },
                        onSongsLoaded = { wiring.songsLoaded += it.size },
                        onScenesChanged = { wiring.scenesChanged += it.size },
                        onScheduleChanged = { wiring.scheduleChanged += it.size },
                        onPicturesLoaded = { id, _, _, _ -> wiring.picturesLoaded += id },
                        onPresentationSlidesLoaded = { id, _, _, _, _, _ -> wiring.slidesLoaded += id },
                        onTabChange = { wiring.tabChanges += it },
                    ),
                    flows = RemoteControlFlows(
                        selectPictureImageFlow = flows.selectPicture,
                        selectSlideFlow = flows.selectSlide,
                        nextPictureFlow = flows.nextPicture,
                        previousPictureFlow = flows.previousPicture,
                        nextSlideFlow = flows.nextSlide,
                        previousSlideFlow = flows.previousSlide,
                        remotePresentationPlayPauseFlow = flows.playPause,
                        remotePresentationLoopToggleFlow = flows.loopToggle,
                        remotePresentationGotoFlow = flows.goto,
                        selectBibleVerseFlow = flows.selectBibleVerse,
                        remoteSelectSongFlow = flows.remoteSelectSong,
                        remoteSelectPictureFlow = flows.remoteSelectPicture,
                        remoteSelectPresentationFlow = flows.remoteSelectPresentation,
                        uploadPresentationFlow = flows.uploadPresentation,
                    ),
                )
            }
            }
        }
        waitForIdle()
        block(actions)
    }

    /** The remote-command flows, so the collectors that wait on them are entered. */
    protected class Flows {
        val selectPicture = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 4)
        val selectSlide = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 4)
        val nextPicture = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val previousPicture = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val nextSlide = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val previousSlide = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val playPause = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val loopToggle = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
        val goto = MutableSharedFlow<Int>(extraBufferCapacity = 4)
        val selectBibleVerse = MutableSharedFlow<SelectBibleVerseRequest>(extraBufferCapacity = 4)
        val remoteSelectSong = MutableSharedFlow<RemoteSongSelection>(extraBufferCapacity = 4)
        val remoteSelectPicture = MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = 4)
        val remoteSelectPresentation = MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = 4)
        val uploadPresentation = MutableSharedFlow<File>(extraBufferCapacity = 4)
    }

    /** Settings that leave [tab] as the only visible one, so the root builds that branch. */
    protected fun showingOnly(tab: Tabs): AppSettings =
        settings().copy(hiddenTabs = Tabs.entries.filter { it != tab }.map { it.name }.toSet())

    protected fun ComposeUiTest.press(key: Key, ctrl: Boolean = false, shift: Boolean = false) {
        onAllNodes(isRoot())[0].performKeyInput {
            if (ctrl) keyDown(Key.CtrlLeft)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            if (ctrl) keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    /**
     * Takes the schedule row reading [label] live -- the row itself, not a tab's list that may show
     * the same name -- by selecting it and then double-clicking it. Selecting first lets the tab the
     * row opens settle: a double-click whose first click switched the tab could otherwise reach the
     * row as two single clicks.
     */
    protected fun ComposeUiTest.takeLive(label: String) {
        val row = onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
        row.performMouseInput { click() }
        waitForIdle()
        row.performMouseInput { doubleClick() }
        waitForIdle()
    }

    protected fun withPreviewMode(on: Boolean, take: KeyChord? = null) = withOneSong().let {
        it.copy(
            projectionSettings = it.projectionSettings.copy(previewModeEnabled = on),
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = if (take == null) emptyMap() else mapOf(ShortcutAction.TAKE.name to listOf(take)),
            ),
        )
    }

    protected fun cuedManager() = PresenterManager().apply {
        previewBus.setEnabled(true)
        previewBus.showLowerThird("{}", false, -1f, 0L, "Pastor")
    }
}
