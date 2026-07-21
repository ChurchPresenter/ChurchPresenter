package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audience Q&A moderation: what the congregation submits from their phones, what the operator
 * approves, and what reaches the screen. The rule that matters most is that nothing unapproved
 * can ever be displayed.
 *
 * IMPORTANT: [QAManager] resolves its state file from `user.home` **at construction** and both
 * loads from and saves to it, so every manager here is built while `user.home` points at a
 * throwaway directory -- otherwise a test run would read and overwrite the developer's real
 * `~/.churchpresenter/qa_state.json`.
 */
class QAManagerTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-qa-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    /** A manager with an open session, which is the state most actions require. */
    private fun openSession(): QAManager = QAManager().also { it.toggleSession() }

    private fun QAManager.submitApproved(text: String): String {
        val q = assertNotNull(submitQuestion(text), "submit failed for \"$text\"")
        assertTrue(approveQuestion(q.id))
        return q.id
    }

    // ── Submission ──────────────────────────────────────────────────────────────

    @Test
    fun `a new manager starts with no session and no questions`() {
        val qa = QAManager()
        assertFalse(qa.sessionActive)
        assertTrue(qa.questions.isEmpty())
        assertNull(qa.displayedQuestion)
        assertFalse(qa.showQRCodeOnDisplay)
    }

    @Test
    fun `submissions are rejected until the session is opened`() {
        val qa = QAManager()
        assertNull(qa.submitQuestion("Too early"), "the audience must not be able to queue up before the session opens")
        assertTrue(qa.questions.isEmpty())

        qa.toggleSession()
        assertNotNull(qa.submitQuestion("Now fine"))
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `blank submissions are rejected and text is trimmed`() {
        val qa = openSession()
        assertNull(qa.submitQuestion(""))
        assertNull(qa.submitQuestion("   "))
        assertEquals(0, qa.questions.size)

        val q = assertNotNull(qa.submitQuestion("  What is grace?  "))
        assertEquals("What is grace?", q.text)
    }

    @Test
    fun `submitted questions start unapproved`() {
        val qa = openSession()
        val q = assertNotNull(qa.submitQuestion("Anything"))
        assertEquals(QuestionStatus.PENDING, q.status, "audience submissions must never arrive pre-approved")
    }

    @Test
    fun `an operator-added question bypasses the session gate`() {
        // addQuestion is the operator typing directly into the desktop app, so unlike
        // submitQuestion it deliberately works with the session closed.
        val qa = QAManager()
        assertFalse(qa.sessionActive)
        assertNotNull(qa.addQuestion("Operator question"))
        assertEquals(1, qa.questions.size)
        assertNull(qa.addQuestion("  "), "but blank text is still rejected")
    }

    // ── Rate limiting ───────────────────────────────────────────────────────────

    @Test
    fun `the same ip cannot submit twice inside the cooldown`() {
        val qa = openSession()
        assertNotNull(qa.submitQuestion("First", clientIp = "10.0.0.1", cooldownSeconds = 30))
        assertNull(qa.submitQuestion("Second", clientIp = "10.0.0.1", cooldownSeconds = 30), "cooldown not enforced")
        assertTrue(qa.isRateLimited("10.0.0.1", 30))
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `the cooldown is per ip, not global`() {
        val qa = openSession()
        assertNotNull(qa.submitQuestion("From A", clientIp = "10.0.0.1", cooldownSeconds = 30))
        assertNotNull(qa.submitQuestion("From B", clientIp = "10.0.0.2", cooldownSeconds = 30), "one phone must not block the room")
        assertFalse(qa.isRateLimited("10.0.0.2", 0))
    }

    @Test
    fun `a zero cooldown or an unknown ip disables rate limiting`() {
        val qa = openSession()
        assertNotNull(qa.submitQuestion("A", clientIp = "10.0.0.1", cooldownSeconds = 0))
        assertNotNull(qa.submitQuestion("B", clientIp = "10.0.0.1", cooldownSeconds = 0))
        assertFalse(qa.isRateLimited("10.0.0.1", 0))
        assertFalse(qa.isRateLimited("", 30), "an empty ip cannot be rate limited")
        assertFalse(qa.isRateLimited("10.0.0.99", 30), "an ip that never submitted is not limited")
    }

    // ── Moderation ──────────────────────────────────────────────────────────────

    @Test
    fun `approve deny and done move a question through its states`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Q")).id

        assertTrue(qa.approveQuestion(id))
        assertEquals(QuestionStatus.APPROVED, qa.findQuestion(id)?.status)

        assertTrue(qa.denyQuestion(id))
        assertEquals(QuestionStatus.DENIED, qa.findQuestion(id)?.status)

        assertTrue(qa.markDone(id))
        assertEquals(QuestionStatus.DONE, qa.findQuestion(id)?.status)
    }

    @Test
    fun `moderation actions on an unknown id report failure instead of throwing`() {
        val qa = openSession()
        assertFalse(qa.approveQuestion("nope"))
        assertFalse(qa.denyQuestion("nope"))
        assertFalse(qa.markDone("nope"))
        assertFalse(qa.deleteQuestion("nope"))
        assertFalse(qa.editQuestion("nope", "text"))
        assertFalse(qa.displayQuestion("nope"))
    }

    @Test
    fun `editing rewrites the text but refuses to blank it`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Original")).id

        assertTrue(qa.editQuestion(id, "  Reworded by the operator  "))
        assertEquals("Reworded by the operator", qa.findQuestion(id)?.text)

        assertFalse(qa.editQuestion(id, "   "), "a question must not be blanked out by an edit")
        assertEquals("Reworded by the operator", qa.findQuestion(id)?.text)
    }

    @Test
    fun `deleting removes the question entirely`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Q")).id
        assertTrue(qa.deleteQuestion(id))
        assertNull(qa.findQuestion(id))
        assertTrue(qa.questions.isEmpty())
    }

    // ── Display ─────────────────────────────────────────────────────────────────

    @Test
    fun `only an approved question can be put on screen`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Unvetted")).id

        assertFalse(qa.displayQuestion(id), "an unapproved question must never reach the screen")
        assertNull(qa.displayedQuestion)

        qa.approveQuestion(id)
        assertTrue(qa.displayQuestion(id))
        assertEquals(id, qa.displayedQuestion?.id)
    }

    @Test
    fun `displaying a second question marks the first done`() {
        val qa = openSession()
        val first = qa.submitApproved("First")
        val second = qa.submitApproved("Second")

        qa.displayQuestion(first)
        qa.displayQuestion(second)

        assertEquals(second, qa.displayedQuestion?.id)
        assertEquals(QuestionStatus.DONE, qa.findQuestion(first)?.status, "the outgoing question should be retired automatically")
    }

    @Test
    fun `retiring the on-screen question clears the display`() {
        val qa = openSession()

        val denied = qa.submitApproved("To deny")
        qa.displayQuestion(denied)
        qa.denyQuestion(denied)
        assertNull(qa.displayedQuestion, "a denied question must leave the screen immediately")

        val done = qa.submitApproved("To finish")
        qa.displayQuestion(done)
        qa.markDone(done)
        assertNull(qa.displayedQuestion)

        val deleted = qa.submitApproved("To delete")
        qa.displayQuestion(deleted)
        qa.deleteQuestion(deleted)
        assertNull(qa.displayedQuestion)
    }

    @Test
    fun `editing the on-screen question updates what is displayed`() {
        val qa = openSession()
        val id = qa.submitApproved("Before")
        qa.displayQuestion(id)

        qa.editQuestion(id, "After")
        assertEquals("After", qa.displayedQuestion?.text, "the screen must not keep showing stale text")
    }

    @Test
    fun `the QR code and a question are mutually exclusive on screen`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")
        qa.displayQuestion(id)
        assertFalse(qa.showQRCodeOnDisplay)

        qa.toggleQRCodeDisplay()
        assertTrue(qa.showQRCodeOnDisplay)
        assertNull(qa.displayedQuestion, "showing the QR code must take the question off screen")

        qa.displayQuestion(id)
        assertFalse(qa.showQRCodeOnDisplay, "showing a question must take the QR code off screen")
    }

    @Test
    fun `clearDisplay blanks both the question and the QR code`() {
        val qa = openSession()
        qa.toggleQRCodeDisplay()
        qa.clearDisplay()
        assertNull(qa.displayedQuestion)
        assertFalse(qa.showQRCodeOnDisplay)
    }

    // ── Session lifecycle ───────────────────────────────────────────────────────

    @Test
    fun `closing the session archives the questions and resets live state`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")
        qa.displayQuestion(id)

        qa.toggleSession() // close
        assertFalse(qa.sessionActive)
        assertTrue(qa.questions.isEmpty(), "the live queue should empty when the session closes")
        assertEquals(1, qa.history.size, "closed-out questions belong in history")
        assertNull(qa.displayedQuestion, "closing the session must clear the screen")
    }

    @Test
    fun `closing the session lifts the rate limit so the next service starts clean`() {
        val qa = openSession()
        qa.submitQuestion("First", clientIp = "10.0.0.1", cooldownSeconds = 300)
        assertTrue(qa.isRateLimited("10.0.0.1", 300))

        qa.toggleSession() // close
        qa.toggleSession() // reopen
        assertFalse(qa.isRateLimited("10.0.0.1", 300))
        assertNotNull(qa.submitQuestion("Next service", clientIp = "10.0.0.1", cooldownSeconds = 300))
    }

    @Test
    fun `restoreFromHistory brings an archived session back and reopens it`() {
        val qa = openSession()
        qa.submitQuestion("Q1")
        qa.submitQuestion("Q2")
        qa.toggleSession() // close, archiving both
        assertEquals(2, qa.history.size)

        qa.restoreFromHistory()
        assertEquals(2, qa.questions.size)
        assertTrue(qa.history.isEmpty())
        assertTrue(qa.sessionActive, "restoring should leave the session usable")
    }

    @Test
    fun `clearAll empties the live queue while clearHistory empties the archive`() {
        val qa = openSession()
        qa.submitQuestion("Live")
        qa.toggleSession()  // archive it
        qa.toggleSession()  // reopen
        qa.submitQuestion("Also live")

        qa.clearAll()
        assertTrue(qa.questions.isEmpty())
        assertEquals(1, qa.history.size, "clearAll must not touch history")

        qa.clearHistory()
        assertTrue(qa.history.isEmpty())
    }

    // ── Voting ──────────────────────────────────────────────────────────────────

    @Test
    fun `only approved questions can be voted on`() {
        val qa = openSession()
        val pending = assertNotNull(qa.submitQuestion("Pending")).id
        assertFalse(qa.voteForQuestion(pending, "10.0.0.1"), "voting on an unvetted question should be refused")
        assertFalse(qa.voteForQuestion("unknown-id", "10.0.0.1"))
    }

    @Test
    fun `an upvote counts once per ip and voting again undoes it`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")

        assertTrue(qa.voteForQuestion(id, "10.0.0.1", "up"))
        assertEquals(1, qa.findQuestion(id)?.upvotes)
        assertEquals(1, qa.findQuestion(id)?.voteCount)
        assertEquals("up", qa.getVoteDirection(id, "10.0.0.1"))

        qa.voteForQuestion(id, "10.0.0.1", "up") // same direction again = undo
        assertEquals(0, qa.findQuestion(id)?.upvotes)
        assertEquals(0, qa.findQuestion(id)?.voteCount)
        assertNull(qa.getVoteDirection(id, "10.0.0.1"), "an undone vote should be forgotten entirely")
    }

    @Test
    fun `switching direction moves the vote rather than double-counting`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")

        qa.voteForQuestion(id, "10.0.0.1", "up")
        qa.voteForQuestion(id, "10.0.0.1", "down")

        val q = assertNotNull(qa.findQuestion(id))
        assertEquals(0, q.upvotes, "the original upvote should have been withdrawn")
        assertEquals(1, q.downvotes)
        assertEquals(-1, q.voteCount)
        assertEquals("down", qa.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `votes from different ips accumulate and voteCount is up minus down`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")

        qa.voteForQuestion(id, "10.0.0.1", "up")
        qa.voteForQuestion(id, "10.0.0.2", "up")
        qa.voteForQuestion(id, "10.0.0.3", "up")
        qa.voteForQuestion(id, "10.0.0.4", "down")

        val q = assertNotNull(qa.findQuestion(id))
        assertEquals(3, q.upvotes)
        assertEquals(1, q.downvotes)
        assertEquals(2, q.voteCount)
    }

    @Test
    fun `getApprovedQuestions returns only approved items, most upvoted first`() {
        val qa = openSession()
        val low = qa.submitApproved("Low")
        val high = qa.submitApproved("High")
        val mid = qa.submitApproved("Mid")
        assertNotNull(qa.submitQuestion("Still pending")) // must be excluded

        qa.voteForQuestion(high, "10.0.0.1", "up")
        qa.voteForQuestion(high, "10.0.0.2", "up")
        qa.voteForQuestion(mid, "10.0.0.1", "up")

        val ordered = qa.getApprovedQuestions()
        assertEquals(listOf(high, mid, low), ordered.map { it.id })
        assertTrue(ordered.all { it.status == QuestionStatus.APPROVED })
    }

    @Test
    fun `closing the session clears vote records`() {
        val qa = openSession()
        val id = qa.submitApproved("Q")
        qa.voteForQuestion(id, "10.0.0.1", "up")
        assertEquals("up", qa.getVoteDirection(id, "10.0.0.1"))

        qa.toggleSession() // close
        assertNull(qa.getVoteDirection(id, "10.0.0.1"), "votes must not carry over between services")
    }
}
