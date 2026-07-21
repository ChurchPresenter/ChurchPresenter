package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What survives the app restarting mid-session, and what the connected phones are told.
 *
 * [QAManagerTest] covers moderation itself. This covers the two things around it that only matter
 * once other software is involved: the state file that is rewritten after every action and read
 * back in the constructor, and the event stream the companion server broadcasts over WebSocket.
 * Both are silent on failure — the file writes inside a `runCatching`, and an event nobody emits
 * simply never arrives — so neither shows up as a crash, only as a phone displaying something the
 * operator retired minutes ago.
 *
 * `user.home` is swapped per test, as in [QAManagerTest]: the manager resolves its state file at
 * construction, so a "restart" here is just a second manager built against the same temp home.
 */
class QAManagerStateTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    private val stateFile: File get() = File(tempHome, ".churchpresenter/qa_state.json")

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-qa-state-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /**
     * Waits until the saved state satisfies [predicate].
     *
     * Every action triggers its own off-thread save, so waiting for a substring is not enough: the
     * text of a question reaches the file on the save that *created* it, long before the save that
     * archived or moderated it. Each wait therefore describes the shape it is expecting. A torn
     * half-written file simply fails to parse and the poll continues.
     *
     * These tests also wait after EVERY action rather than only at the end. `QAManager.saveState()`
     * launches an independent coroutine that snapshots the state when it runs, so two actions in
     * quick succession put two writes in flight with no ordering between them — and if the older
     * one lands last, the file keeps a stale snapshot for good. Letting each save finish before the
     * next action starts sidesteps that; see the note in the class doc.
     */
    private fun awaitState(what: String, predicate: (JsonObject) -> Boolean) =
        awaitUntil(what) {
            stateFile.exists() && runCatching {
                predicate(Json.parseToJsonElement(stateFile.readText()).jsonObject)
            }.getOrDefault(false)
        }

    private fun JsonObject.entries(name: String): List<JsonObject> =
        this[name]?.jsonArray?.map { it.jsonObject }.orEmpty()

    private fun JsonObject.texts(name: String): List<String> =
        entries(name).mapNotNull { it["text"]?.jsonPrimitive?.content }

    private fun JsonObject.statuses(): List<String> =
        entries("questions").mapNotNull { it["status"]?.jsonPrimitive?.content }

    /** Waits for the state file to contain [marker] — enough when only one save can produce it. */
    private fun awaitSaved(marker: String) =
        awaitUntil("the state file to record \"$marker\"") {
            stateFile.exists() && stateFile.readText().contains(marker)
        }

    /** Events are emitted from a coroutine on the Swing event queue; draining it delivers them. */
    private fun settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    /**
     * Everything broadcast while [block] runs.
     *
     * Events are emitted from coroutines launched on the event queue, so an earlier action's
     * broadcast can still be in flight when this is called — draining the queue *before*
     * subscribing keeps someone else's event out of the window. That matters most for the tests
     * that assert an event is absent. Unconfined so the collector subscribes before [block] runs.
     */
    private fun QAManager.eventsDuring(block: () -> Unit): List<QAEvent> {
        settle()
        val seen = mutableListOf<QAEvent>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { events.collect { seen.add(it) } }
        block()
        settle()
        job.cancel()
        return seen
    }

    /**
     * A manager with an open session, whose save has landed.
     *
     * Opening the session is itself a save, so waiting for it here is not politeness — without it
     * that write races the first submit's write, and if it lands second the file keeps an empty
     * queue permanently. The state file does not exist until this first save completes, so its
     * mere existence is the signal.
     */
    private fun openSession(): QAManager = QAManager().also {
        it.toggleSession()
        awaitUntil("the opened session to be saved") { stateFile.exists() }
    }

    /** Submits and approves [text], letting each save land before the next action. */
    private fun QAManager.submitApproved(text: String): String {
        val question = assertNotNull(submitQuestion(text), "submit failed for \"$text\"")
        awaitState("\"$text\" to be saved") { it.texts("questions").contains(text) }
        assertTrue(approveQuestion(question.id))
        awaitState("\"$text\" to be approved") { state ->
            state.entries("questions").any {
                it["id"]?.jsonPrimitive?.content == question.id &&
                    it["status"]?.jsonPrimitive?.content == "APPROVED"
            }
        }
        return question.id
    }

    /** A second manager against the same home — i.e. the app restarted. */
    private fun restarted(): QAManager = QAManager()

    // ── Surviving a restart ─────────────────────────────────────────────────────

    @Test
    fun `the moderation queue survives a restart`() {
        val qa = openSession()
        qa.submitQuestion("Where is the nursery?")
        awaitState("the first question") { it.entries("questions").size == 1 }
        qa.submitApproved("How do I join a group?")
        awaitState("both questions to be saved") { it.entries("questions").size == 2 }

        val afterRestart = restarted()

        assertEquals(
            listOf("How do I join a group?", "Where is the nursery?"),
            afterRestart.questions.map { it.text }.sorted(),
            "a crash mid-service must not lose what the room already asked",
        )
    }

    @Test
    fun `each question comes back with the state it was moderated into`() {
        // Each action is allowed to finish saving before the next one starts — see the note on
        // [awaitState] for why two saves must never be in flight together.
        val qa = openSession()
        val approved = assertNotNull(qa.submitQuestion("Approved one")).id
        awaitState("the first question") { it.entries("questions").size == 1 }
        assertTrue(qa.approveQuestion(approved))
        awaitState("the approval") { it.statuses() == listOf("APPROVED") }

        val pending = assertNotNull(qa.submitQuestion("Pending one")).id
        awaitState("the second question") { it.entries("questions").size == 2 }

        val denied = assertNotNull(qa.submitQuestion("Denied one")).id
        awaitState("the third question") { it.entries("questions").size == 3 }
        assertTrue(qa.denyQuestion(denied))
        awaitState("the denial") { it.statuses().contains("DENIED") }

        val afterRestart = restarted()

        assertEquals(QuestionStatus.APPROVED, afterRestart.findQuestion(approved)?.status)
        assertEquals(QuestionStatus.PENDING, afterRestart.findQuestion(pending)?.status)
        assertEquals(QuestionStatus.DENIED, afterRestart.findQuestion(denied)?.status)
    }

    @Test
    fun `votes come back so nobody gets a second vote out of a restart`() {
        val qa = openSession()
        val id = qa.submitApproved("Should we add a second service?")
        assertTrue(qa.voteForQuestion(id, "10.0.0.5", "up"))
        awaitSaved("10.0.0.5")

        val afterRestart = restarted()

        assertEquals("up", afterRestart.getVoteDirection(id, "10.0.0.5"), "the vote record is what stops double-voting")
        assertEquals(1, afterRestart.findQuestion(id)?.upvotes)
    }

    @Test
    fun `the archive survives a restart`() {
        val qa = openSession()
        qa.submitApproved("Asked last service")

        qa.toggleSession() // closing archives the queue
        awaitState("the closed session to be archived") {
            it.texts("history") == listOf("Asked last service") && it.entries("questions").isEmpty()
        }

        val afterRestart = restarted()

        assertEquals(listOf("Asked last service"), afterRestart.history.map { it.text })
        assertTrue(afterRestart.questions.isEmpty())
    }

    @Test
    fun `a restart leaves the session closed until the operator reopens it`() {
        val qa = openSession()
        qa.submitQuestion("Before the crash")
        awaitSaved("Before the crash")

        val afterRestart = restarted()

        assertFalse(afterRestart.sessionActive, "whether the room may submit is the operator's call, not the file's")
        assertNull(afterRestart.submitQuestion("After the crash"), "submissions stay closed until reopened")
        assertEquals(1, afterRestart.questions.size, "but the queue is still there to moderate")
    }

    @Test
    fun `a restart shows nothing on screen`() {
        val qa = openSession()
        val id = qa.submitApproved("On screen right now")
        assertTrue(qa.displayQuestion(id)) // displaying is deliberately not persisted

        val afterRestart = restarted()

        assertNull(
            afterRestart.displayedQuestion,
            "the audience screen must not come back mid-question after a crash",
        )
    }

    @Test
    fun `clearing the queue is remembered`() {
        val qa = openSession()
        qa.submitApproved("Cleared away")

        qa.clearAll()
        awaitState("the cleared queue to be written") { it.entries("questions").isEmpty() }

        assertTrue(restarted().questions.isEmpty())
    }

    @Test
    fun `clearing the archive is remembered`() {
        val qa = openSession()
        qa.submitApproved("Old question")
        qa.toggleSession()
        awaitState("the session to be closed and archived") { it.entries("history").size == 1 }

        qa.clearHistory()
        awaitState("the cleared archive to be written") { it.entries("history").isEmpty() }

        assertTrue(restarted().history.isEmpty())
    }

    // ── Bad state files ─────────────────────────────────────────────────────────

    @Test
    fun `a corrupt state file starts an empty board rather than failing to open`() {
        stateFile.parentFile.mkdirs()
        stateFile.writeText("this is not json")

        val qa = restarted()

        assertTrue(qa.questions.isEmpty())
        assertTrue(qa.history.isEmpty())
        assertFalse(qa.sessionActive)
    }

    @Test
    fun `no state file at all is a clean start`() {
        assertFalse(stateFile.exists())
        assertTrue(restarted().questions.isEmpty())
    }

    @Test
    fun `a question with an unrecognised status comes back unapproved`() {
        // A file from another build could name a status this one doesn't have. Defaulting to
        // PENDING is the safe direction: the operator re-approves it, rather than something
        // unmoderated becoming displayable.
        stateFile.parentFile.mkdirs()
        stateFile.writeText(
            """{"questions":[{"id":"q1","text":"From the future","submitterName":"","submitterDeviceId":"",""" +
                """"timestamp":0,"status":"SOMETHING_NEW","voteCount":0,"upvotes":0,"downvotes":0}],""" +
                """"history":[],"votedIps":{}}"""
        )

        val qa = restarted()

        assertEquals(QuestionStatus.PENDING, qa.findQuestion("q1")?.status)
        assertFalse(qa.displayQuestion("q1"), "and it cannot be put on screen until approved")
    }

    // ── What the phones are told ────────────────────────────────────────────────

    @Test
    fun `a submitted question is broadcast`() {
        val qa = openSession()

        val events = qa.eventsDuring { qa.submitQuestion("Where is the nursery?") }

        val submitted = events.filterIsInstance<QAEvent.QuestionSubmitted>().single()
        assertEquals("Where is the nursery?", submitted.question.text)
    }

    @Test
    fun `moderation is broadcast so the phones can follow along`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Question")).id

        val events = qa.eventsDuring { qa.approveQuestion(id) }

        val updated = events.filterIsInstance<QAEvent.QuestionUpdated>().single()
        assertEquals(QuestionStatus.APPROVED, updated.question.status)
    }

    @Test
    fun `an edit is broadcast with the new text`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Typo here")).id

        val events = qa.eventsDuring { qa.editQuestion(id, "Fixed") }

        assertEquals("Fixed", events.filterIsInstance<QAEvent.QuestionUpdated>().single().question.text)
    }

    @Test
    fun `a deleted question is broadcast as denied so clients drop it`() {
        val qa = openSession()
        val id = assertNotNull(qa.submitQuestion("Remove me")).id

        val events = qa.eventsDuring { qa.deleteQuestion(id) }

        val updated = events.filterIsInstance<QAEvent.QuestionUpdated>().single()
        assertEquals(
            QuestionStatus.DENIED,
            updated.question.status,
            "there is no 'deleted' event, so a denial is how a phone learns to stop showing it",
        )
    }

    @Test
    fun `opening and closing the session is broadcast`() {
        val qa = QAManager()

        val opening = qa.eventsDuring { qa.toggleSession() }
        assertEquals(true, opening.filterIsInstance<QAEvent.SessionChanged>().single().active)

        val closing = qa.eventsDuring { qa.toggleSession() }
        assertEquals(false, closing.filterIsInstance<QAEvent.SessionChanged>().single().active)
    }

    @Test
    fun `putting a question on screen and taking it off are both broadcast`() {
        val qa = openSession()
        val id = qa.submitApproved("On screen")

        val shown = qa.eventsDuring { qa.displayQuestion(id) }
        assertEquals("On screen", shown.filterIsInstance<QAEvent.DisplayChanged>().single().question?.text)

        val cleared = qa.eventsDuring { qa.clearDisplay() }
        assertNull(cleared.filterIsInstance<QAEvent.DisplayChanged>().single().question)
    }

    @Test
    fun `restoring last session's questions reopens it for everyone`() {
        val qa = openSession()
        qa.submitApproved("Asked last time")
        qa.toggleSession()

        val events = qa.eventsDuring { qa.restoreFromHistory() }

        assertEquals(true, events.filterIsInstance<QAEvent.SessionChanged>().single().active)
        assertTrue(qa.sessionActive)
    }

    /**
     * Documents a KNOWN GAP rather than desired behaviour: [QAManager.toggleQRCodeDisplay] clears
     * the displayed question locally but emits no [QAEvent.DisplayChanged], so anything driven by
     * the event stream — a connected phone, a follower instance — is never told the question came
     * down and keeps showing it alongside the operator's QR code.
     *
     * Every other path that clears the display does emit. The fix is one `emitEvent` call; left
     * unmade here because this slate is tests only. When it is made, this flips to asserting the
     * event arrives.
     */
    @Test
    fun `switching to the QR code retires the question without telling anyone -- known gap`() {
        val qa = openSession()
        val id = qa.submitApproved("Still on the phones")
        assertTrue(qa.displayQuestion(id))

        val events = qa.eventsDuring { qa.toggleQRCodeDisplay() }

        assertNull(qa.displayedQuestion, "locally the question is down")
        assertTrue(qa.showQRCodeOnDisplay)
        assertTrue(
            events.filterIsInstance<QAEvent.DisplayChanged>().isEmpty(),
            "current behaviour: nothing is broadcast, so a phone still shows the retired question",
        )
    }
}
