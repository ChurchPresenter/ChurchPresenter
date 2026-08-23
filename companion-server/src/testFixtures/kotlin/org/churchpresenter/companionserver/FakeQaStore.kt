package org.churchpresenter.companionserver

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus

/**
 * An in-memory [QaModeration] for driving the Q&A routes.
 *
 * The real one is the app's `QAManager`, which holds Compose snapshot state and persists to
 * `~/.churchpresenter/qa_state.json` — neither of which this module can reach, and neither of which
 * the routes care about. What the routes care about is the answer to each call, so this gives real
 * answers: a submission is refused when the session is closed or the IP is inside its cooldown, a
 * vote is one per IP per question and reversible, and only an approved question can be displayed.
 *
 * `QAManager`'s own behaviour stays pinned by `CompanionServerQaTest` and
 * `CompanionServerQaModerationTest` in `:composeApp`, which drive the real one over the same routes.
 * This exists so the routes are also testable from the side that owns them.
 *
 * Timestamps come from [now], which a test advances by hand — nothing here reads the wall clock.
 */
class FakeQaStore(
    override var sessionActive: Boolean = true,
    /** The clock the cooldown is measured against. Advance it rather than sleeping. */
    var now: Long = 1_000_000L,
) : QaModeration {

    private val items = mutableListOf<Question>()
    private val lastSubmission = mutableMapOf<String, Long>()
    private val votes = mutableMapOf<String, MutableMap<String, String>>()
    private var nextId = 1

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<*> = _events

    override val questions: List<Question> get() = items.toList()

    override var displayedQuestion: Question? = null
        private set

    /** Seeds a question directly, bypassing the session and cooldown checks. */
    fun seed(text: String, status: QuestionStatus = QuestionStatus.PENDING): Question {
        val q = Question(id = "q${nextId++}", text = text, timestamp = now, status = status)
        items += q
        return q
    }

    override fun submitQuestion(
        text: String,
        name: String,
        clientIp: String,
        cooldownSeconds: Int,
        deviceId: String,
        timestamp: Long,
    ): Question? {
        if (!sessionActive || text.isBlank()) return null
        if (isRateLimited(clientIp, cooldownSeconds)) return null
        if (clientIp.isNotEmpty() && cooldownSeconds > 0) lastSubmission[clientIp] = now
        val q = Question(
            id = "q${nextId++}",
            text = text.trim(),
            submitterName = name.trim(),
            submitterDeviceId = deviceId,
            timestamp = timestamp,
        )
        items += q
        _events.tryEmit(Unit)
        return q
    }

    override fun addQuestion(text: String, timestamp: Long): Question? {
        if (!sessionActive || text.isBlank()) return null
        val q = Question(id = "q${nextId++}", text = text.trim(), timestamp = timestamp)
        items += q
        _events.tryEmit(Unit)
        return q
    }

    override fun findQuestion(id: String): Question? = items.firstOrNull { it.id == id }

    override fun getApprovedQuestions(): List<Question> =
        items.filter { it.status == QuestionStatus.APPROVED }

    override fun approveQuestion(id: String) = setStatus(id, QuestionStatus.APPROVED)
    override fun denyQuestion(id: String) = setStatus(id, QuestionStatus.DENIED)
    override fun markDone(id: String) = setStatus(id, QuestionStatus.DONE)

    override fun editQuestion(id: String, newText: String): Boolean {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0 || newText.isBlank()) return false
        items[i] = items[i].copy(text = newText.trim())
        _events.tryEmit(Unit)
        return true
    }

    override fun deleteQuestion(id: String): Boolean {
        val q = items.firstOrNull { it.id == id } ?: return false
        items.remove(q)
        if (displayedQuestion?.id == id) displayedQuestion = null
        _events.tryEmit(Unit)
        return true
    }

    override fun displayQuestion(id: String): Boolean {
        val q = items.firstOrNull { it.id == id && it.status == QuestionStatus.APPROVED } ?: return false
        displayedQuestion = q
        _events.tryEmit(Unit)
        return true
    }

    override fun clearDisplay() {
        displayedQuestion = null
        _events.tryEmit(Unit)
    }

    override fun clearAll() {
        items.clear()
        votes.clear()
        displayedQuestion = null
        _events.tryEmit(Unit)
    }

    override fun voteForQuestion(questionId: String, clientIp: String, direction: String): Boolean {
        val i = items.indexOfFirst { it.id == questionId }
        if (i < 0) return false
        val perIp = votes.getOrPut(questionId) { mutableMapOf() }
        val existing = perIp[clientIp]
        var up = items[i].upvotes
        var down = items[i].downvotes
        // Same direction twice is an undo, the opposite direction moves the vote across.
        when {
            existing == direction -> {
                perIp.remove(clientIp)
                if (direction == "up") up-- else down--
            }
            existing != null -> {
                perIp[clientIp] = direction
                if (direction == "up") { up++; down-- } else { down++; up-- }
            }
            else -> {
                perIp[clientIp] = direction
                if (direction == "up") up++ else down++
            }
        }
        items[i] = items[i].copy(upvotes = up, downvotes = down, voteCount = up - down)
        _events.tryEmit(Unit)
        return true
    }

    override fun getVoteDirection(questionId: String, clientIp: String): String? =
        votes[questionId]?.get(clientIp)

    override fun isRateLimited(clientIp: String, cooldownSeconds: Int): Boolean {
        if (clientIp.isEmpty() || cooldownSeconds <= 0) return false
        val last = lastSubmission[clientIp] ?: return false
        return now - last < cooldownSeconds * MILLIS_PER_SECOND
    }

    private fun setStatus(id: String, status: QuestionStatus): Boolean {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return false
        items[i] = items[i].copy(status = status)
        if (displayedQuestion?.id == id && status != QuestionStatus.APPROVED) displayedQuestion = null
        _events.tryEmit(Unit)
        return true
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
