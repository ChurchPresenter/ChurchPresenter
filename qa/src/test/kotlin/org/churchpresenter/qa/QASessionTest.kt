package org.churchpresenter.qa

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Named Q&A sessions: each one is started with a name, filed on its own when it stops, and resumed
 * on its own -- never lumped together with the sessions before it.
 *
 * Every manager is built while `user.home` points at a throwaway directory, as in [QAManagerTest]:
 * the manager reads and writes its state file there.
 */
class QASessionTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private val managers = mutableListOf<QAManager>()

    private val stateFile: File get() = File(tempHome, ".churchpresenter/qa_state.json")

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-qa-session-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        joinSaves()
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private fun manager(): QAManager = QAManager().also { managers += it }

    private fun joinSaves() = runBlocking { managers.forEach { it.awaitPendingSave() } }

    // ── Starting ────────────────────────────────────────────────────────────────

    @Test
    fun `a session starts under the name it is given`() {
        val qa = manager()

        qa.startSession("  Sunday service  ")

        assertTrue(qa.sessionActive)
        assertEquals("Sunday service", qa.sessionName, "trimmed")
    }

    @Test
    fun `a session given no name is named for when it started`() {
        val qa = manager()
        val start = LocalDateTime.of(2026, 11, 10, 9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

        qa.startSession("   ", now = start)

        assertEquals(defaultSessionName(start), qa.sessionName)
    }

    @Test
    fun `the default name is the start time in the machine's own format`() {
        val start = LocalDateTime.of(2026, 11, 10, 9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()

        val name = defaultSessionName(start, ZoneOffset.UTC)

        assertTrue("2026" in name && "30" in name, "names the date and the time: $name")
    }

    @Test
    fun `starting while a session runs changes nothing`() {
        val qa = manager()
        qa.startSession("First")

        qa.startSession("Second")

        assertEquals("First", qa.sessionName)
    }

    @Test
    fun `stopping while no session runs files nothing`() {
        val qa = manager()

        qa.stopSession()

        assertTrue(qa.sessions.isEmpty())
    }

    // ── Filing ──────────────────────────────────────────────────────────────────

    @Test
    fun `stopping files the questions under the session's name, newest session first`() {
        val qa = manager()
        qa.startSession("Morning", now = 1_000L)
        qa.askAll("Morning question")
        qa.stopSession(now = 2_000L)
        qa.startSession("Evening", now = 3_000L)
        qa.askAll("Evening question")
        qa.stopSession(now = 4_000L)

        assertEquals(listOf("Evening", "Morning"), qa.sessions.map { it.name })
        val morning = qa.sessions.last()
        assertEquals(listOf("Morning question"), morning.questions.map { it.text })
        assertEquals(1_000L, morning.startedAt)
        assertEquals(2_000L, morning.endedAt)
        assertEquals("", qa.sessionName, "nothing is running")
    }

    @Test
    fun `a session nobody asked anything in is not filed`() {
        val qa = manager()
        qa.startSession("Quiet")

        qa.stopSession()

        assertTrue(qa.sessions.isEmpty())
    }

    // ── Resuming and deleting ───────────────────────────────────────────────────

    @Test
    fun `resuming brings back only that session, under its own name`() {
        val qa = manager()
        qa.startSession("Morning")
        qa.askAll("Morning question")
        qa.stopSession()
        qa.startSession("Evening")
        qa.askAll("Evening question")
        qa.stopSession()
        val morning = qa.sessions.first { it.name == "Morning" }

        qa.resumeSession(morning.id)

        assertTrue(qa.sessionActive)
        assertEquals("Morning", qa.sessionName)
        assertEquals(listOf("Morning question"), qa.questions.map { it.text })
        assertEquals(listOf("Evening"), qa.sessions.map { it.name }, "the other session stays filed")
    }

    @Test
    fun `resuming while a session runs files the running one first`() {
        val qa = manager()
        qa.startSession("Earlier")
        qa.askAll("Earlier question")
        qa.stopSession()
        qa.startSession("Now")
        qa.askAll("Question now")

        qa.resumeSession(qa.sessions.single().id)

        assertEquals("Earlier", qa.sessionName)
        assertEquals(listOf("Earlier question"), qa.questions.map { it.text }, "the two never merge")
        assertEquals(listOf("Now"), qa.sessions.map { it.name })
    }

    @Test
    fun `a resumed session is filed under the same id when it stops again`() {
        val qa = manager()
        qa.startSession("Sunday")
        qa.askAll("Question")
        qa.stopSession()
        val id = qa.sessions.single().id

        qa.resumeSession(id)
        qa.stopSession()

        assertEquals(id, qa.sessions.single().id)
    }

    @Test
    fun `resuming a session that is not there changes nothing`() {
        val qa = manager()

        qa.resumeSession("no-such-session")

        assertFalse(qa.sessionActive)
    }

    @Test
    fun `deleting one session leaves the others`() {
        val qa = manager()
        qa.startSession("Keep")
        qa.askAll("Kept")
        qa.stopSession()
        qa.startSession("Drop")
        qa.askAll("Dropped")
        qa.stopSession()

        qa.deleteSession(qa.sessions.first { it.name == "Drop" }.id)

        assertEquals(listOf("Keep"), qa.sessions.map { it.name })
    }

    // ── What is kept on disk ────────────────────────────────────────────────────

    @Test
    fun `the questions kept before sessions had names come back as one session`() {
        stateFile.parentFile.mkdirs()
        stateFile.writeText(
            """{"questions":[],"history":[""" +
                """{"id":"h1","text":"Old one","timestamp":100,"status":"DONE"},""" +
                """{"id":"h2","text":"Old two","timestamp":200,"status":"DENIED"}""" +
                """],"votedIps":{}}"""
        )

        val qa = manager()

        val session = qa.sessions.single()
        assertEquals(LEGACY_HISTORY_SESSION_NAME, session.name)
        assertEquals(listOf("Old one", "Old two"), session.questions.map { it.text })
        assertEquals(100L, session.startedAt)
        assertEquals(200L, session.endedAt)
    }

    @Test
    fun `finished sessions and their names survive a restart`() {
        val qa = manager()
        qa.startSession("Sunday service")
        qa.askAll("Kept for next week")
        qa.stopSession()
        joinSaves()

        val afterRestart = manager()

        assertEquals("Sunday service", afterRestart.sessions.single().name)
        assertEquals(listOf("Kept for next week"), afterRestart.archived.map { it.text })
        assertFalse("\"history\":[{" in stateFile.readText(), "the old flat archive is no longer written")
    }

    @Test
    fun `a session open when the app quit is filed with its name, ready to resume`() {
        val qa = manager()
        qa.startSession("Interrupted")
        qa.askAll("Asked before the crash")
        joinSaves()

        val afterRestart = manager()
        afterRestart.resumeNewest()

        assertEquals("Interrupted", afterRestart.sessionName)
        assertEquals(listOf("Asked before the crash"), afterRestart.questions.map { it.text })
    }

    // ── Export lines ────────────────────────────────────────────────────────────

    @Test
    fun `a question over several lines survives being written to one line and read back`() {
        val text = "First line\nДругая строка\r\nA \\n that was typed"

        val line = encodeQuestionLine(text)

        assertFalse('\n' in line, "one question, one line: $line")
        assertEquals(text.replace("\r\n", "\n"), decodeQuestionLine(line))
    }

    @Test
    fun `a line with no escapes reads back as it is`() {
        assertEquals("Plain question?", decodeQuestionLine("Plain question?"))
    }
}
