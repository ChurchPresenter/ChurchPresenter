@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.atem.AtemState
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.AtemSettings
import org.churchpresenter.settings.StreamingSettings
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `LowerThirdTab` test classes.
 *
 * The tab is a preset picker over a folder of Lottie files: it lists what is in the folder, previews
 * one, and hands the chosen animation to the output or the schedule. So the fixtures are real files
 * on disk — the tab reads and parses them itself, and a stub that never parses would exercise the
 * error path instead of the one under test.
 *
 * Nothing here drives the ATEM upload panel: it reaches a switcher over the network, and what can be
 * decided before that is already covered by `CompanionServerLowerThirdTest`.
 */

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** A Lottie the app can time: 60 frames at 30fps = 2000ms. */
internal const val LOWER_THIRD_LOTTIE =
    """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":1920,"h":1080,"layers":[]}"""

/** Same, at an arbitrary canvas size — for the warnings that compare the design against a frame. */
internal fun lottieSized(width: Int, height: Int): String =
    """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":$width,"h":$height,"layers":[]}"""

/** A folder holding [names] as real Lottie files, plus one file that is not a preset. */
internal fun lottieFolder(vararg names: String): File =
    Files.createTempDirectory("cp-lowerthird-tab").toFile().apply {
        names.forEach { File(this, "$it.json").writeText(LOWER_THIRD_LOTTIE) }
        File(this, "notes.txt").writeText("not a preset")
    }

/** A folder holding one real Lottie file per (name, json) pair, each with its own content. */
internal fun lottieFolderWithContent(vararg files: Pair<String, String>): File =
    Files.createTempDirectory("cp-lowerthird-tab").toFile().apply {
        files.forEach { (name, json) -> File(this, "$name.json").writeText(json) }
    }

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class LowerThirdReports {
    /** presetId, presetLabel, pauseAtFrame, pauseDurationMs — as the schedule would be given them. */
    val scheduled = mutableListOf<List<Any>>()
    /** presetName of each go-live, in order. */
    val live = mutableListOf<String>()
    /** The json handed to the output for the most recent go-live. */
    var liveJson: String? = null
    var settingsChanges = 0
    /** Each time the generator was asked for, with the folder it was pointed at. */
    val generatorOpenedFor = mutableListOf<String>()
    /** The `onFileSaved` callback the tab handed the generator, so a test can fire it. */
    var generatorOnFileSaved: (() -> Unit)? = null
    /** How many times the operator was asked to confirm removing a preset. */
    var removalsAsked = 0
}

/**
 * Composes `LowerThirdTab` over [folder] and runs [block].
 *
 * Settings are fed back into the tab on every change, as `MainDesktop` does, so a control's effect
 * is visible on the next frame.
 */
@OptIn(ExperimentalTestApi::class)
internal fun lowerThirdTab(
    folder: File? = lottieFolder("Welcome", "Speaker Name"),
    /**
     * What the ATEM upload dialog reads its media-pool state from.
     *
     * Injected because the real call is **UDP with a 5s socket timeout** — there is no fast
     * connection-refused, so every test that opened the dialog used to cost five seconds, which is
     * what capped this tab at ~42%. Supply a canned [AtemState], or throw to exercise the error path.
     */
    queryAtemState: suspend (host: String, port: Int) -> AtemState = { _, _ ->
        error("no ATEM in tests unless the test supplies one")
    },
    /**
     * Whether the (fake) switcher answers.
     *
     * The ATEM row is gated on `atemConfigured && atemEverConnected`, so a test that wants it needs
     * both a non-blank host and a probe that succeeds — this sets both. Off by default, which is how
     * the tab looks for the majority of churches, who have no switcher.
     */
    atemReachable: Boolean = false,
    /**
     * Overrides the reachability probe the tab polls on a loop.
     *
     * [atemReachable] is a constant answer, which is what almost every test wants. This is for the
     * ones about the *transition* — a switcher that answers and then stops.
     */
    probeAtemReachable: (suspend (String, Int) -> Boolean)? = null,
    /**
     * How the removal confirm is answered. The shipped default is a modal Swing dialog that no
     * headless test can click, so the harness stands in for the operator: `true` says yes.
     */
    confirmRemoval: Boolean = true,
    /**
     * Where the in-app ATEM upload actually connects. The default is unroutable on purpose — most
     * tests only need the dialog, and reaching a real switcher would cost a 5s socket timeout.
     * `LowerThirdAtemUploadTest` points these at a `FakeAtemSwitcher` on loopback.
     */
    atemHost: String = "10.0.0.9",
    atemPort: Int = 9910,
    /**
     * Raster the ATEM dialog reasons about. Defaults MUST match `AtemSettings()`'s own — the dialog
     * compares the design's size against them to decide whether it is upscaling, so a smaller
     * default here silently changes what other tests in this package are asserting.
     */
    atemRenderWidth: Int = 1920,
    atemRenderHeight: Int = 1080,
    /** Whether the ATEM row shows one-click upload buttons instead of opening the dialog. */
    quickUpload: Boolean = false,
    /** A lower third clicked in the schedule, which the tab resolves back to one of its presets. */
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem? = null,
    /**
     * Applied to the settings this harness builds, for the states the parameters above don't name —
     * `goLiveKey` already armed, a wider preset list. Runs last, so it can override anything here.
     */
    settings: (AppSettings) -> AppSettings = { it },
    /** Constrains the tab's width, for the screenshots that show how it reflows in a narrow panel. */
    width: Dp? = null,
    /** Non-null renders through the real app theme, which is what the screenshot suite shoots. */
    themeMode: ThemeMode? = null,
    /**
     * Whether the main window is maximised, which picks *which* saved panel width the tab reads —
     * `maximizedLayout` or `windowedLayout`. The app's own default is `true`.
     */
    isWindowMaximized: Boolean = true,
    block: ComposeUiTest.(reports: LowerThirdReports) -> Unit,
) {
    val reports = LowerThirdReports()
    try {
        runComposeUiTest {
            setContent {
                var appSettings by remember {
                    mutableStateOf(
                        settings(
                            AppSettings(
                                streamingSettings = StreamingSettings(
                                    lowerThirdFolder = folder?.absolutePath ?: ""
                                ),
                                atemSettings = if (atemReachable) {
                                    AtemSettings(
                                        host = atemHost,
                                        port = atemPort,
                                        quickUpload = quickUpload,
                                        renderWidth = atemRenderWidth,
                                        renderHeight = atemRenderHeight,
                                    )
                                } else {
                                    AtemSettings()
                                },
                            )
                        )
                    )
                }
                ThemedForTest(themeMode) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                        LowerThirdTab(
                            appSettings = appSettings,
                            onSettingsChange = { transform ->
                                appSettings = transform(appSettings)
                                reports.settingsChanges++
                            },
                            onAddToSchedule = { id, label, pause, pauseMs ->
                                reports.scheduled += listOf(id, label, pause, pauseMs)
                            },
                            onGoLive = { json, _, _, _, presetName ->
                                reports.live += presetName
                                reports.liveJson = json
                            },
                            selectedLowerThirdItem = selectedLowerThirdItem,
                            isWindowMaximized = isWindowMaximized,
                            onOpenLottieGen = { outputDir, onSaved ->
                                reports.generatorOpenedFor += outputDir
                                reports.generatorOnFileSaved = onSaved
                            },
                            queryAtemState = queryAtemState,
                            probeAtemReachable = probeAtemReachable ?: { _, _ -> atemReachable },
                            confirmRemoval = { _, _, onConfirmed ->
                                reports.removalsAsked++
                                if (confirmRemoval) onConfirmed()
                            },
                        )
                    }
                }
            }
            block(reports)
        }
    } finally {
        folder?.deleteRecursively()
    }
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object LowerThirdLabel {
    const val NO_PRESETS = "No presets saved yet"
    const val SELECT_PRESET = "Select a preset to preview"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val REMOVE = "Remove"
    const val GENERATE = "Generate"
    const val GO_LIVE_KEY = "Go Live drives ATEM key"
    /** The quick-upload pair name the slot they target, which is 1-based on screen. */
    const val QUICK_STILL = "Upload image to ATEM slot 1"
    const val QUICK_CLIP = "Upload video to ATEM slot 1"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.ltButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasLtButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * Selects a preset from the list by its name, and waits for its animation to finish loading.
 *
 * The wait is the point. Clicking a preset only starts the Lottie parse; `canPlay` — and with it
 * every action in the tab — stays false until that finishes, off the composition thread, so
 * `waitForIdle` can return with the preset chosen and nothing yet playable. Whether a test sees
 * that gap comes down to how much else the JVM was doing, which is why it shows up when suites run
 * together and never in isolation. [openAtemDialog] carried its own copy of this wait for exactly
 * that reason; it belongs here, where every caller gets it.
 *
 * The Go Live button is the signal because it is enabled on `canPlay` itself. Every fixture in this
 * file is a valid Lottie, so the wait always ends on the button rather than on the timeout.
 */
internal fun ComposeUiTest.selectPreset(name: String) {
    onAllNodesWithText(name)[0].performClick()
    waitForIdle()
    waitUntil("the chosen preset finished loading", 5_000L) {
        onAllNodes(hasContentDescription(LowerThirdLabel.GO_LIVE) and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

/** The upload button's tooltip, which is also its content description. */
internal const val ATEM_UPLOAD_LABEL = "Send to ATEM"

/** The dialog's status line once the frames it will send have been rendered. */
internal const val ATEM_READY = "Ready to upload"

/**
 * Waits until the open dialog's status line reads [ATEM_READY].
 *
 * Opening the dialog — and switching its mode — starts a background render of the frames the upload
 * would send, and until that lands the status line is "Preparing frames" over a progress bar
 * instead. Nothing in the dialog waits for it, so which of the two a test sees is decided by how
 * fast the render finished, and the two states are different heights: the whole dialog below the
 * line shifts with them.
 *
 * The `waitForIdle` first is what makes the wait mean something. `atemPrepareProgress` starts at
 * `1f`, so the dialog's first composition claims "ready" before its `LaunchedEffect` has started the
 * render and reported back - checking the text without letting that effect run could pass on the
 * claim rather than on the render.
 */
internal fun ComposeUiTest.waitForAtemPrepared() {
    waitForIdle()
    waitUntil("the ATEM frame render finished", 5_000L) {
        onAllNodesWithText(ATEM_READY)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}

/**
 * Selects [presetName] — the upload button is disabled until one is chosen — then opens the ATEM
 * upload dialog.
 *
 * Both waits below replace a bare `waitForIdle()` that made this helper intermittently fail under
 * load (reliably when the ATEM suites ran together, never in isolation). Selecting a preset enables
 * the upload button asynchronously, and **a click on a disabled control is silently swallowed** — so
 * the click landed on nothing and the dialog never opened, which surfaced much later as "there are
 * no existing nodes for that selector" at whatever the test did next. Each wait ends on a positive
 * signal; the timeouts exist only to fail the test.
 */
internal fun ComposeUiTest.openAtemDialog(presetName: String = "Welcome") {
    selectPreset(presetName)
    waitUntil("the ATEM upload button is enabled", 5_000L) {
        onAllNodes(hasContentDescription(ATEM_UPLOAD_LABEL) and isEnabled())
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
    ltButton(ATEM_UPLOAD_LABEL).performClick()
    waitUntil("the dialog's upload-mode rows are composed", 5_000L) {
        onAllNodes(isSelectable()).fetchSemanticsNodes().size >= 2
    }
    waitForAtemPrepared()
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
