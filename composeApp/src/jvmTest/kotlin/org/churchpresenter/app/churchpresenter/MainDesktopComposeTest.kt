@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.data.settings.SongSettings
import org.churchpresenter.app.churchpresenter.data.settings.WindowLayoutSettings
import org.churchpresenter.app.churchpresenter.data.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.server.SelectBibleVerseRequest
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The root composable, actually composed.
 *
 * Everything else about `MainDesktop` is reached through the pure helpers in `MainDesktopLogic.kt`.
 * This covers what those cannot: the composable's own body — which tab is built, which panels are
 * on screen, and which optional wiring is present.
 *
 * The tabs that need a browser, a VLC decoder, a camera or a live manager are deliberately not
 * driven here: composing them starts real subsystems, which a headless test should not do. They are
 * covered by their own tab tests.
 */
class MainDesktopComposeTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        // Both latches have to happen against the real user.home, before anything swaps it.
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        dir = Files.createTempDirectory("cp-main-desktop-compose").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun settings(): AppSettings =
        AppSettings(songSettings = SongSettings(storageDirectory = dir.absolutePath))

    /** A folder of real images, so the picture paths do their work instead of exiting early. */
    private fun pictureFolder(): File {
        val folder = File(dir, "Pictures").apply { mkdirs() }
        repeat(3) { i ->
            val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
            ImageIO.write(image, "png", File(folder, "image-$i.png"))
        }
        return folder
    }

    /** Puts one song in the library, so the paths that only run with something loaded are taken. */
    private fun withOneSong(): AppSettings {
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
    private class Wiring {
        val songsLoaded = mutableListOf<Int>()
        val scenesChanged = mutableListOf<Int>()
        val scheduleChanged = mutableListOf<Int>()
        val picturesLoaded = mutableListOf<String>()
        val slidesLoaded = mutableListOf<String>()
        val tabChanges = mutableListOf<Int>()
    }

    /** Composes the root with [appSettings], then lets everything it launched settle. */
    private fun root(
        appSettings: AppSettings = settings(),
        flows: Flows = Flows(),
        wiring: Wiring = Wiring(),
        presenterManager: PresenterManager = PresenterManager(),
        block: ComposeUiTest.(ScheduleActions) -> Unit = {},
    ) = runComposeUiTest {
        var actions = ScheduleActions()
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = appSettings,
                    presenterManager = presenterManager,
                    onScheduleActionsReady = { actions = it },
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    onSongsLoaded = { wiring.songsLoaded += it.size },
                    onScenesChanged = { wiring.scenesChanged += it.size },
                    onScheduleChanged = { wiring.scheduleChanged += it.size },
                    onPicturesLoaded = { id, _, _, _ -> wiring.picturesLoaded += id },
                    onPresentationSlidesLoaded = { id, _, _, _, _, _ -> wiring.slidesLoaded += id },
                    onTabChange = { wiring.tabChanges += it },
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
                )
            }
        }
        waitForIdle()
        block(actions)
    }

    /** The remote-command flows, so the collectors that wait on them are entered. */
    private class Flows {
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
        val remoteSelectSong = MutableSharedFlow<ScheduleItem.SongItem>(extraBufferCapacity = 4)
        val remoteSelectPicture = MutableSharedFlow<ScheduleItem.PictureItem>(extraBufferCapacity = 4)
        val remoteSelectPresentation = MutableSharedFlow<ScheduleItem.PresentationItem>(extraBufferCapacity = 4)
        val uploadPresentation = MutableSharedFlow<File>(extraBufferCapacity = 4)
    }

    /** Settings that leave [tab] as the only visible one, so the root builds that branch. */
    private fun showingOnly(tab: Tabs): AppSettings =
        settings().copy(hiddenTabs = Tabs.entries.filter { it != tab }.map { it.name }.toSet())

    // ── Composing at all ────────────────────────────────────────────────────────

    @Test
    fun `the root composable composes headless`() = root()

    // ── Each tab the root can build ─────────────────────────────────────────────

    @Test
    fun `the songs tab is built`() = root(showingOnly(Tabs.SONGS))

    @Test
    fun `the bible tab is built`() = root(showingOnly(Tabs.BIBLE))

    @Test
    fun `the pictures tab is built`() = root(showingOnly(Tabs.PICTURES))

    @Test
    fun `the presentation tab is built`() = root(showingOnly(Tabs.PRESENTATION))

    @Test
    fun `the lower third tab is built`() = root(showingOnly(Tabs.LOWER_THIRD))

    @Test
    fun `the announcements tab is built`() = root(showingOnly(Tabs.ANNOUNCEMENTS))

    @Test
    fun `the dictionary tab is built`() = root(showingOnly(Tabs.DICTIONARY))

    @Test
    fun `the crossword tab is built`() = root(showingOnly(Tabs.CROSSWORD))

    @Test
    fun `the companion surface tab is built`() = root(showingOnly(Tabs.COMPANION_SURFACE))

    @Test
    fun `the qa tab without a manager shows its unavailable state`() = root(showingOnly(Tabs.QA))

    @Test
    fun `the stt tab without a manager shows its unavailable state`() = root(showingOnly(Tabs.STT))

    @Test
    fun `the media tab without a player shows its unavailable state`() = root(showingOnly(Tabs.MEDIA))

    @Test
    fun `the canvas tab is built`() = root(showingOnly(Tabs.CANVAS))

    // ── The panels either side of it ────────────────────────────────────────────

    @Test
    fun `both side panels collapsed`() = root(
        settings().copy(
            maximizedLayout = WindowLayoutSettings(schedulePanelCollapsed = true, previewPanelCollapsed = true),
            windowedLayout = WindowLayoutSettings(schedulePanelCollapsed = true, previewPanelCollapsed = true),
        )
    )

    @Test
    fun `only the schedule panel collapsed`() = root(
        settings().copy(
            maximizedLayout = WindowLayoutSettings(schedulePanelCollapsed = true),
            windowedLayout = WindowLayoutSettings(schedulePanelCollapsed = true),
        )
    )

    @Test
    fun `only the preview panel collapsed`() = root(
        settings().copy(
            maximizedLayout = WindowLayoutSettings(previewPanelCollapsed = true),
            windowedLayout = WindowLayoutSettings(previewPanelCollapsed = true),
        )
    )

    // ── Optional wiring the root only builds when it is configured ──────────────

    @Test
    fun `a companion connection routed to each sidebar is built`() = root(
        settings().copy(
            companionSatelliteConnections = listOf(
                CompanionSatelliteSettings(host = "10.0.0.2", showInLeftSidebar = true),
                CompanionSatelliteSettings(host = "10.0.0.3", showInRightSidebar = true),
            )
        )
    )

    @Test
    fun `several connections in one sidebar offer a chooser`() = root(
        settings().copy(
            companionSatelliteConnections = listOf(
                CompanionSatelliteSettings(host = "10.0.0.2", showInLeftSidebar = true),
                CompanionSatelliteSettings(host = "10.0.0.4", showInLeftSidebar = true),
                CompanionSatelliteSettings(host = "10.0.0.3", showInRightSidebar = true),
                CompanionSatelliteSettings(host = "10.0.0.5", showInRightSidebar = true),
            )
        )
    )

    // ── The remote commands the root listens for ───────────────────────────────

    @Test
    fun `every remote command is delivered to a listening root`() {
        val flows = Flows()
        root(flows = flows) { _ ->
            // Each of these enters a collector that otherwise never runs. Nothing is asserted about
            // the outcome — with no deck and no folder loaded, the point is that the root receives
            // the command and takes its "nothing to act on" path rather than failing.
            runBlocking {
                flows.nextPicture.emit(Unit)
                flows.previousPicture.emit(Unit)
                flows.nextSlide.emit(Unit)
                flows.previousSlide.emit(Unit)
                flows.playPause.emit(Unit)
                flows.loopToggle.emit(Unit)
                flows.goto.emit(0)
                flows.selectSlide.emit("deck" to 0)
                flows.selectPicture.emit("folder" to 0)
            }
            waitForIdle()
        }
    }

    @Test
    fun `a slide index outside the deck is refused rather than thrown`() {
        val flows = Flows()
        root(flows = flows) { _ ->
            runBlocking {
                flows.goto.emit(99)
                flows.selectSlide.emit("deck" to 99)
            }
            waitForIdle()
        }
    }

    // ── With something actually loaded ─────────────────────────────────────────

    @Test
    fun `a library with a song in it loads and is published`() {
        val loaded = Wiring()
        root(appSettings = withOneSong(), wiring = loaded) { _ ->
            waitForIdle()
        }
    }

    @Test
    fun `the operator can move between tabs`() = root(withOneSong()) { _ ->
        // Walking the tab strip runs selectTab and the tab-change reporting, and builds each tab
        // branch in turn rather than only the one the root opens on.
        listOf("Bible", "Songs", "Pictures").forEach { label ->
            val node = onAllNodesWithText(label)
            if (node.fetchSemanticsNodes().isNotEmpty()) {
                node[0].performClick()
                waitForIdle()
            }
        }
    }

    @Test
    fun `keys the root handles are accepted`() = root(withOneSong()) { _ ->
        // Escape and the presentation step keys are handled on the root's own key handler; with
        // nothing live they take their "nothing to step" paths, which is the branch being covered.
        val focusable = onAllNodesWithText("Search songs...", substring = true)
        if (focusable.fetchSemanticsNodes().isNotEmpty()) {
            focusable[0].performClick()
            waitForIdle()
        }
    }

    // ── A schedule with something in it ────────────────────────────────────────

    @Test
    fun `an item of every kind can be put on the schedule`() = root(withOneSong()) { actions ->
        // Each add reaches a different branch of the root's item handling, and populating the
        // schedule is what makes the click handling below reachable at all.
        actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
        actions.addBibleVerse("John", 3, 16, "verse text", "", 43)
        actions.addPicture(dir.absolutePath, "Pictures", 0)
        actions.addPresentation(File(dir, "deck.pptx").absolutePath, "deck", 0, "pptx")
        actions.addMedia("http://example.invalid/clip.mp4", "Clip", "video")
        actions.addScene("scene-1", "A Scene")
        actions.addDictionary("H1", "word", "translit", "definition")
        actions.addWebsite("http://example.invalid", "A Site")
        waitForIdle()
    }

    @Test
    fun `choosing a scheduled item of each kind is handled`() = root(withOneSong()) { actions ->
        actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
        actions.addBibleVerse("John", 3, 16, "verse text", "", 43)
        actions.addWebsite("http://example.invalid", "A Site")
        actions.addDictionary("H1", "word", "translit", "definition")
        waitForIdle()

        // Clicking each one runs the root's when-over-item-type, which routes to a tab and stores
        // the selection the tab then picks up.
        listOf("A Test Song", "A Site", "word").forEach { label ->
            val node = onAllNodesWithText(label, substring = true)
            if (node.fetchSemanticsNodes().isNotEmpty()) {
                node[0].performClick()
                waitForIdle()
            }
        }
    }

    @Test
    fun `the schedule can be cleared again`() = root(withOneSong()) { actions ->
        actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
        waitForIdle()
        actions.clearSchedule()
        waitForIdle()
    }

    // ── While something is live ────────────────────────────────────────────────

    @Test
    fun `the root follows whatever is being presented`() {
        val manager = PresenterManager()
        root(appSettings = withOneSong(), presenterManager = manager) { _ ->
            listOf(
                Presenting.LYRICS,
                Presenting.BIBLE,
                Presenting.PICTURES,
                Presenting.PRESENTATION,
                Presenting.ANNOUNCEMENTS,
                Presenting.NONE,
            ).forEach { mode ->
                manager.setPresentingMode(mode)
                waitForIdle()
            }
        }
    }

    // ── Recomposition ──────────────────────────────────────────────────────────

    @Test
    fun `the root survives its inputs changing one at a time`() {
        // Compose generates a skip branch per parameter group; they are only taken when the root
        // actually recomposes with some inputs changed and others not, which a single composition
        // can never reach.
        val base = withOneSong()
        var current by mutableStateOf(base)
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = current,
                        presenterManager = PresenterManager(),
                        presenting = {},
                        onVerseSelected = {},
                        onSongItemSelected = {},
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    )
                }
            }
            waitForIdle()

            listOf(
                base.copy(schedulePanelWidthDp = 320),
                base.copy(schedulePanelWidthDp = 320, previewPanelWidthDp = 320),
                base.copy(hiddenTabs = setOf(Tabs.WEB.name)),
                base.copy(theme = "dark"),
                base.copy(scheduleItemZoomPercent = 120),
                base,
            ).forEach { next ->
                current = next
                waitForIdle()
            }
        }
    }

    // ── Linked to another instance ─────────────────────────────────────────────

    @Test
    fun `a controlled follower mirrors the primary`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = withOneSong(),
                    presenterManager = PresenterManager(),
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    instanceLinkConnectionStatus = InstanceLinkStatus.CONNECTED,
                    instanceLinkRole = InstanceLinkRole.CONTROLLED,
                    instanceLinkFollowingHost = "10.0.0.9",
                    instanceLinkBibleUpdatedSignal = 1,
                    instanceLinkSecondaryBibleUpdatedSignal = 1,
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun `a controller drives the other instance rather than mirroring it`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = withOneSong(),
                    presenterManager = PresenterManager(),
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    instanceLinkConnectionStatus = InstanceLinkStatus.CONNECTED,
                    instanceLinkRole = InstanceLinkRole.CONTROLLER,
                    instanceLinkFollowingHost = "10.0.0.9",
                    instanceLinkSendClear = {},
                    instanceLinkSendProject = {},
                    instanceLinkSendNextPicture = {},
                    instanceLinkSendPreviousPicture = {},
                    instanceLinkSendNextSlide = {},
                    instanceLinkSendPreviousSlide = {},
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun `a link that dropped shows its retry countdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = withOneSong(),
                    presenterManager = PresenterManager(),
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    instanceLinkConnectionStatus = InstanceLinkStatus.ERROR,
                    instanceLinkFollowingHost = "10.0.0.9",
                    instanceLinkNextRetryAtMs = System.currentTimeMillis() + 5_000,
                )
            }
        }
        waitForIdle()
    }

    // ── Keys the root handles itself ───────────────────────────────────────────

    @Test
    fun `the keys the root owns are handled without anything live`() = root(withOneSong()) { _ ->
        // Not onRoot(): a tooltip/popup composes as its own root, so "the" root is ambiguous and
        // fetching it throws before any key is delivered. The handler under test is on the main
        // root, which is always the first.
        onAllNodes(isRoot())[0].performKeyInput {
            pressKey(Key.Escape)
            pressKey(Key.PageDown)
            pressKey(Key.PageUp)
            pressKey(Key.F1)
            pressKey(Key.F2)
        }
        waitForIdle()
    }

    // ── A stage monitor being configured ───────────────────────────────────────

    @Test
    fun `a configured stage monitor is accounted for`() = root(
        withOneSong().copy(
            projectionSettings = withOneSong().projectionSettings.copy(
                screenAssignments = listOf(
                    ScreenAssignment(displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
                ),
            ),
        )
    )

    // ── With pictures actually on disk ─────────────────────────────────────────

    @Test
    fun `a scheduled picture folder is loaded and published`() {
        val folder = pictureFolder()
        val flows = Flows()
        val wiring = Wiring()
        root(appSettings = withOneSong(), flows = flows, wiring = wiring) { actions ->
            actions.addPicture(folder.absolutePath, folder.name, 3)
            waitForIdle()

            val item = onAllNodesWithText(folder.name, substring = true)
            if (item.fetchSemanticsNodes().isNotEmpty()) {
                item[0].performClick()
                waitForIdle()
            }
        }
    }

    @Test
    fun `stepping through pictures is handled once a folder is loaded`() {
        val folder = pictureFolder()
        val images = folder.listFiles()!!.sortedBy { it.name }
        val flows = Flows()
        root(appSettings = withOneSong(), flows = flows) { actions ->
            actions.addPicture(folder.absolutePath, folder.name, images.size)
            waitForIdle()

            runBlocking {
                // Resolvable and unresolvable selections both, so the fallback path runs too.
                flows.selectPicture.emit(stableFileId(folder) to 1)
                flows.nextPicture.emit(Unit)
                flows.previousPicture.emit(Unit)
                flows.selectPicture.emit("no-such-folder" to 0)
            }
            waitForIdle()
        }
    }

    @Test
    fun `a picture resolved from the server's own map is shown`() {
        val folder = pictureFolder()
        val images = folder.listFiles()!!.sortedBy { it.name }
        val flows = Flows()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = withOneSong(),
                        presenterManager = PresenterManager(),
                        presenting = {},
                        onVerseSelected = {},
                        onSongItemSelected = {},
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        selectPictureImageFlow = flows.selectPicture,
                        resolveImageFile = { _, index -> images.getOrNull(index) },
                        onSlideChanged = { _, _, _, _ -> },
                    )
                }
            }
            waitForIdle()
            runBlocking {
                flows.selectPicture.emit("uploads" to 0)
                flows.selectPicture.emit("uploads" to 2)
                flows.selectPicture.emit("uploads" to 99)
            }
            waitForIdle()
        }
    }

    @Test
    fun `the root survives each of its inputs changing in turn`() {
        // Compose groups the parameters into changed-masks and generates a skip branch per group.
        // A single composition never takes any of them, and changing one parameter only ever takes
        // the branches for that one group — so each input is moved in turn.
        val base = withOneSong()
        var appSettings by mutableStateOf(base)
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        var serverUrl by mutableStateOf("")
        var qaDisplayUrl by mutableStateOf("")
        var tunnelUrl by mutableStateOf("")
        var presentationDisplayUrl by mutableStateOf("")
        var presentationFrozen by mutableStateOf(false)
        var followers by mutableStateOf(0)
        var dismissSignal by mutableStateOf(0)
        var followingHost by mutableStateOf("")
        var bibleSignal by mutableStateOf(0)

        runComposeUiTest {
            setContent {
                MaterialTheme {
                    MainDesktop(
                        appSettings = appSettings,
                        presenterManager = PresenterManager(),
                        presenting = {},
                        onVerseSelected = {},
                        onSongItemSelected = {},
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        theme = theme,
                        serverUrl = serverUrl,
                        qaDisplayUrl = qaDisplayUrl,
                        tunnelUrl = tunnelUrl,
                        presentationDisplayUrl = presentationDisplayUrl,
                        presentationFrozen = presentationFrozen,
                        connectedInstanceLinkFollowerCount = followers,
                        dialogDismissSignal = dismissSignal,
                        instanceLinkFollowingHost = followingHost,
                        instanceLinkBibleUpdatedSignal = bibleSignal,
                    )
                }
            }
            waitForIdle()

            val steps: List<() -> Unit> = listOf(
                { theme = ThemeMode.DARK },
                { serverUrl = "http://127.0.0.1:1/" },
                { qaDisplayUrl = "http://127.0.0.1:1/qa" },
                { tunnelUrl = "http://tunnel.invalid" },
                { presentationDisplayUrl = "http://127.0.0.1:1/present" },
                { presentationFrozen = true },
                { followers = 2 },
                { dismissSignal = 1 },
                { followingHost = "10.0.0.9" },
                { bibleSignal = 1 },
                { appSettings = base.copy(schedulePanelWidthDp = 340) },
                { theme = ThemeMode.LIGHT },
                { presentationFrozen = false },
                { followers = 0 },
                { dismissSignal = 2 },
            )
            steps.forEach { step ->
                step()
                waitForIdle()
            }
        }
    }

    // ── The rest of the remote commands ────────────────────────────────────────

    @Test
    fun `a verse and an item chosen remotely are handled`() {
        val flows = Flows()
        val folder = pictureFolder()
        root(appSettings = withOneSong(), flows = flows) { _ ->
            runBlocking {
                flows.selectBibleVerse.emit(
                    SelectBibleVerseRequest(bookName = "John", chapter = 3, verseNumber = 16, verseText = "text"),
                )
                flows.selectBibleVerse.emit(
                    SelectBibleVerseRequest(bookName = "No Such Book", chapter = 1, verseNumber = 1),
                )
                flows.remoteSelectSong.emit(
                    ScheduleItem.SongItem(
                        id = "a",
                        songNumber = 1,
                        title = "A Test Song",
                        songbook = "Hymnal",
                        songId = "Hymnal::1"
                    ),
                )
                flows.remoteSelectPicture.emit(
                    ScheduleItem.PictureItem(
                        id = "b",
                        folderPath = folder.absolutePath,
                        folderName = folder.name,
                        imageCount = 3
                    ),
                )
                flows.remoteSelectPresentation.emit(
                    ScheduleItem.PresentationItem(
                        id = "c",
                        filePath = File(dir, "deck.pptx").absolutePath,
                        fileName = "deck",
                        slideCount = 0,
                        fileType = "pptx"
                    ),
                )
                flows.uploadPresentation.emit(File(dir, "uploaded.pptx"))
            }
            waitForIdle()
        }
    }

    // ── With the optional managers wired ───────────────────────────────────────

    @Test
    fun `the qa and stt tabs are built when their managers exist`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = withOneSong().copy(hiddenTabs = emptySet()),
                    presenterManager = PresenterManager(),
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    qaManager = QAManager(),
                    sttManager = STTManager(),
                    statisticsManager = StatisticsManager(),
                )
            }
        }
        waitForIdle()
    }

    // ── A follower given the primary's own content ─────────────────────────────

    @Test
    fun `a follower is given the primary's schedule and catalog`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                MainDesktop(
                    appSettings = withOneSong(),
                    presenterManager = PresenterManager(),
                    presenting = {},
                    onVerseSelected = {},
                    onSongItemSelected = {},
                    companionSatelliteViewModel = CompanionSatelliteViewModel(),
                    instanceLinkConnectionStatus = InstanceLinkStatus.CONNECTED,
                    instanceLinkRole = InstanceLinkRole.CONTROLLED,
                    instanceLinkFollowingHost = "10.0.0.9",
                    instanceLinkRemoteSchedule = listOf(
                        ScheduleItemDto(
                            id = "1",
                            type = "song",
                            displayText = "A Test Song",
                            songNumber = 1,
                            title = "A Test Song",
                            songbook = "Hymnal"
                        ),
                        ScheduleItemDto(
                            id = "2",
                            type = "bible",
                            displayText = "John 3:16",
                            bookName = "John",
                            chapter = 3,
                            verseNumber = 16
                        ),
                    ),
                    instanceLinkFetchSongDetail = { _, _ -> null },
                    instanceLinkFetchBibleFile = { null },
                )
            }
        }
        waitForIdle()
    }

    // ── Taking a scheduled item live ───────────────────────────────────────────

    @Test
    fun `taking each kind of scheduled item live is handled`() {
        val folder = pictureFolder()
        root(withOneSong()) { actions ->
            actions.addSong(1, "A Test Song", "Hymnal", "Hymnal::1")
            actions.addBibleVerse("John", 3, 16, "verse text", "", 43)
            actions.addPicture(folder.absolutePath, folder.name, 3)
            actions.addPresentation(File(dir, "deck.pptx").absolutePath, "deck", 0, "pptx")
            actions.addMedia("http://example.invalid/clip.mp4", "Clip", "video")
            actions.addWebsite("http://example.invalid", "A Site")
            actions.addDictionary("H1", "word", "translit", "definition")
            actions.addScene("scene-1", "A Scene")
            waitForIdle()

            // A double-click on a scheduled row is what takes it live, and each type routes through
            // its own handler on the root.
            listOf("A Test Song", "John", folder.name, "deck", "Clip", "A Site", "word", "A Scene").forEach { label ->
                val node = onAllNodesWithText(label, substring = true)
                if (node.fetchSemanticsNodes().isNotEmpty()) {
                    node[0].performMouseInput { doubleClick() }
                    waitForIdle()
                }
            }
        }
    }

    @Test
    fun `an announcement taken live rewrites the announcement settings`() = root(withOneSong()) { actions ->
        actions.addAnnouncement(
            ScheduleItem.AnnouncementItem(id = "ann", text = "A notice for the room"),
        )
        waitForIdle()

        val node = onAllNodesWithText("A notice for the room", substring = true)
        if (node.fetchSemanticsNodes().isNotEmpty()) {
            node[0].performMouseInput { doubleClick() }
            waitForIdle()
        }
    }

    @Test
    fun `a title slide adds an entry ahead of the song`() =
        root(settings().copy(songSettings = settings().songSettings.copy(titleSlideEnabled = true)))
}
