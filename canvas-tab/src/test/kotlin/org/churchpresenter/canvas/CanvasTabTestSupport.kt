@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `CanvasTab` test classes.
 *
 * The tab is the scene compositor's control surface: a list of scenes, the sources inside the
 * selected one, and the buttons that add, reorder, hide, lock and delete them. What is asserted is
 * the scene graph that results — [SceneViewModel] is real, and its own rules are covered by the
 * `SceneViewModel*` suites, so nothing here re-tests those.
 *
 * This tab only became testable once `assignedDisplayBounds` replaced a raw `screenDevices` call
 * (PR #88): the old one threw `HeadlessException` during composition.
 *
 * `user.home` is isolated because the view model persists scenes there and would otherwise write
 * into the developer's own library.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class CanvasReports {
    /** sceneId to sceneName, exactly as the schedule would be given them. */
    val scheduled = mutableListOf<Pair<String, String>>()
    var settingsChanges = 0

    /**
     * The settings as the tab's own edits have left them.
     *
     * The tab hands back a transform rather than a value, so counting the calls says a change was
     * asked for but not what it was. Applying each one in turn is what the app does with them, and
     * it is the only way to check that a panel width actually landed where the drag put it.
     */
    var settings: AppSettings = AppSettings()
        private set

    internal fun apply(change: (AppSettings) -> AppSettings) {
        settingsChanges++
        settings = change(settings)
    }
}

/**
 * Builds a real [SceneViewModel] under an isolated `user.home`, seeds it with [seed], composes
 * `CanvasTab` over it, and runs [block].
 */
@OptIn(ExperimentalTestApi::class)
internal fun canvasTab(
    seed: SceneViewModel.() -> Unit = {},
    settings: (AppSettings) -> AppSettings = { it },
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(vm: SceneViewModel, output: FakeCanvasOutput, reports: CanvasReports) -> Unit,
) {
    // :composeApp latched InstanceLinkLogger here; that class is in :companion-server and is not on
    // this module's classpath, so there is nothing to latch before the user.home swap below.
    val realHome = System.getProperty("user.home")
    val tempHome: File = Files.createTempDirectory("cp-canvas-tab").toFile()
    System.setProperty("user.home", tempHome.absolutePath)
    val vm = SceneViewModel()
    val output = FakeCanvasOutput()
    val reports = CanvasReports()
    try {
        vm.seed()
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                        CanvasTab(
                            appSettings = settings(AppSettings()),
                            onSettingsChange = { change -> reports.apply(change) },
                            output = output,
                            sceneViewModel = vm,
                            onAddToSchedule = { id, name -> reports.scheduled += id to name },
                        )
                    }
                }
            }
            block(vm, output, reports)
        }
    } finally {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object CanvasLabel {
    const val SCENES = "Scenes"
    const val SOURCES = "Sources"
    const val NEW_SCENE = "New"
    const val RENAME_SCENE = "Rename"
    const val REMOVE_SCENE = "Remove"
    const val ADD_SOURCE = "Add source"
    const val DELETE_SOURCE = "Delete source"
    const val TOGGLE_VISIBILITY = "Toggle visibility"
    const val TOGGLE_LOCK = "Toggle lock"
    const val MOVE_FORWARD = "Move forward"
    const val MOVE_BACKWARD = "Move backward"
    // The Add-source menu offers ten types; each is also the name the new source is given, which is
    // what makes `sourceNames()` enough to tell them apart.
    const val IMAGE = "Image"
    const val TEXT = "Text"
    const val CLOCK = "Clock"
    const val COLOR = "Color"
    const val VIDEO = "Video"
    const val BROWSER = "Browser"
    const val QR_CODE = "QR Code"
    const val CAMERA = "Camera"
    const val SCREEN_CAPTURE = "Screen Capture"
    const val BIBLE = "Bible"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.canvasButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasCanvasButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

internal fun ComposeUiTest.canvasButtonCount(label: String): Int =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .size

/**
 * The nth button with this label, top to bottom.
 *
 * Source rows each carry their own visibility, lock and delete buttons, so a test that means "the
 * second source's" addresses it by position — which is what the operator is doing too.
 */
internal fun ComposeUiTest.canvasButtonAt(label: String, n: Int): SemanticsNodeInteraction {
    val nodes = onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val order = nodes.indices.sortedBy { nodes[it].boundsInRoot.top }
    return onAllNodesWithContentDescription(label)[order[n]]
}

/**
 * Adds a source of the named kind through the add menu, as an operator would.
 *
 * Usable **once per test**: after the first use the button keeps its hover tooltip, and a second
 * click surfaces that instead of reopening the menu — the menu never appears and the item cannot be
 * found. Tests needing more than one source seed the rest with [seedSources].
 */
internal fun ComposeUiTest.addSourceOfType(typeLabel: String) {
    canvasButton(CanvasLabel.ADD_SOURCE).performClick()
    waitForIdle()
    onAllNodesWithText(typeLabel)[0].performClick()
    waitForIdle()
}

/** Puts [names] into the current scene as text sources, bottom layer first. */
internal fun SceneViewModel.seedSources(vararg names: String) {
    names.forEach { name ->
        addSource(SceneSource.TextSource(id = "src-$name", name = name))
    }
}

/** Clicks a labelled control, taking the topmost when the label repeats. */
internal fun ComposeUiTest.clickCanvasLabel(label: String) {
    val nodes = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val topmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.top }
        ?: error("nothing labelled \"$label\" is on screen")
    onAllNodesWithText(label)[topmost].performClick()
    waitForIdle()
}

/** The sources in the current scene, in the order the view model holds them. */
internal fun SceneViewModel.sourceNames(): List<String> =
    currentScene?.sources?.map(SceneSource::name) ?: emptyList()
