@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.LiveOutputCallbacks
import org.churchpresenter.app.churchpresenter.MainDesktop
import org.churchpresenter.app.churchpresenter.MainDesktopPublishers
import org.churchpresenter.app.churchpresenter.ScheduleActions
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.WebAccessState
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.liveoutput.OffscreenOutputContent
import org.churchpresenter.liveoutput.OffscreenOutputContext
import org.churchpresenter.liveoutput.OffscreenOutputKind
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterTransitionEffects
import org.churchpresenter.qa.QAManager
import org.churchpresenter.stt.STTManager
import org.churchpresenter.schedule.SCHEDULE_ROW_CARD_TAG
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

private const val OUTPUT_TAG = "happy-path-output"
private const val WAIT_MS = 5_000L
private const val DOUBLE_CLICK_WINDOW_MS = 500L

/** How far above or below a row's title its controls may sit. */
private const val ROW_SLACK = 30f

/**
 * The main window with the app's own output wiring, for the happy-path suites: a real
 * [PresenterManager] fed through the Preview bus as `MainWindowChrome` feeds it, its transition
 * effects running as the output windows run them, and the output itself composed on demand over the
 * window so a test can read what the screen draws.
 */
abstract class HappyPathHarness {
    protected lateinit var dir: File

    @BeforeTest
    fun setUpHappyPath() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        clearScheduleAutoSave()
        dir = Files.createTempDirectory("cp-happy-path").toFile()
    }

    @AfterTest
    fun tearDownHappyPath() {
        dir.deleteRecursively()
        clearScheduleAutoSave()
    }

    private fun clearScheduleAutoSave() {
        File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").delete()
    }

    /** A song on disk under [songbook], one section per entry of [sections] (header to its line). */
    protected fun song(number: Int, title: String, sections: List<Pair<String, String>>, songbook: String = "Hymnal") {
        val book = File(songsDir(), songbook).apply { mkdirs() }
        SongFileParser().writeSongFile(
            SongItem(
                number = "$number",
                title = title,
                songbook = songbook,
                lyrics = sections.flatMap { (header, line) -> listOf("[$header]", line) },
            ),
            File(book, "$number - $title.song").absolutePath,
        )
    }

    private fun songsDir() = File(dir, "songs").apply { mkdirs() }

    /** A Bible module of [content] (an `SpbFixture.buildContent` text), written as [name]. */
    protected fun bible(name: String, content: String) {
        SpbFixture.spbFile(File(dir, "bibles").apply { mkdirs() }, name = name, content = content)
    }

    /** Settings over this test's folders, showing only [tabs]. */
    protected fun settings(
        vararg tabs: Tabs,
        songSettings: SongSettings = SongSettings(),
        bibleFile: String? = null,
        secondaryBibleFile: String? = null,
        bibleSettings: BibleSettings = BibleSettings(),
    ): AppSettings = AppSettings(
        songSettings = songSettings.copy(storageDirectory = songsDir().absolutePath),
        bibleSettings = if (bibleFile == null) {
            bibleSettings
        } else {
            bibleSettings.copy(
                storageDirectory = File(dir, "bibles").absolutePath,
                primaryBible = bibleFile,
                secondaryBible = secondaryBibleFile.orEmpty(),
                translations = listOfNotNull(bibleFile, secondaryBibleFile)
                    .map { BibleTranslationSettings(fileName = it) },
            )
        },
        hiddenTabs = Tabs.entries.filter { it !in tabs }.map { it.name }.toSet(),
    )

    /** What a happy-path test drives: the manager the output draws from, and the schedule. */
    protected class HappyApp(
        val presenter: PresenterManager,
        private val scheduleActions: () -> ScheduleActions,
        val schedule: () -> List<ScheduleItem>,
        val settings: () -> AppSettings,
        internal val outputShown: MutableState<Boolean>,
    ) {
        val actions: ScheduleActions get() = scheduleActions()
    }

    /** Composes the main window over [appSettings] with the app's live wiring, then runs [block]. */
    protected fun happyPath(
        appSettings: AppSettings,
        qaManager: QAManager? = null,
        sttManager: STTManager? = null,
        serverUrl: String = "",
        block: ComposeUiTest.(HappyApp) -> Unit,
    ) =
        runSkikoComposeUiTest(size = Size(1600f, 1000f), density = Density(1f)) {
        val presenter = PresenterManager()
        var actions = ScheduleActions()
        var schedule = emptyList<ScheduleItem>()
        val outputShown = mutableStateOf(false)
        // What the main window keeps: every change the screen asks for is applied and handed back.
        val current = mutableStateOf(appSettings)
        setContent {
            val appSettings = current.value
            MaterialTheme {
                Box {
                    MainDesktop(
                        appSettings = appSettings,
                        presenterManager = presenter,
                        companionSatelliteViewModel = CompanionSatelliteViewModel(),
                        qaManager = qaManager,
                        sttManager = sttManager,
                        web = WebAccessState(serverUrl = serverUrl),
                        onSettingsChange = { change -> current.value = change(current.value) },
                        live = liveWiring(presenter),
                        publish = MainDesktopPublishers(
                            onScheduleActionsReady = { actions = it },
                            onScheduleChanged = { schedule = it },
                        ),
                    )
                    PresenterTransitionEffects(presenter, appSettings)
                    if (outputShown.value) {
                        val mode = remember { derivedStateOf { presenter.slideContent.value } }
                        Box(Modifier.size(480.dp, 270.dp).testTag(OUTPUT_TAG)) {
                            OffscreenOutputContent(
                                OffscreenOutputContext(
                                    presenterManager = presenter,
                                    appSettingsState = current,
                                    screenAssignmentState = remember { mutableStateOf(ScreenAssignment()) },
                                    effectiveModeState = mode,
                                    kind = OffscreenOutputKind.NDI,
                                    sttManager = sttManager,
                                ),
                                transparentBlanking = false,
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        block(HappyApp(presenter, { actions }, { schedule }, { current.value }, outputShown))
    }

    /** As `MainWindowChrome.liveOutputCallbacks` wires them, less the window and the row timing. */
    private fun liveWiring(presenter: PresenterManager) = LiveOutputCallbacks(
        presenting = { presenter.previewBus.present(it) },
        onVerseSelected = { presenter.previewBus.forVerses(it).setSelectedVerses(it) },
        onSongItemSelected = { presenter.previewBus.forSong(it).setLyricSection(it) },
        onAllSectionsChanged = { presenter.previewBus.forSong(it.firstOrNull()).setAllLyricSections(it) },
        onSectionIndexChanged = { presenter.previewBus.songStepTarget.setSongDisplaySectionIndex(it) },
        onLineIndexChanged = { presenter.previewBus.songStepTarget.setSongDisplayLineIndex(it) },
    )

    /** Every string the output draws right now, composing it for the read and taking it down after. */
    protected fun ComposeUiTest.outputText(app: HappyApp): String {
        app.outputShown.value = true
        waitForIdle()
        val text = textsUnder(hasAnyAncestor(hasTestTag(OUTPUT_TAG))).joinToString("\n")
        app.outputShown.value = false
        waitForIdle()
        return text
    }

    /** Waits until the output draws [fragment]. */
    protected fun ComposeUiTest.awaitOnOutput(app: HappyApp, fragment: String) {
        await("the output shows \"$fragment\"", WAIT_MS) { fragment in outputText(app) }
    }

    protected fun ComposeUiTest.await(what: String, timeoutMs: Long = WAIT_MS, condition: () -> Boolean) {
        try {
            waitUntil(timeoutMillis = timeoutMs, condition = condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("timed out waiting until $what", e)
        }
    }

    private fun ComposeUiTest.textsUnder(matcher: SemanticsMatcher): List<String> =
        onAllNodes(matcher and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { node -> node.text() }

    private fun SemanticsNode.text(): String? =
        config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    /** Clicks the leftmost, then topmost, node reading exactly [text] -- the tab's, not the preview's. */
    protected fun ComposeUiTest.clickText(text: String) {
        val nodes = onAllNodes(hasText(text))
        val target = nodes.fetchSemanticsNodes(atLeastOneRootRequired = false).withIndex()
            .minWithOrNull(compareBy({ it.value.boundsInRoot.left }, { it.value.boundsInRoot.top }))
            ?: throw AssertionError("nothing on screen reads \"$text\"")
        nodes[target.index].performClick()
        waitForIdle()
    }

    /** The control described as [description] outside the schedule's rows, once it is enabled. */
    protected fun ComposeUiTest.clickTabControl(description: String) {
        val matcher = hasContentDescription(description) and !hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG))
        await("\"$description\" is enabled") {
            onAllNodes(matcher and isEnabled())
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { it.boundsInRoot.width > 0f }
        }
        val nodes = onAllNodes(matcher and isEnabled())
        val shown = nodes.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.width > 0f }
        nodes[shown].performClick()
        waitForIdle()
    }

    protected fun ComposeUiTest.goLive() = clickTabControl("Go Live")

    protected fun ComposeUiTest.press(key: Key, ctrl: Boolean = false) {
        onAllNodes(isRoot())[0].performKeyInput {
            if (ctrl) keyDown(Key.CtrlLeft)
            pressKey(key)
            if (ctrl) keyUp(Key.CtrlLeft)
        }
        waitForIdle()
    }

    /** Clicks the control described as [description] on the schedule row reading [label]. */
    protected fun ComposeUiTest.clickOnScheduleRow(label: String, description: String) {
        val row = onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))
            .fetchSemanticsNodes().first().boundsInRoot
        val inRows = hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG))
        val controls = onAllNodes(hasContentDescription(description) and inRows)
        val onRow = controls.fetchSemanticsNodes()
            .indexOfFirst { it.boundsInRoot.center.y in row.top - ROW_SLACK..row.bottom + ROW_SLACK }
        if (onRow < 0) throw AssertionError("the \"$label\" row carries no \"$description\"")
        controls[onRow].performClick()
        waitForIdle()
    }

    /** Takes the schedule row reading [label] live with its own Go Live. */
    protected fun ComposeUiTest.takeRowLive(label: String) = clickOnScheduleRow(label, "Go Live")

    protected fun ComposeUiTest.selectTab(label: String) {
        onNodeWithText(label).performScrollTo().performClick()
        waitForIdle()
    }

    /** Clicks the schedule row reading [label], which opens it in its tab. */
    protected fun ComposeUiTest.clickScheduleRow(label: String) {
        onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))[0]
            .performMouseInput { click() }
        // The row waits out the double-click window before taking a press as a single click.
        mainClock.advanceTimeBy(DOUBLE_CLICK_WINDOW_MS)
        waitForIdle()
    }

    /** Whether the schedule holds a row reading [label]. */
    protected fun ComposeUiTest.scheduleShows(label: String): Boolean =
        onAllNodes(hasText(label, substring = true) and hasAnyAncestor(hasTestTag(SCHEDULE_ROW_CARD_TAG)))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
}
