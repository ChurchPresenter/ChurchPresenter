package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a phone sends and receives when someone asks a question.
 *
 * These three carry the Audience Q&A feature over HTTP: a submission and a vote come in from a
 * browser on someone's phone, and the question list goes back out. The browser side is hand-written
 * JavaScript served by the app, so nothing on that side is type-checked against these classes —
 * renaming a field here compiles perfectly and simply stops the phone working, with the failure
 * appearing as a submission that vanishes rather than as an error.
 *
 * So the field names on the wire are the contract, and that is what these pin.
 */
class QuestionWireFormatTest {

    /** Lenient, as an incoming payload is read. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Configured the way the companion server configures its own encoder — `encodeDefaults = true`.
     * That setting is load-bearing rather than cosmetic: without it kotlinx omits every field still
     * at its default, so a question with no name and no votes would go out as four fields and the
     * phone's `question.upvotes` would read as undefined. See the test at the end of this class.
     */
    private val wireJson = Json { encodeDefaults = true }

    // ── Coming in from a phone ──────────────────────────────────────────────────

    @Test
    fun `a submitted question is read from what the phone posts`() {
        val request = json.decodeFromString(
            SubmitQuestionRequest.serializer(),
            """{"text":"Where is the nursery?","name":"Sam"}""",
        )

        assertEquals("Where is the nursery?", request.text)
        assertEquals("Sam", request.name)
    }

    @Test
    fun `a question submitted without a name is still accepted`() {
        val request = json.decodeFromString(SubmitQuestionRequest.serializer(), """{"text":"Anonymous question"}""")

        assertEquals("", request.name, "asking anonymously is the default, not an error")
    }

    @Test
    fun `a submission keeps the exact words that were typed`() {
        val typed = """Why "this" & not that? — 100% curious"""
        val request = json.decodeFromString(
            SubmitQuestionRequest.serializer(),
            json.encodeToString(SubmitQuestionRequest.serializer(), SubmitQuestionRequest(text = typed)),
        )

        assertEquals(typed, request.text, "quotes and ampersands survive the round trip or the question is mangled")
    }

    @Test
    fun `the submission field names are what the phone sends`() {
        val encoded = wireJson.encodeToString(
            SubmitQuestionRequest.serializer(),
            SubmitQuestionRequest(text = "Question", name = "Sam"),
        )

        assertTrue(""""text":"Question"""" in encoded, "renaming this stops every submission: $encoded")
        assertTrue(""""name":"Sam"""" in encoded, encoded)
    }

    // ── Voting ──────────────────────────────────────────────────────────────────

    @Test
    fun `a vote names the question and the direction`() {
        val vote = json.decodeFromString(VoteRequest.serializer(), """{"questionId":"q1","direction":"down"}""")

        assertEquals("q1", vote.questionId)
        assertEquals("down", vote.direction)
    }

    @Test
    fun `a vote with no direction counts as an upvote`() {
        val vote = json.decodeFromString(VoteRequest.serializer(), """{"questionId":"q1"}""")

        assertEquals("up", vote.direction, "the common case is a thumbs-up, so it is the default")
    }

    @Test
    fun `the vote field names are what the phone sends`() {
        val encoded = wireJson.encodeToString(VoteRequest.serializer(), VoteRequest(questionId = "q1", direction = "up"))

        assertTrue(""""questionId":"q1"""" in encoded, encoded)
        assertTrue(""""direction":"up"""" in encoded, encoded)
    }

    // ── Going back out to every phone ───────────────────────────────────────────

    @Test
    fun `a question is sent out with everything a phone needs to show it`() {
        val dto = Question(
            id = "q1",
            text = "Where is the nursery?",
            submitterName = "Sam",
            submitterDeviceId = "phone-1",
            timestamp = 1_700_000_000_000,
            status = QuestionStatus.APPROVED,
            voteCount = 3,
            upvotes = 4,
            downvotes = 1,
        ).toDto()

        assertEquals("q1", dto.id)
        assertEquals("Where is the nursery?", dto.text)
        assertEquals("Sam", dto.submitterName)
        assertEquals("APPROVED", dto.status, "the status travels as its name, not its ordinal")
        assertEquals(3, dto.voteCount)
        assertEquals(4, dto.upvotes)
        assertEquals(1, dto.downvotes)
    }

    @Test
    fun `a question status travels as a name so a reordering cannot change it`() {
        QuestionStatus.entries.forEach { status ->
            val dto = Question(id = "q", text = "t", timestamp = 0L, status = status).toDto()

            assertEquals(status.name, dto.status, "an ordinal would silently shift if the enum were reordered")
        }
    }

    @Test
    fun `the outgoing field names are what the phone reads`() {
        val encoded = wireJson.encodeToString(
            QuestionDto.serializer(),
            Question(id = "q1", text = "Question", timestamp = 1_700_000_000_000).toDto(),
        )

        listOf("id", "text", "submitterName", "timestamp", "status", "voteCount", "upvotes", "downvotes").forEach {
            assertTrue(""""$it":""" in encoded, "the phone reads $it: $encoded")
        }
    }

    @Test
    fun `a question survives the round trip out and back`() {
        val original = Question(
            id = "q1",
            text = "Where is the nursery?",
            submitterName = "Sam",
            timestamp = 1_700_000_000_000,
            status = QuestionStatus.APPROVED,
            upvotes = 2,
        ).toDto()

        val restored = json.decodeFromString(
            QuestionDto.serializer(),
            json.encodeToString(QuestionDto.serializer(), original),
        )

        assertEquals(original, restored, "this is also how the Q&A state file is written and read")
    }

    /**
     * Documents WHY the server sets `encodeDefaults = true`: without it, every field still at its
     * default is left out of the payload entirely. A question nobody has voted on would arrive at
     * the phone with no `upvotes`, `downvotes` or `voteCount` at all, and the hand-written client
     * reads those directly — so this is a one-line configuration change away from a Q&A list that
     * shows blanks instead of vote counts.
     */
    @Test
    fun `without that setting the defaults vanish from the payload`() {
        val plain = Json.encodeToString(
            QuestionDto.serializer(),
            Question(id = "q1", text = "Question", timestamp = 0L).toDto(),
        )

        assertTrue(""""upvotes":""" !in plain, "a defaulted field is simply absent: $plain")
        assertTrue(
            """"upvotes":""" in wireJson.encodeToString(
                QuestionDto.serializer(),
                Question(id = "q1", text = "Question", timestamp = 0L).toDto(),
            ),
            "which is why the server's encoder is configured to keep them",
        )
    }

    @Test
    fun `a payload from a newer build is still read`() {
        val dto = json.decodeFromString(
            QuestionDto.serializer(),
            """{"id":"q1","text":"Question","timestamp":0,"status":"PENDING","fieldFromANewerBuild":true}""",
        )

        assertEquals("q1", dto.id, "an unknown field must not reject the whole payload")
    }
}

/**
 * A point on a hand-drawn shape.
 *
 * Canvas shapes with a `points` list persist through the scenes file, so this tiny pair is part of
 * that format. It has no defaults, which means a point written without both coordinates cannot be
 * read back — worth knowing, because the failure takes the whole scenes file with it.
 */
class PathPointTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a point keeps both coordinates`() {
        val point = PathPoint(x = 12.5f, y = -3.25f)

        assertEquals(12.5f, point.x)
        assertEquals(-3.25f, point.y)
    }

    @Test
    fun `a point round-trips through the scenes file format`() {
        val original = PathPoint(x = 0.125f, y = 1920f)

        val restored = json.decodeFromString(
            PathPoint.serializer(),
            json.encodeToString(PathPoint.serializer(), original),
        )

        assertEquals(original, restored, "a shape redrawn from rounded points would not match what was saved")
    }

    @Test
    fun `the coordinate names are what the scenes file uses`() {
        val encoded = json.encodeToString(PathPoint.serializer(), PathPoint(x = 1f, y = 2f))

        assertTrue(""""x":1""" in encoded, encoded)
        assertTrue(""""y":2""" in encoded, encoded)
    }

    @Test
    fun `two points at the same place are the same point`() {
        assertEquals(PathPoint(1f, 2f), PathPoint(1f, 2f), "shapes are compared by value when a scene is saved")
    }
}
