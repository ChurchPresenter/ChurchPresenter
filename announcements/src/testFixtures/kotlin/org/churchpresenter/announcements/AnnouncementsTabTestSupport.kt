@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import org.churchpresenter.sharedui.utils.LocalMainWindowState
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.utils.PreviewOutput
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement

/**
 * Harness and fixtures shared by the `AnnouncementsTab` test classes.
 *
 * This tab owns its view model outright (`remember { AnnouncementsViewModel() }`), so unlike the
 * other tabs there is no instance to reach in from a test. That turns out to suit it: everything
 * the tab does is observable from outside anyway — what it draws, the `AnnouncementsSettings` it
 * hands back for persisting, and what it puts on the output ([FakeAnnouncementsOutput]) — and asserting through
 * those keeps the tests pinned to behaviour rather than to the view model's internals.
 *
 * The settings the tab reports are fed back into it, as the app does, so a control's effect is
 * visible on the next frame and the tests read the way the tab is actually used.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
class AnnouncementReports {
    val scheduled = mutableListOf<AnnouncementsSettings>()
    val presets = mutableListOf<AnnouncementsSettings>()
    var settingsChanges = 0

    /** The most recent settings the tab asked to have persisted. */
    var settings: AnnouncementsSettings? = null

    /** The whole of the most recent settings, for what the tab keeps outside its own section. */
    var appSettings: AppSettings? = null
}

/**
 * Composes `AnnouncementsTab` over a [FakeAnnouncementsOutput] and runs [block].
 *
 * The tab is given its settings back on every change, so the state it renders from is the state it
 * just asked for — the same loop `MainDesktop` runs.
 *
 * The window is [WINDOW_HEIGHT] tall rather than the default 768: the text card sits above the timer
 * in the left column, and at 768 the timer's controls scroll out of view, where they measure as
 * zero-size and cannot be clicked.
 */
@OptIn(ExperimentalTestApi::class)
fun announcementsTab(
    initial: AnnouncementsSettings = AnnouncementsSettings(),
    withPresenter: Boolean = true,
    withOnAddToSchedule: Boolean = true,
    withOnSavePreset: Boolean = false,
    /** Pinned rather than read from the host's locale, so the tests read the same everywhere. */
    use24HourClock: Boolean = false,
    /** Draws the tab in a floating (windowed) window rather than with no window state, which reads as maximised. */
    floatingWindow: Boolean = false,
    projectionSettings: ProjectionSettings = ProjectionSettings(),
    /** The screens the app would report as stage monitors for [projectionSettings]. */
    stageMonitorScreens: List<Int> = emptyList(),
    /** The output the app would have the preview stand for; 1920x1080 by default. */
    previewOutput: PreviewOutput? = null,
    settings: (AppSettings) -> AppSettings = { it },
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(presenter: FakeAnnouncementsOutput, reports: AnnouncementReports) -> Unit,
) {
    val presenter = FakeAnnouncementsOutput()
    val reports = AnnouncementReports()
    runDesktopComposeUiTest(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
        setContent {
            var appSettings by remember {
                mutableStateOf(
                    settings(AppSettings(announcementsSettings = initial, projectionSettings = projectionSettings))
                )
            }
            val windowState = if (floatingWindow) WindowState(placement = WindowPlacement.Floating) else null
            CompositionLocalProvider(LocalMainWindowState provides windowState) {
            ThemedForTest(themeMode) {
                Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    AnnouncementsTab(
                        appSettings = appSettings,
                        onSettingsChange = { transform ->
                            appSettings = transform(appSettings)
                            reports.settingsChanges++
                            reports.settings = appSettings.announcementsSettings
                            reports.appSettings = appSettings
                        },
                        output = presenter.takeIf { withPresenter },
                        stageMonitorScreens = stageMonitorScreens,
                        use24HourClock = use24HourClock,
                        previewOutput = previewOutput ?: FallbackPreviewOutput,
                        onAddToSchedule =
                            if (withOnAddToSchedule) {
                                { s: AnnouncementsSettings -> reports.scheduled += s }
                            } else null,
                        onSavePreset =
                            if (withOnSavePreset) {
                                { s: AnnouncementsSettings -> reports.presets += s }
                            } else null,
                    )
                }
            }
            }
        }
        block(presenter, reports)
    }
}

private const val WINDOW_WIDTH = 1024
private const val WINDOW_HEIGHT = 1200

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

object AnnouncementLabel {
    const val TEXT_HINT = "Enter announcement text here…"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val START = "Start"
    const val PAUSE = "Pause"
    const val RESET = "Reset"
    // The style keys draw a letter but are named for what they do (see clickStyle).
    const val BOLD = "Bold"
    const val ITALIC = "Italic"
    const val UNDERLINE = "Underline"
    const val SHADOW = "Shadow"
    const val DURATION_MODE = "Duration"
    const val CLOCK_MODE = "Specific Time"
    const val CLOCK_DISPLAY_MODE = "Clock"
    const val CENTER = "Center"
    const val TOP_LEFT = "Top Left"
    const val EXPIRED_HINT = "Enter message to show when done…"
    const val SEND_TO_STAGE_MONITOR = "Send to Stage Monitor"
    const val HIDE_FROM_STAGE_MONITOR = "Hide Announcement"
    const val TRANSPARENT = "Transparent (Default)"
}

// ── Driving the tab ─────────────────────────────────────────────────────────────────────────────

fun ComposeUiTest.typeAnnouncement(text: String) {
    announcementField().performTextReplacement(text)
    waitForIdle()
}

/** Clicks a labelled control and settles the frame. */
fun ComposeUiTest.clickLabel(label: String) {
    onNodeWithText(label).performClick()
    waitForIdle()
}

/** Clicks one of the B/I/U/S style keys, by the name a screen reader hears ([AnnouncementLabel.BOLD]…). */
fun ComposeUiTest.clickStyle(name: String) {
    onNodeWithContentDescription(name).performClick()
    waitForIdle()
}

/** Clicks one of the nine spots on the position screen, which carry their name as a description. */
fun ComposeUiTest.clickPosition(name: String) {
    onNodeWithContentDescription(name).performClick()
    waitForIdle()
}
