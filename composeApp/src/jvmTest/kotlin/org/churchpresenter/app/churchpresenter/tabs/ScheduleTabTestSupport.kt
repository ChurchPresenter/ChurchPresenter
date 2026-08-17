@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.ScheduleViewModel
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `ScheduleTab` test classes.
 *
 * The tab is driven through a real [ScheduleViewModel] — the same one the app builds — so what is
 * exercised is the wiring between the two: which view-model call a button makes, and what the list
 * renders from the resulting state. The view model's own rules (undo history, move semantics,
 * remote following) are already covered by the `ScheduleViewModel*` suites, so nothing here
 * re-tests those; these tests assert the schedule the operator ends up with.
 *
 * `user.home` is isolated per test because the view model resolves its autosave path at
 * construction and `newSchedule()` deletes that file. [TestSingletons.latchToTestHome] pins the
 * JVM-wide loggers to the real test home first, so they do not latch onto a temp dir that is then
 * deleted.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The window [runComposeUiTest] composes into when it is not told otherwise, which is why every
 * capture from this harness comes out 1024x768. Stated here only so the density branch below can
 * hand the Skiko runner the same size instead of silently rendering into a different one.
 */
private val DEFAULT_TEST_WINDOW = Size(1024f, 768f)

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class ScheduleReports {
    val presenting = mutableListOf<Presenting>()
    val clicked = mutableListOf<ScheduleItem>()
    val presented = mutableListOf<ScheduleItem>()
    val editedLabels = mutableListOf<ScheduleItem.LabelItem>()
    val selectionChanges = mutableListOf<String?>()
    var addLabelRequests = 0
    var addWebsiteRequests = 0
    val zoomChanges = mutableListOf<Int>()
    val legacyRowActionChanges = mutableListOf<Boolean>()
    val toolbarButtonToggles = mutableListOf<ScheduleToolbarButton>()

    /**
     * The action set the tab hands its parent, so the menu and keyboard paths — which never touch a
     * button in this tab — can be driven the way `MainDesktop` drives them.
     */
    var actions: ScheduleTabActions? = null
}

/**
 * Builds a real [ScheduleViewModel] under an isolated `user.home`, seeds it with [seed], composes
 * `ScheduleTab` over it, and runs [block].
 *
 * The view model is created before composition and passed in, so a test can seed it without racing
 * the tab's first frame — and so `block` can read the schedule back from the same instance the tab
 * is driving.
 */
@OptIn(ExperimentalTestApi::class)
internal fun scheduleTab(
    itemZoomPercent: Int = 100,
    /** The legacy card layout — buttons on their own line under the title, always visible. */
    legacyRowActions: Boolean = false,
    /** Toolbar buttons turned off from the panel's options menu, by [ScheduleToolbarButton] name. */
    hiddenToolbarButtons: Set<String> = emptySet(),
    /** Constrains the panel, for the layout tests that need it narrow enough to wrap. */
    width: Dp? = null,
    seed: ScheduleViewModel.() -> Unit = {},
    /** Null keeps the plain MaterialTheme every other test composes under; set to shoot a theme. */
    themeMode: ThemeMode? = null,
    /**
     * Null renders at the default 1 dp = 1 px, which every test here wants: they assert on layout,
     * and more pixels of the same layout tells them nothing.
     *
     * Set only by the capture the website exports, which needs the same dp at more pixels to stay
     * crisp on a retina display. It switches to the Skiko runner because [runComposeUiTest] has no
     * density hook, and that runner needs its window size stated rather than defaulted — hence
     * [DEFAULT_TEST_WINDOW], which is the size the other branch gets for free.
     */
    density: Float? = null,
    block: ComposeUiTest.(vm: ScheduleViewModel, reports: ScheduleReports) -> Unit,
) {
    TestSingletons.latchToTestHome()
    val realHome = System.getProperty("user.home")
    val tempHome: File = Files.createTempDirectory("cp-schedule-tab").toFile()
    System.setProperty("user.home", tempHome.absolutePath)
    val vm = ScheduleViewModel()
    try {
        vm.seed()
        val reports = ScheduleReports()
        val body: ComposeUiTest.() -> Unit = {
            setContent {
                ThemedForTest(themeMode) {
                    Box(modifier = if (width != null) Modifier.width(width) else Modifier) {
                    ScheduleTab(
                        scheduleViewModel = vm,
                        itemZoomPercent = itemZoomPercent,
                        onItemZoomChange = { reports.zoomChanges += it },
                        legacyRowActions = legacyRowActions,
                        onLegacyRowActionsChange = { reports.legacyRowActionChanges += it },
                        hiddenToolbarButtons = hiddenToolbarButtons,
                        onToggleToolbarButton = { reports.toolbarButtonToggles += it },
                        onPresenting = { reports.presenting += it },
                        onItemClick = { reports.clicked += it },
                        onEditLabel = { reports.editedLabels += it },
                        onSelectedItemChanged = { reports.selectionChanges += it },
                        onActionsReady = { reports.actions = it },
                        onAddLabel = { reports.addLabelRequests++ },
                        onPresentSong = { reports.presented += it },
                        onPresentBible = { reports.presented += it },
                        onPresentWebsite = { reports.presented += it },
                        onPresentAnnouncement = { reports.presented += it },
                        onPresentMedia = { reports.presented += it },
                        onPresentLowerThird = { reports.presented += it },
                        onPresentDictionary = { reports.presented += it },
                    )
                    }
                }
            }
            block(vm, reports)
        }
        if (density == null) {
            runComposeUiTest(block = body)
        } else {
            val window = Size(width?.value ?: DEFAULT_TEST_WINDOW.width, DEFAULT_TEST_WINDOW.height)
            runSkikoComposeUiTest(size = window * density, density = Density(density), block = body)
        }
    } finally {
        runCatching { vm.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }
}

/**
 * The registered [ScheduleTabActions], once the tab's `LaunchedEffect` has published them.
 *
 * Registration happens in an effect rather than during composition, so the wait is on the effect
 * having run — `waitForIdle` returns on that positive signal, not on a duration.
 */
internal fun ComposeUiTest.registeredActions(reports: ScheduleReports): ScheduleTabActions {
    waitForIdle()
    return requireNotNull(reports.actions) { "the tab must publish its actions to the parent" }
}

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/**
 * Writes an autosave the tab will offer to restore, into the temp `user.home` the harness has
 * already installed. Call from `seed`, which runs after the view model is built (so the path is
 * fixed) and before the first frame (so the prompt is decided against a file that exists).
 *
 * Plain JSON rather than the encrypted form: the encryption helpers are on a private companion, and
 * `restoreAutoSave` falls back to the raw text when decryption fails, which is the branch this
 * takes. What the file contains is beside the point here — `ScheduleAutoSaveTest` covers the
 * restore itself; these tests are about the dialog in front of it.
 */
internal fun plantAutoSave(vararg titles: String) {
    val items = titles.mapIndexed { i, title ->
        ScheduleItem.SongItem(id = "auto-$i", songNumber = i + 1, title = title, songbook = "Hymnal")
    }
    // Serialized through the real ScheduleItem serializer so the polymorphic discriminator is
    // whatever the model actually declares, rather than a string this fixture guesses at.
    val itemsJson = Json { encodeDefaults = true }
        .encodeToString(ListSerializer(ScheduleItem.serializer()), items)
    val file = File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp")
    file.parentFile.mkdirs()
    file.writeText("""{"version":2,"items":$itemsJson,"notes":{}}""")
}

/** Whether the autosave the tab was offered is still on disk. */
internal fun autoSaveExists(): Boolean =
    File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp").exists()


/** A service order with one of each item type the row renderer draws differently. */
internal fun ScheduleViewModel.seedService() {
    addLabel("Welcome", "#FFFFFF", "#203040")
    addSong(songNumber = 42, title = "Amazing Grace", songbook = "Hymnal")
    addBibleVerse(
        bookName = "John", chapter = 3, verseNumber = 16,
        verseText = "For God so loved the world.",
    )
    addWebsite(url = "https://example.org", title = "Notices")
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object ScheduleLabel {
    const val TITLE = "Schedule"
    const val NEW = "New Schedule"
    // Undo/Redo are located by tag, not by label — see [taggedButton].
    const val UNDO = ScheduleToolbarTags.UNDO
    const val REDO = ScheduleToolbarTags.REDO
    const val ADD_LABEL = "Add Label"
    const val CLEAR = "Clear Schedule"
    const val ZOOM_IN = "Zoom In"
    const val ZOOM_OUT = "Zoom Out"
    const val DROP_HINT = "Drag files here to add to schedule"
    const val MOVE_UP = "Move Up"
    const val MOVE_DOWN = "Move Down"
    const val GO_LIVE = "Go Live"
    const val REMOVE = "Remove"
    const val NOTE = "Note"
    const val EDIT_LABEL = "Edit Label"
    const val NOTE_SAVE = "Save note"
    const val NOTE_CLEAR = "Clear note"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/**
 * A toolbar or row button, addressed by the content description [TooltipIconButton] gives it —
 * which is its tooltip text, so the label a user would see is also the test's selector.
 */
internal fun ComposeUiTest.button(label: String) = onNodeWithContentDescription(label)

/**
 * A toolbar button addressed by test tag rather than by its tooltip.
 *
 * Undo and Redo name their keyboard shortcut in the tooltip, and that text is now built from the
 * live binding — it reads `Ctrl+Z` on Windows and Linux but `⌃Z` on macOS. Addressing those two by
 * their visible label would make the test pass on whichever platform the constant was written for
 * and fail on the rest.
 */
internal fun ComposeUiTest.taggedButton(tag: String) = onNodeWithTag(tag)

/**
 * The [n]th button with this label, top to bottom.
 *
 * Row buttons repeat once per schedule item, so a test that means "the second item's Go Live"
 * addresses it by position — which is also what the operator is doing.
 */
internal fun ComposeUiTest.buttonAt(label: String, n: Int) = onAllNodesWithContentDescription(label)[n]

internal fun ComposeUiTest.buttonCount(label: String): Int =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .size

/**
 * The note editor, which only exists once a row's note has been opened.
 *
 * Addressed as the node taking typed text rather than by its placeholder: the placeholder is drawn
 * separately and disappears as soon as anything is typed.
 */
internal fun ComposeUiTest.noteField() = onAllNodes(hasSetTextAction())[0]

/**
 * Where [labels] appear on screen, top to bottom, ignoring any that are absent.
 *
 * Ordered by vertical position rather than by walking the schedule, so a change to the order the
 * tab draws rows in is visible here. Takes explicit labels because a row is not one node: a song
 * draws its number, title and songbook separately, so there is no single node carrying the item's
 * `displayText`.
 */
internal fun ComposeUiTest.orderOf(vararg labels: String): List<String> {
    val wanted = labels.toSet()
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString("") { it.text } ?: return@mapNotNull null
            if (text in wanted) node.boundsInRoot.top to text else null
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
