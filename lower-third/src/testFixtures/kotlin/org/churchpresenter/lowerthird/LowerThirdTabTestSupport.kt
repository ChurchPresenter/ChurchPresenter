@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import org.churchpresenter.sharedui.utils.PreviewOutput
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
import org.churchpresenter.sharedui.screenshot.RENDER_TIMEOUT_MS
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
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

/** How long a test waits for the tab to reach a state before it fails. */
const val WAIT_TIMEOUT_MS = 5_000L

// ── Fixtures ────────────────────────────────────────────────────────────────────────────────────

/** A Lottie the app can time: 60 frames at 30fps = 2000ms. */
const val LOWER_THIRD_LOTTIE =
    """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":1920,"h":1080,"layers":[]}"""

/** Same, at an arbitrary canvas size — for the warnings that compare the design against a frame. */
fun lottieSized(width: Int, height: Int): String =
    """{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":$width,"h":$height,"layers":[]}"""

/** A folder holding [names] as real Lottie files, plus one file that is not a preset. */
fun lottieFolder(vararg names: String): File =
    Files.createTempDirectory("cp-lowerthird-tab").toFile().apply {
        names.forEach { File(this, "$it.json").writeText(LOWER_THIRD_LOTTIE) }
        File(this, "notes.txt").writeText("not a preset")
    }

/** A folder holding one real Lottie file per (name, json) pair, each with its own content. */
fun lottieFolderWithContent(vararg files: Pair<String, String>): File =
    Files.createTempDirectory("cp-lowerthird-tab").toFile().apply {
        files.forEach { (name, json) -> File(this, "$name.json").writeText(json) }
    }

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
class LowerThirdReports {
    /** How many times the tab asked whether to delete a preset. */
    var removeAsked = 0

    /** presetId, presetLabel, pauseAtFrame, pauseDurationMs — as the schedule would be given them. */
    val scheduled = mutableListOf<List<Any>>()
    /** presetName of each go-live, in order. */
    val live = mutableListOf<String>()
    /** The json handed to the output for the most recent go-live. */
    var liveJson: String? = null
    var settingsChanges = 0
    /** The settings as they stood after the most recent change the tab asked for. */
    var settings: AppSettings? = null
}

/**
 * Composes `LowerThirdTab` over [folder] and runs [block].
 *
 * Settings are fed back into the tab on every change, as `MainDesktop` does, so a control's effect
 * is visible on the next frame.
 */
@OptIn(ExperimentalTestApi::class)
fun lowerThirdTab(
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
    /** Stands in for opening the Lottie generator window, which is a real window of its own. */
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit = { _, _ -> },
    /** The output the app would have the preview stand for; 1920x1080 by default. */
    previewOutput: PreviewOutput? = null,
    /**
     * Works the preview output out from the settings, as the app does; the app's own screenshot suite
     * passes its real one, so a capture draws what the app would. Wins over [previewOutput].
     */
    previewFor: (@Composable (AppSettings) -> PreviewOutput)? = null,
    /** The app's preview-output picker, given the settings, where changes go and its modifier. */
    pickerFor: (@Composable (AppSettings, ((AppSettings) -> AppSettings) -> Unit, Modifier) -> Unit)? = null,
    /** The answer to "delete this preset?"; asked questions are counted in the reports. */
    confirmRemove: Boolean = true,
    /** The preset the host reports on air, by name; none by default. */
    liveLowerThirdName: String? = null,
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
                val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = { transform ->
                    appSettings = transform(appSettings)
                    reports.settingsChanges++
                    reports.settings = appSettings
                }
                val preview = previewFor?.invoke(appSettings) ?: previewOutput ?: FallbackLowerThirdPreview
                ThemedForTest(themeMode) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                        LowerThirdTab(
                            appSettings = appSettings,
                            onSettingsChange = onSettingsChange,
                            outputPicker = { modifier -> pickerFor?.invoke(appSettings, onSettingsChange, modifier) },
                            onAddToSchedule = { id, label, pause, pauseMs ->
                                reports.scheduled += listOf(id, label, pause, pauseMs)
                            },
                            onGoLive = { json, _, _, _, presetName ->
                                reports.live += presetName
                                reports.liveJson = json
                            },
                            selectedLowerThirdItem = selectedLowerThirdItem,
                            previewOutput = preview,
                            queryAtemState = queryAtemState,
                            probeAtemReachable = { _, _ -> atemReachable },
                            onOpenLottieGen = onOpenLottieGen,
                            liveLowerThirdName = liveLowerThirdName,
                            confirmRemove = { _, _, onConfirmed ->
                                reports.removeAsked++
                                if (confirmRemove) onConfirmed()
                            },
                        )
                    }
                }
            }
            awaitPresetScan()
            block(reports)
        }
    } finally {
        folder?.deleteRecursively()
    }
}

/**
 * Waits for the tab's folder read to land before a test asserts on the list.
 *
 * The listing runs on `Dispatchers.IO` — deciding whether a `.json` is a Lottie means reading the
 * whole of it, so it cannot happen in composition. That work is off the test clock, so `waitForIdle`
 * does not cover it: without this, every test that names a preset raced a list that was still empty,
 * and the tab's own "Scanning folder…" caption is the positive signal that it is no longer reading.
 */
fun ComposeUiTest.awaitPresetScan() {
    // The deadline, not the condition. `waitUntil`'s own default is one second, and this is waiting
    // on a disk read that opens and parses every `.json` in the folder -- on four parallel JVMs that
    // is not a race, it is simply a deadline set below what the work legitimately takes, and the
    // suite failed on it about twice in three full runs while passing every time on its own.
    //
    // Widening a deadline is exactly what `AGENT.md` forbids as a flake "fix", and this is the case
    // it excludes: the wait already ends on a positive signal -- the "Scanning folder…" caption
    // going away -- so nothing here can be satisfied by the clock. The timeout is only how it fails,
    // and `RENDER_TIMEOUT_MS` is the number this repo already uses for a wait of that shape.
    waitUntil("the preset folder scan finished", timeoutMillis = RENDER_TIMEOUT_MS) {
        onAllNodesWithText(LowerThirdLabel.SCANNING, substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
    }
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

object LowerThirdLabel {
    // Three messages where there used to be one. "No presets saved yet" covered a folder that was
    // never chosen, one still being read and one with nothing in it — three different things to be
    // told, and an operator with a mistyped path was looking for files that were never coming.
    const val NO_FOLDER = "No directory selected"
    const val NO_FILES = "No JSON files found"
    const val SCANNING = "Scanning folder…"
    const val SELECT_PRESET = "Select a preset to preview"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val REMOVE = "Remove"
    const val GENERATE = "Generate"
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}
