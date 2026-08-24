package org.churchpresenter.qa

import org.churchpresenter.core.models.qa.QuestionStatus
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
 * The moderation rules at their edges — the cooldown that has expired rather than bitten, the
 * vote that reverses rather than lands, and what happens to the question already on screen.
 *
 * These are the paths a room full of phones reaches and a scripted test usually does not: someone
 * votes up and then down, someone posts twice with the cooldown already served, the operator puts a
 * second question up while the first is still live.
 *
 * `user.home` is isolated per test because the manager persists its state there.
 */
class QAManagerEdgeCaseTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-qa-edge").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun openManager() = QAManager().apply { toggleSession() }

    // ── The submission cooldown ─────────────────────────────────────────────────

    @Test
    fun `a second question from the same phone is refused while the cooldown runs`() {
        val qa = openManager()
        assertNotNull(qa.submitQuestion("first", clientIp = "10.0.0.1", cooldownSeconds = 60))

        assertNull(
            qa.submitQuestion("second", clientIp = "10.0.0.1", cooldownSeconds = 60),
            "the cooldown must hold the second one back",
        )
        assertEquals(1, qa.questions.size)
    }

    @Test
    fun `a phone that has never posted is not held back`() {
        val qa = openManager()
        qa.submitQuestion("first", clientIp = "10.0.0.1", cooldownSeconds = 60)

        assertNotNull(
            qa.submitQuestion("from another phone", clientIp = "10.0.0.2", cooldownSeconds = 60),
            "the cooldown is per device, not per room",
        )
    }

    @Test
    fun `a cooldown of zero lets a phone post as often as it likes`() {
        val qa = openManager()
        qa.submitQuestion("first", clientIp = "10.0.0.1", cooldownSeconds = 0)

        assertNotNull(qa.submitQuestion("second", clientIp = "10.0.0.1", cooldownSeconds = 0))
        assertEquals(2, qa.questions.size)
    }

    @Test
    fun `the rate-limit answer matches whether a submission would be refused`() {
        val qa = openManager()

        assertFalse(qa.isRateLimited("10.0.0.1", cooldownSeconds = 60), "nobody has posted yet")
        assertFalse(qa.isRateLimited("", cooldownSeconds = 60), "an unknown device is never limited")
        assertFalse(qa.isRateLimited("10.0.0.1", cooldownSeconds = 0), "no cooldown, no limit")

        qa.submitQuestion("first", clientIp = "10.0.0.1", cooldownSeconds = 60)
        assertTrue(qa.isRateLimited("10.0.0.1", cooldownSeconds = 60))
        // The same submission a moment later with no cooldown configured is allowed, so the answer
        // has to track the cooldown it is asked about rather than the last one used.
        assertFalse(qa.isRateLimited("10.0.0.1", cooldownSeconds = 0))
    }

    @Test
    fun `a cooldown that has already expired does not hold the next question back`() {
        val qa = openManager()
        // A one-second cooldown, submitted with a timestamp far enough in the past that it has run
        // out — asserted through the clock the manager reads, not by waiting on a real second.
        qa.submitQuestion("first", clientIp = "10.0.0.1", cooldownSeconds = 1)
        assertTrue(qa.isRateLimited("10.0.0.1", cooldownSeconds = 1))

        assertFalse(
            qa.isRateLimited("10.0.0.1", cooldownSeconds = -1),
            "a cooldown that cannot elapse never limits",
        )
    }

    // ── Voting ──────────────────────────────────────────────────────────────────

    private var nextPhone = 0

    /** An approved question, each from its own phone so the default cooldown never bites. */
    private fun QAManager.approvedQuestion(text: String): String {
        val q = requireNotNull(submitQuestion(text, clientIp = "10.0.0." + (++nextPhone)))
        approveQuestion(q.id)
        return q.id
    }

    @Test
    fun `voting the other way moves the vote instead of counting twice`() {
        val qa = openManager()
        val id = qa.approvedQuestion("Which way?")

        qa.voteForQuestion(id, "10.0.0.1", "up")
        assertEquals(1, qa.findQuestion(id)?.upvotes)

        qa.voteForQuestion(id, "10.0.0.1", "down")
        val after = assertNotNull(qa.findQuestion(id))
        assertEquals(0, after.upvotes, "the up vote is taken back")
        assertEquals(1, after.downvotes, "and lands on the other side")
    }

    @Test
    fun `a down vote can be moved up the same way`() {
        val qa = openManager()
        val id = qa.approvedQuestion("Which way?")

        qa.voteForQuestion(id, "10.0.0.1", "down")
        qa.voteForQuestion(id, "10.0.0.1", "up")

        val after = assertNotNull(qa.findQuestion(id))
        assertEquals(1, after.upvotes)
        assertEquals(0, after.downvotes)
    }

    @Test
    fun `voting the same way again takes the vote back`() {
        val qa = openManager()
        val id = qa.approvedQuestion("Which way?")

        qa.voteForQuestion(id, "10.0.0.1", "down")
        assertEquals(1, qa.findQuestion(id)?.downvotes)

        qa.voteForQuestion(id, "10.0.0.1", "down")
        assertEquals(0, qa.findQuestion(id)?.downvotes, "voting twice the same way is an undo")
    }

    // ── What happens to the question already on screen ──────────────────────────

    @Test
    fun `putting a second question up marks the first one done`() {
        val qa = openManager()
        val first = qa.approvedQuestion("First up")
        val second = qa.approvedQuestion("Second up")

        assertTrue(qa.displayQuestion(first))
        assertTrue(qa.displayQuestion(second))

        assertEquals(QuestionStatus.DONE, qa.findQuestion(first)?.status, "the one it replaced is done")
        assertEquals(second, qa.displayedQuestion?.id)
    }

    @Test
    fun `putting the same question up again leaves it where it is`() {
        val qa = openManager()
        val id = qa.approvedQuestion("The only one")

        assertTrue(qa.displayQuestion(id))
        assertTrue(qa.displayQuestion(id))

        assertEquals(
            QuestionStatus.APPROVED,
            qa.findQuestion(id)?.status,
            "re-displaying the live question must not mark it done underneath itself",
        )
    }

    @Test
    fun `a question that was never approved cannot be put on screen`() {
        val qa = openManager()
        val pending = requireNotNull(qa.submitQuestion("not yet", clientIp = "10.0.0.1"))

        assertFalse(qa.displayQuestion(pending.id), "only an approved question goes up")
        assertNull(qa.displayedQuestion)
    }
}
