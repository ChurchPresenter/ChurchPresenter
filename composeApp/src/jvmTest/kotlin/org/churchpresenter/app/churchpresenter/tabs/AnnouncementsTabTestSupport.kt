@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

/**
 * Harness and fixtures shared by the `AnnouncementsTab` test classes.
 *
 * This tab owns its view model outright (`remember { AnnouncementsViewModel() }`), so unlike the
 * other tabs there is no instance to reach in from a test. That turns out to suit it: everything
 * the tab does is observable from outside anyway — what it draws, the `AnnouncementsSettings` it
 * hands back for persisting, and what it puts on the [PresenterManager] — and asserting through
 * those keeps the tests pinned to behaviour rather than to the view model's internals.
 *
 * The settings the tab reports are fed back into it, as the app does, so a control's effect is
 * visible on the next frame and the tests read the way the tab is actually used.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class AnnouncementReports {
    val scheduled = mutableListOf<AnnouncementsSettings>()
    var settingsChanges = 0

    /** The most recent settings the tab asked to have persisted. */
    var settings: AnnouncementsSettings? = null
}

/**
 * Composes `AnnouncementsTab` with a real [PresenterManager] and runs [block].
 *
 * The tab is given its settings back on every change, so the state it renders from is the state it
 * just asked for — the same loop `MainDesktop` runs.
 */
@OptIn(ExperimentalTestApi::class)
internal fun announcementsTab(
    initial: AnnouncementsSettings = AnnouncementsSettings(),
    withPresenter: Boolean = true,
    withOnAddToSchedule: Boolean = true,
    projectionSettings: ProjectionSettings = ProjectionSettings(),
    settings: (AppSettings) -> AppSettings = { it },
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(presenter: PresenterManager, reports: AnnouncementReports) -> Unit,
) {
    val presenter = PresenterManager()
    val reports = AnnouncementReports()
    runComposeUiTest {
        setContent {
            var appSettings by remember {
                mutableStateOf(
                    settings(AppSettings(announcementsSettings = initial, projectionSettings = projectionSettings))
                )
            }
            ThemedForTest(themeMode) {
                Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    AnnouncementsTab(
                        appSettings = appSettings,
                        onSettingsChange = { transform ->
                            appSettings = transform(appSettings)
                            reports.settingsChanges++
                            reports.settings = appSettings.announcementsSettings
                        },
                        presenterManager = presenter.takeIf { withPresenter },
                        onAddToSchedule =
                            if (withOnAddToSchedule) {
                                { s: AnnouncementsSettings -> reports.scheduled += s }
                            } else null,
                    )
                }
            }
        }
        block(presenter, reports)
    }
}

@Composable
private fun ThemedForTest(themeMode: ThemeMode?, content: @Composable () -> Unit) {
    if (themeMode == null) MaterialTheme(content = content)
    else ChurchPresenterTheme(themeMode = themeMode, content = content)
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object AnnouncementLabel {
    const val TEXT_HINT = "Enter announcement text here…"
    const val GO_LIVE = "Go Live"
    const val ADD_TO_SCHEDULE = "Add to Schedule"
    const val SHOW = "Show Announcement on Display"
    const val HIDE = "Hide Announcement from Display"
    const val START = "Start"
    const val PAUSE = "Pause"
    const val RESET = "Reset"
    const val BOLD = "B"
    const val ITALIC = "I"
    const val UNDERLINE = "U"
    const val SHADOW = "S"
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

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/**
 * A button, addressed by the content description its tooltip gives it and by which half of the tab
 * it belongs to.
 *
 * The announcement and the timer each have their own Go Live and Add to Schedule, identically
 * labelled, so a bare lookup is ambiguous — and an ambiguous lookup fails as "cannot inject mouse
 * input" rather than as anything that names the real problem. They are told apart by position: the
 * announcement's row is above the timer's.
 */
private fun ComposeUiTest.buttonsByRow(label: String) =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .sortedBy { it.boundsInRoot.top }

internal fun ComposeUiTest.annButton(label: String): SemanticsNodeInteraction {
    val index = buttonsByRow(label).indices.firstOrNull()
        ?: error("no button labelled \"$label\" is on screen")
    return sortedButton(label, index)
}

/** The timer's copy of a button that both halves of the tab have. */
internal fun ComposeUiTest.timerButton(label: String): SemanticsNodeInteraction =
    sortedButton(label, buttonsByRow(label).lastIndex)

private fun ComposeUiTest.sortedButton(label: String, position: Int): SemanticsNodeInteraction {
    val tops = buttonsByRow(label).map { it.boundsInRoot.top }
    val target = tops.getOrNull(position) ?: error("no button labelled \"$label\" at $position")
    // Re-resolve through the matcher rather than holding the node, so the interaction stays live.
    val unsorted = onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    return onAllNodesWithContentDescription(label)[unsorted.indexOfFirst { it.boundsInRoot.top == target }]
}

/**
 * The loop count field's own increment button.
 *
 * "Increment"/"Decrement" also label the font-size field's arrows, so a bare lookup is ambiguous.
 * Loop count sits lower on screen (in the right column, below the preview), so it is the one with
 * the larger `top`.
 */
internal fun ComposeUiTest.loopCountIncrement(): SemanticsNodeInteraction {
    val nodes = onAllNodesWithContentDescription("Increment").fetchSemanticsNodes(atLeastOneRootRequired = false)
    val lowest = nodes.indices.maxByOrNull { nodes[it].boundsInRoot.top } ?: error("no Increment button is on screen")
    return onAllNodesWithContentDescription("Increment")[lowest]
}

internal fun ComposeUiTest.hasAnnButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * The announcement text box — the first field taking typed text.
 *
 * Addressed that way rather than by its hint, because the hint is a separate `Text` inside the
 * `BasicTextField` decoration box and vanishes as soon as anything is typed.
 */
internal fun ComposeUiTest.announcementField() = onAllNodes(hasSetTextAction())[0]

/**
 * The expiry-message field, addressed as the lowest field on screen.
 *
 * Not by index: the tab has eight fields (the announcement, the font name and size, the three timer
 * digits, this, and the loop count) and their order in the semantics tree is not the order they are
 * drawn in. The expiry message is the bottom-most of them, and it is the only one whose presence
 * depends on the timer mode.
 */
internal fun ComposeUiTest.expiredTextField(): SemanticsNodeInteraction {
    val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val lowest = fields.indices.maxByOrNull { fields[it].boundsInRoot.top }
        ?: error("no text fields are on screen")
    return onAllNodes(hasSetTextAction())[lowest]
}

internal fun ComposeUiTest.typeAnnouncement(text: String) {
    announcementField().performTextReplacement(text)
    waitForIdle()
}

/** Clicks a labelled control and settles the frame. */
internal fun ComposeUiTest.clickLabel(label: String) {
    onNodeWithText(label).performClick()
    waitForIdle()
}
