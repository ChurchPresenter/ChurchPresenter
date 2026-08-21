@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import java.io.File
import java.nio.file.Files

/**
 * Harness and fixtures shared by the `QATab` test classes.
 *
 * Q&A is the one part of the app a stranger can reach: everyone in the room posts to it from their
 * own phone, and the moderator decides what reaches the screen. So the tab under test is a
 * moderation queue, and what these tests are about is which question ends up where — the
 * server-side guards on submission are already covered by `CompanionServerQaTest`.
 *
 * A real [QAManager] drives it, seeded through the same `submitQuestion` a phone would call.
 * `user.home` is isolated because the manager persists its session state there.
 */

// ── Harness ─────────────────────────────────────────────────────────────────────────────────────

/** What the tab reported back, so a test asserts on the choice rather than on a stub. */
internal class QAReports {
    val presenting = mutableListOf<Presenting>()
    var settingsChanges = 0
    var settingsAfterChange: AppSettings? = null
}

/**
 * Builds a real [QAManager] under an isolated `user.home`, seeds it with [seed], composes `QATab`
 * over it, and runs [block].
 */
@OptIn(ExperimentalTestApi::class)
internal fun qaTab(
    serverUrl: String = "http://192.0.2.1:8080",
    settings: AppSettings = AppSettings(),
    seed: QAManager.() -> Unit = {},
    width: Dp? = null,
    themeMode: ThemeMode? = null,
    block: ComposeUiTest.(qa: QAManager, presenter: PresenterManager, reports: QAReports) -> Unit,
) {
    TestSingletons.latchToTestHome()
    val realHome = System.getProperty("user.home")
    val tempHome: File = Files.createTempDirectory("cp-qa-tab").toFile()
    System.setProperty("user.home", tempHome.absolutePath)
    val qa = QAManager()
    val presenter = PresenterManager()
    val reports = QAReports()
    try {
        qa.seed()
        runComposeUiTest {
            setContent {
                ThemedForTest(themeMode) {
                    // The tab paints no ground of its own — in the app it sits on the window's
                    // `colorScheme.background` (MainDesktop). Without this the capture is
                    // transparent everywhere the tab does not draw, which reads as a black page.
                    Surface(color = MaterialTheme.colorScheme.background) {
                    Box(modifier = width?.let { Modifier.width(it) } ?: Modifier) {
                    QATab(
                        qaManager = qa,
                        presenterManager = presenter,
                        serverUrl = serverUrl,
                        presenting = { reports.presenting += it },
                        appSettings = settings,
                        onSettingsChange = { transform ->
                            reports.settingsChanges++
                            reports.settingsAfterChange = transform(settings)
                        },
                    )
                    }
                    }
                }
            }
            block(qa, presenter, reports)
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

/** Opens a session and posts [texts] as a phone would, returning nothing — read them off the manager. */
internal fun QAManager.askAll(vararg texts: String) {
    if (!sessionActive) toggleSession()
    texts.forEachIndexed { index, text ->
        // A distinct IP per question: the manager rate-limits repeat submissions from one device.
        submitQuestion(text, clientIp = "10.0.0.${index + 1}")
    }
}

// ── Labels, as the tab renders them ─────────────────────────────────────────────────────────────

internal object QALabel {
    const val ALL = "All"
    const val INCOMING = "Incoming"
    const val FINISHED = "Finished"
    const val APPROVED = "Approved"
    const val DONE = "Done"
    const val DENIED = "Denied"
    const val HISTORY = "History"
    const val NEW_SESSION = "New Session"
    const val STOP_SESSION = "Stop Session"
    const val ADD = "Add"
    const val ADD_HINT = "Add a question…"
    const val APPROVE = "Approve"
    const val DENY = "Deny"
    const val MARK_DONE = "Mark Done"
    const val DELETE = "Delete"
    const val GO_LIVE = "Go Live"
    const val CLEAR_DISPLAY = "Clear Display"
    const val WAITING = "Waiting for questions…"
    const val NO_APPROVED = "No approved questions yet"
    const val EDIT = "Edit"
    const val SAVE = "Save"
    const val CANCEL = "Cancel"
    const val CLEAR_ALL = "Clear All Questions"
    /** The confirm button inside the clear-all dialog. */
    const val CLEAR = "Clear"
    const val BACK_TO_INCOMING = "Back to Incoming"
    const val CONFIRM_GO_LIVE = "Confirm Go Live"
    const val INCOMING_APPROVED = "Incoming + Approved"
    const val NO_FINISHED = "No finished questions"
    const val NO_DENIED = "No denied questions"
    const val SHOW_QR = "Show QR on Display"
    const val HIDE_QR = "Hide QR from Display"
    const val VOTING_ENABLED = "Voting Enabled"
    const val VOTING_DISABLED = "Voting Disabled"
    const val SORT_OLDEST = "Oldest First"
    const val SORT_MOST_VOTES = "Most Votes"
    const val SORT_LEAST_VOTES = "Least Votes"
    const val DONE_CLEAR = "Done & Clear"
    const val DELETE_ALL_HISTORY = "Delete All History"
    const val EXPORT_TO_FILE = "Export to File"
    const val IMPORT_FROM_FILE = "Import from File"
    const val EXPORT_AND_CLEAR = "Export & Clear"
}

// ── Reading and driving what was rendered ───────────────────────────────────────────────────────
// (renderedText/showsExactly/showsContainingText are shared — see TabRenderedText.kt)

/** A button, addressed by the content description its tooltip gives it. */
internal fun ComposeUiTest.qaButton(label: String): SemanticsNodeInteraction =
    onNodeWithContentDescription(label)

internal fun ComposeUiTest.hasQaButton(label: String): Boolean =
    onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/** The question-entry box — the tab's first freely-typed field. */
internal fun ComposeUiTest.qaAddField() = onAllNodes(hasSetTextAction())[0]

/**
 * The inline edit box of the row being edited.
 *
 * The add box is always present and comes first in the tree, so the edit field is the *second*
 * typed field — there is only ever one row in edit mode at a time.
 */
internal fun ComposeUiTest.editField() = onAllNodes(hasSetTextAction())[1]

/** Clicks a labelled control, taking the topmost when a label repeats across panes. */
internal fun ComposeUiTest.clickQaLabel(label: String) {
    val nodes = onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false)
    val topmost = nodes.indices.minByOrNull { nodes[it].boundsInRoot.top }
        ?: error("nothing labelled \"$label\" is on screen")
    onAllNodesWithText(label)[topmost].performClick()
    waitForIdle()
}

/**
 * Chooses a view from the FILTER dropdown.
 *
 * The dropdown merges its caption and current value into one node ("FILTERAll (2)"), and each
 * option carries a live count ("Approved (1)"), so the trigger is matched on the caption and the
 * option on its name plus the opening bracket.
 */
internal fun ComposeUiTest.selectFilter(name: String) {
    onAllNodes(hasText("FILTER", substring = true))[0].performClick()
    waitForIdle()
    onAllNodes(hasText("$name (", substring = true))[0].performClick()
    waitForIdle()
}

/** Chooses an ordering from the SORT dropdown — its options carry no count, unlike FILTER's. */
internal fun ComposeUiTest.selectSort(name: String) {
    onAllNodes(hasText("SORT", substring = true))[0].performClick()
    waitForIdle()
    onAllNodes(hasText(name))[0].performClick()
    waitForIdle()
}

/** The nth button with this label, top to bottom — row buttons repeat once per question. */
internal fun ComposeUiTest.qaButton2(label: String, n: Int): SemanticsNodeInteraction {
    val nodes = onAllNodesWithContentDescription(label)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val order = nodes.indices.sortedBy { nodes[it].boundsInRoot.top }
    return onAllNodesWithContentDescription(label)[order[n]]
}

/** Which of [texts] are on screen, top to bottom. */
internal fun ComposeUiTest.orderOfQuestions(vararg texts: String): List<String> =
    texts.mapNotNull { text ->
        onAllNodes(hasText(text, substring = true))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .minByOrNull { it.boundsInRoot.top }
            ?.let { it.boundsInRoot.top to text }
    }.sortedBy { it.first }.map { it.second }

/** The Add button beside the question box — a plain labelled button, not an icon. */
internal fun ComposeUiTest.addButton(): SemanticsNodeInteraction = onAllNodesWithText(QALabel.ADD)[0]

/** Whether a question's text is on screen anywhere. */
internal fun ComposeUiTest.showsQuestion(text: String): Boolean =
    onAllNodes(hasText(text, substring = true))
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
        .isNotEmpty()

/**
 * Hovers the row carrying [text] and lets its tooltip open.
 *
 * The clock is advanced past `TooltipArea`'s open delay rather than waited out, so the tooltip is
 * on screen by the time this returns without the test costing the delay in real time.
 */
internal fun ComposeUiTest.hoverQuestionRow(text: String) {
    onAllNodes(hasText(text, substring = true))[0].performMouseInput { moveTo(center) }
    mainClock.advanceTimeBy(600)
    waitForIdle()
}

internal fun ComposeUiTest.typeQuestion(text: String) {
    qaAddField().performTextReplacement(text)
    waitForIdle()
}
