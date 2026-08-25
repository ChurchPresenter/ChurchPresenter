@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.shortcuts.LocalShortcuts
import org.churchpresenter.shortcuts.ShortcutMap
import org.churchpresenter.app.churchpresenter.viewmodel.PicturesViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * Harness and fixtures shared by the `PicturesTab` test classes.
 *
 * The tab is driven through a real [PicturesViewModel] over a real folder of real image files —
 * written with `ImageIO` rather than as stub bytes, because the view model decodes each one into a
 * thumbnail and a file that will not decode would exercise the error path instead of the one under
 * test.
 *
 * The folder listing is synchronous, so by the time a test body runs the grid is populated;
 * thumbnails decode on a background scope, so anything asserting on a *drawn* thumbnail waits for
 * that image to appear rather than assuming it is there.
 */

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** Writes a real, decodable image so the view model's thumbnail load succeeds. */
internal fun writeImage(dir: File, name: String) {
    val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
    ImageIO.write(image, name.substringAfterLast('.'), File(dir, name))
}

/** Three images plus a file that is not one, to pin what counts as a picture. */
internal fun pictureFolder(): File =
    Files.createTempDirectory("cp-pictures-tab").toFile().apply {
        writeImage(this, "one.png")
        writeImage(this, "two.png")
        writeImage(this, "three.jpg")
        File(this, "notes.txt").writeText("not an image")
    }

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class PictureReports {
    /** folderPath, folderName, imageCount — exactly what the schedule would be given. */
    val scheduled = mutableListOf<Triple<String, String, Int>>()
    var settingsChanges = 0
    var settingsAfterChange: AppSettings? = null
}

/**
 * Builds a real [PicturesViewModel] over [folder], composes `PicturesTab`, and runs [block].
 *
 * `storageDirectory` points at the folder so nothing resolves under the real `user.home`. Pass
 * `folder = null` for the no-folder-selected state.
 *
 * [presenterManager] is left null in most tests — passing one renders the Go Live button and turns
 * on the presenter-sync effects. [selectedPictureItem] and the Instance Link callbacks are also left
 * out of most tests; each is exercised on its own where it matters.
 *
 * Deliberately never touches `RecentPictureFolders` — that object is a JVM-wide singleton private to
 * `PicturesTab.kt` that persists to real JSON files under the real `~/.churchpresenter` directory on
 * first touch, with no test seam to redirect it. Every existing test already reads it (composing the
 * tab evaluates the recent-folders row unconditionally), which is harmless; nothing here calls
 * `add`/`togglePin`/`clear`, which
 * would write, because JVM-wide latching means an isolated `user.home` in this file could not
 * guarantee it wins the race to be first (see `TestSingletons`'s own doc comment for the same failure
 * mode on `CrashReporter`/`InstanceLinkLogger`). The "Select Folder" button and the recent-folder
 * chips are left untested for the same reason — both call `RecentPictureFolders.add` in the same
 * click handler as the part that would otherwise be worth testing.
 */
@OptIn(ExperimentalTestApi::class)
internal fun picturesTab(
    folder: File? = pictureFolder(),
    settings: (AppSettings) -> AppSettings = { it },
    presenterManager: PresenterManager? = null,
    selectedPictureItem: ScheduleItem.PictureItem? = null,
    onInstanceLinkSendNextPicture: (() -> Unit)? = null,
    onInstanceLinkSendPreviousPicture: (() -> Unit)? = null,
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    /** The bindings the tab resolves its key handler through; the shipped set unless overridden. */
    shortcuts: ShortcutMap = ShortcutMap.DEFAULT,
    block: ComposeUiTest.(vm: PicturesViewModel, reports: PictureReports) -> Unit,
) {
    val appSettings = settings(
        AppSettings(
            pictureSettings = PictureSettings(storageDirectory = folder?.absolutePath ?: "")
        )
    )
    val vm = PicturesViewModel(appSettings)
    try {
        folder?.let { vm.selectFolder(it) }
        val reports = PictureReports()
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                  CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                        PicturesTab(
                            viewModel = vm,
                            appSettings = appSettings,
                            presenterManager = presenterManager,
                            selectedPictureItem = selectedPictureItem,
                            onInstanceLinkSendNextPicture = onInstanceLinkSendNextPicture,
                            onInstanceLinkSendPreviousPicture = onInstanceLinkSendPreviousPicture,
                            onAddToSchedule = { path, name, count ->
                                reports.scheduled += Triple(path, name, count)
                            },
                            onSettingsChange = { transform ->
                                reports.settingsChanges++
                                reports.settingsAfterChange = transform(appSettings)
                            },
                        )
                    }
                  }
                }
            }
            block(vm, reports)
        }
    } finally {
        runCatching { vm.dispose() }
        folder?.deleteRecursively()
    }
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object PictureLabel {
    const val SELECT_FOLDER = "Select Folder"
    const val NO_FOLDER = "No folder selected"
    const val EMPTY_GRID = "Select a folder to view images"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val GO_LIVE = "Go Live"
    const val PREVIOUS = "Previous Image"
    const val NEXT = "Next Image"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val LOADING = "Loading..."
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────

// renderedText/showsExactly/showsContainingText live in TabRenderedText.kt — they are shared with
// the other tab suites in this package.

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.pictureButton(label: String) = onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasPictureButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * The thumbnails currently drawn, by file name — each `Image` is described by the file it came from.
 *
 * Thumbnails decode on a background scope, so a test that needs them waits for this to fill rather
 * than assuming the first frame has them.
 */
internal fun ComposeUiTest.drawnThumbnails(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
        .filter { it.endsWith(".png") || it.endsWith(".jpg") }

/**
 * Opens a settings tile's editor by clicking it.
 *
 * Each tile merges its caption and its value into one node ("AUTO-SCROLL INTERVAL:5 s"), so it is
 * addressed by the caption as a substring rather than by an exact match — the value changes, the
 * caption does not.
 */
internal fun ComposeUiTest.openTile(caption: String) {
    onNodeWithText(caption, substring = true).performClick()
    waitForIdle()
}

internal fun ComposeUiTest.openIntervalEditor() = openTile("AUTO-SCROLL INTERVAL")

internal fun ComposeUiTest.openTransitionEditor() = openTile("TRANSITION DURATION")

/** Opens the animation dropdown, which is merged into one node the same way the tiles are. */
internal fun ComposeUiTest.openAnimationDropdown() = openTile("ANIMATION TYPE")

/** The number field inside whichever editor dialog is open — the only field taking typed text. */
internal fun ComposeUiTest.editorField() = onAllNodes(hasSetTextAction())[0]

/** Waits until every one of [names] has been decoded and drawn. */
internal fun ComposeUiTest.awaitThumbnails(vararg names: String) =
    waitUntil("thumbnails for ${names.toList()}") {
        drawnThumbnails().containsAll(names.toList())
    }

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
