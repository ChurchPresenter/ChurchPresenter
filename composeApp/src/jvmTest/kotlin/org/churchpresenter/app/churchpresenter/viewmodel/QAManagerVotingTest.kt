package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QAManagerVotingTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-qa-voting-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun approvedQuestion(): Pair<QAManager, String> {
        val manager = QAManager()
        manager.toggleSession()
        val question = requireNotNull(manager.addQuestion("Where is the nursery?"))
        manager.approveQuestion(question.id)
        return manager to question.id
    }

    private fun QAManager.question(id: String): Question = requireNotNull(findQuestion(id))

    @Test
    fun `an upvote counts once`() {
        val (manager, id) = approvedQuestion()

        assertTrue(manager.voteForQuestion(id, "10.0.0.1", "up"))

        assertEquals(1, manager.question(id).upvotes)
        assertEquals(0, manager.question(id).downvotes)
        assertEquals(1, manager.question(id).voteCount)
        assertEquals("up", manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `a downvote counts against the total`() {
        val (manager, id) = approvedQuestion()

        manager.voteForQuestion(id, "10.0.0.1", "down")

        assertEquals(0, manager.question(id).upvotes)
        assertEquals(1, manager.question(id).downvotes)
        assertEquals(-1, manager.question(id).voteCount)
    }

    @Test
    fun `voting the same way twice takes the vote back`() {
        val (manager, id) = approvedQuestion()
        manager.voteForQuestion(id, "10.0.0.1", "up")

        manager.voteForQuestion(id, "10.0.0.1", "up")

        assertEquals(0, manager.question(id).upvotes)
        assertEquals(0, manager.question(id).voteCount)
        assertNull(manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `taking back a downvote restores the total`() {
        val (manager, id) = approvedQuestion()
        manager.voteForQuestion(id, "10.0.0.1", "down")

        manager.voteForQuestion(id, "10.0.0.1", "down")

        assertEquals(0, manager.question(id).downvotes)
        assertEquals(0, manager.question(id).voteCount)
        assertNull(manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `switching from up to down moves the vote rather than adding one`() {
        val (manager, id) = approvedQuestion()
        manager.voteForQuestion(id, "10.0.0.1", "up")

        manager.voteForQuestion(id, "10.0.0.1", "down")

        assertEquals(0, manager.question(id).upvotes)
        assertEquals(1, manager.question(id).downvotes)
        assertEquals(-1, manager.question(id).voteCount)
        assertEquals("down", manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `switching from down to up moves the vote rather than adding one`() {
        val (manager, id) = approvedQuestion()
        manager.voteForQuestion(id, "10.0.0.1", "down")

        manager.voteForQuestion(id, "10.0.0.1", "up")

        assertEquals(1, manager.question(id).upvotes)
        assertEquals(0, manager.question(id).downvotes)
        assertEquals(1, manager.question(id).voteCount)
    }

    @Test
    fun `two phones each get a vote`() {
        val (manager, id) = approvedQuestion()

        manager.voteForQuestion(id, "10.0.0.1", "up")
        manager.voteForQuestion(id, "10.0.0.2", "up")

        assertEquals(2, manager.question(id).upvotes)
        assertEquals(2, manager.question(id).voteCount)
    }

    @Test
    fun `a question that does not exist cannot be voted for`() {
        val (manager, _) = approvedQuestion()

        assertFalse(manager.voteForQuestion("no-such-id", "10.0.0.1", "up"))
    }

    @Test
    fun `a pending question cannot be voted for`() {
        val manager = QAManager()
        manager.toggleSession()
        val question = requireNotNull(manager.addQuestion("Not yet approved"))

        assertFalse(manager.voteForQuestion(question.id, "10.0.0.1", "up"))
        assertNull(manager.getVoteDirection(question.id, "10.0.0.1"))
    }

    @Test
    fun `a denied question cannot be voted for`() {
        val (manager, id) = approvedQuestion()
        manager.denyQuestion(id)

        assertFalse(manager.voteForQuestion(id, "10.0.0.1", "up"))
    }

    @Test
    fun `the approved list is ordered by score`() {
        val manager = QAManager()
        manager.toggleSession()
        val ids = listOf("first", "second", "third").map {
            requireNotNull(manager.addQuestion(it)).id.also { id -> manager.approveQuestion(id) }
        }
        manager.voteForQuestion(ids[1], "10.0.0.1", "up")
        manager.voteForQuestion(ids[1], "10.0.0.2", "up")
        manager.voteForQuestion(ids[2], "10.0.0.1", "up")
        manager.voteForQuestion(ids[0], "10.0.0.1", "down")

        val approved = manager.getApprovedQuestions()

        assertEquals(listOf("second", "third", "first"), approved.map { it.text })
        assertEquals(QuestionStatus.APPROVED, approved.first().status)
    }

    @Test
    fun `a phone with no cooldown configured is never rate limited`() {
        val manager = QAManager()
        manager.toggleSession()
        manager.submitQuestion("First", "", "10.0.0.1", 0, "")

        assertFalse(manager.isRateLimited("10.0.0.1", 0))
    }

    @Test
    fun `a phone that has never submitted is not rate limited`() {
        val manager = QAManager()
        manager.toggleSession()

        assertFalse(manager.isRateLimited("10.0.0.9", 3_600))
    }

    @Test
    fun `an unidentified client is not rate limited`() {
        val manager = QAManager()
        manager.toggleSession()
        manager.submitQuestion("First", "", "", 3_600, "")

        assertFalse(manager.isRateLimited("", 3_600))
    }

    @Test
    fun `a phone that just submitted is rate limited`() {
        val manager = QAManager()
        manager.toggleSession()
        manager.submitQuestion("First", "", "10.0.0.1", 3_600, "")

        assertTrue(manager.isRateLimited("10.0.0.1", 3_600))
    }

    @Test
    fun `a vote direction the phone made up counts as neither up nor down`() {
        val (manager, id) = approvedQuestion()

        manager.voteForQuestion(id, "10.0.0.1", "sideways")

        assertEquals(0, manager.question(id).upvotes)
        assertEquals(0, manager.question(id).downvotes)
        assertEquals("sideways", manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `an unidentified client has no recorded direction`() {
        val (manager, id) = approvedQuestion()

        manager.voteForQuestion(id, "", "up")

        assertNull(manager.getVoteDirection(id, "10.0.0.1"))
    }

    @Test
    fun `a phone that has not voted has no direction on a question`() {
        val (manager, id) = approvedQuestion()

        assertNull(manager.getVoteDirection(id, "10.0.0.9"))
    }

    @Test
    fun `no direction is reported for a question that does not exist`() {
        val (manager, _) = approvedQuestion()

        assertNull(manager.getVoteDirection("no-such-question", "10.0.0.1"))
    }

    @Test
    fun `a negative cooldown never rate limits`() {
        val (manager, _) = approvedQuestion()
        manager.submitQuestion("First", "", "10.0.0.1", 60, "device-1")

        assertFalse(manager.isRateLimited("10.0.0.1", -5))
    }
}
