@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.app.churchpresenter.tabs.QALabel
import org.churchpresenter.app.churchpresenter.tabs.clickQaLabel
import org.churchpresenter.app.churchpresenter.tabs.qaButton
import org.churchpresenter.app.churchpresenter.tabs.qaButton2
import org.churchpresenter.app.churchpresenter.tabs.qaTab
import org.churchpresenter.app.churchpresenter.tabs.selectFilter
import org.churchpresenter.app.churchpresenter.tabs.selectSort
import org.churchpresenter.app.churchpresenter.tabs.typeQuestion
import org.churchpresenter.app.churchpresenter.viewmodel.QAManager
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * Every state of the Q&A tab, in both themes.
 *
 * The tab is a moderation queue, so what these shots are of is *where a question is*: waiting to be
 * judged, approved and ready, on screen, done, or refused — each with its own row buttons and its own
 * empty state when the filter finds nothing.
 *
 * Questions are seeded at fixed timestamps. Every row prints its own `HH:mm`, so seeding them at the
 * wall clock would rewrite every one of these images the moment the minute turned.
 */
class QATabScreenshotTest {

    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        seed: QAManager.() -> Unit = {},
        width: Dp? = null,
        rootIndex: Int = 0,
        drive: ComposeUiTest.(QAManager) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        qaTab(settings = settings, seed = seed, width = width, themeMode = mode) { qa, _, _ ->
            drive(qa)
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    // ── Before anyone has asked anything ────────────────────────────────────────────────────────

    @Test
    fun `no session started yet`() = shoot("no_session")

    @Test
    fun `a session open with nothing asked yet`() = shoot("session_waiting", seed = { toggleSession() })

    /** No server means no QR code and nowhere for a phone to post — the tab says so. */
    @Test
    fun `the server is not running`() = stackedThemes(SECTION, "no_server") { mode, file ->
        qaTab(serverUrl = "", themeMode = mode) { _, _, _ ->
            waitForIdle()
            captureTo(file)
        }
    }

    // ── The queue ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `questions waiting to be judged`() = shoot("incoming", seed = { ask() })

    @Test
    fun `one approved, one denied, one done`() = shoot("mixed_statuses", seed = { asked() })

    @Test
    fun `a question being edited in place`() = shoot("editing", seed = { ask() }) {
        qaButton2(QALabel.EDIT, 0).performClick()
        waitForIdle()
    }

    @Test
    fun `something typed into the moderator's own box`() = shoot("adding", seed = { ask() }) {
        typeQuestion("What time does the evening service start?")
    }

    @Test
    fun `an approved question on screen`() = shoot("displayed", seed = { asked() }) { qa ->
        qa.displayQuestion(qa.questions.first { it.text == APPROVED_TEXT }.id)
        waitForIdle()
    }

    // ── Filters, each with what it finds and what it says when it finds nothing ─────────────────

    @Test
    fun `filtered to approved`() = shoot("filter_approved", seed = { asked() }) {
        selectFilter(QALabel.APPROVED)
    }

    @Test
    fun `filtered to finished`() = shoot("filter_done", seed = { asked() }) {
        selectFilter(QALabel.DONE)
    }

    @Test
    fun `filtered to denied`() = shoot("filter_denied", seed = { asked() }) {
        selectFilter(QALabel.DENIED)
    }

    @Test
    fun `filtered to approved with none approved`() = shoot("empty_approved", seed = { ask() }) {
        selectFilter(QALabel.APPROVED)
    }

    @Test
    fun `filtered to denied with none denied`() = shoot("empty_denied", seed = { ask() }) {
        selectFilter(QALabel.DENIED)
    }

    // ── Sorting and voting ──────────────────────────────────────────────────────────────────────

    @Test
    fun `ordered oldest first`() = shoot("sort_oldest", seed = { ask() }) {
        selectSort(QALabel.SORT_OLDEST)
    }

    @Test
    fun `ordered by votes, with voting turned on`() = shoot(
        "sort_most_votes",
        settings = AppSettings(qaSettings = QASettings(votingEnabled = true)),
        seed = { voted() },
    ) { selectSort(QALabel.SORT_MOST_VOTES) }

    @Test
    fun `voting turned on`() = shoot(
        "voting_enabled",
        settings = AppSettings(qaSettings = QASettings(votingEnabled = true)),
        seed = { voted() },
    )

    // ── The display and history controls ────────────────────────────────────────────────────────

    @Test
    fun `the QR code sent to the screen`() = shoot("qr_on_display", seed = { ask() }) {
        qaButton(QALabel.SHOW_QR).performClick()
        waitForIdle()
    }

    @Test
    fun `a finished session waiting to be resumed`() = shoot("history", seed = {
        asked()
        toggleSession()
    })

    @Test
    fun `the clear-all confirmation`() = shoot("clear_all_confirm", seed = { ask() }, rootIndex = 1) {
        clickQaLabel(QALabel.CLEAR_ALL)
    }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot("narrow_panel", seed = { asked() }, width = 720.dp)

    @Test
    fun `a half-width panel`() = shoot("medium_panel", seed = { asked() }, width = 980.dp)

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Four questions as a congregation would send them, at fixed minutes past a fixed hour. */
    private fun QAManager.ask() {
        if (!sessionActive) toggleSession()
        QUESTIONS.forEachIndexed { index, text ->
            // A distinct IP per question: the manager rate-limits repeat submissions from one device.
            submitQuestion(text, clientIp = "10.0.0.${index + 1}", timestamp = SESSION_START + index * 60_000L)
        }
    }

    /** The same four, moderated — one approved, one done, one refused, one still waiting. */
    private fun QAManager.asked() {
        ask()
        questions.first { it.text == APPROVED_TEXT }.let { approveQuestion(it.id) }
        questions.first { it.text == QUESTIONS[1] }.let { approveQuestion(it.id); markDone(it.id) }
        questions.first { it.text == QUESTIONS[3] }.let { denyQuestion(it.id) }
    }

    /** Approved questions carrying different vote counts, so an ordering by votes has something to do. */
    private fun QAManager.voted() {
        ask()
        questions.forEachIndexed { index, question ->
            approveQuestion(question.id)
            repeat(index) { voter -> voteForQuestion(question.id, clientIp = "10.1.$index.$voter") }
        }
    }

    private companion object {
        const val SECTION = "qaTab"

        /** 2026-08-08 10:30 local time — a fixed instant, so every row's `HH:mm` is fixed too. */
        const val SESSION_START = 1_786_530_600_000L

        const val APPROVED_TEXT = "How do I join a small group?"

        val QUESTIONS = listOf(
            APPROVED_TEXT,
            "Is there parking behind the building?",
            "Could you say more about the passage in Romans?",
            "Where can I find the sermon recordings?",
        )
    }
}
