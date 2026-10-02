package org.churchpresenter.qa

import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.sharedui.utils.UsageEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val MILLIS_PER_SECOND = 1000L

/** The operator moderating the questions, and putting one -- or the join QR code -- on screen. */
interface QAModerating {
    fun approveQuestion(id: String): Boolean
    fun denyQuestion(id: String): Boolean
    fun markDone(id: String): Boolean
    fun editQuestion(id: String, newText: String): Boolean
    fun deleteQuestion(id: String): Boolean
    fun findQuestion(id: String): Question?
    fun displayQuestion(id: String): Boolean
    fun clearDisplay()
    fun toggleQRCodeDisplay()
}

/** What the congregation's phones do, and the operator adding a question of their own. */
interface QAAudienceActions {
    /**
     * Records a question as a phone's POST would.
     *
     * [timestamp] is defaulted to the wall clock and only ever passed by a caller that needs a fixed
     * one — the screenshot suite, whose images would otherwise differ every minute because each row
     * prints its own `HH:mm`. Every real caller leaves it alone.
     */
    fun submitQuestion(
        text: String,
        name: String = "",
        clientIp: String = "",
        cooldownSeconds: Int = 30,
        deviceId: String = "",
        timestamp: Long = System.currentTimeMillis(),
    ): Question?

    fun addQuestion(text: String, timestamp: Long = System.currentTimeMillis()): Question?
    fun voteForQuestion(questionId: String, clientIp: String, direction: String = "up"): Boolean
    fun getVoteDirection(questionId: String, clientIp: String): String?
    fun getApprovedQuestions(): List<Question>
    fun isRateLimited(clientIp: String, cooldownSeconds: Int): Boolean
}

/** Starting and ending a session, and what is kept between them. */
interface QASessionActions {
    fun toggleSession()
    fun clearAll()
    fun clearHistory()
    fun restoreFromHistory()
}

internal class QAModeration(private val store: QAStore) : QAModerating {

    override fun approveQuestion(id: String): Boolean = synchronized(store.lock) {
        val questions = store.questions
        val index = questions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        questions[index] = questions[index].copy(status = QuestionStatus.APPROVED)
        store.emit(QAEvent.QuestionUpdated(questions[index]))
        store.save()
        true
    }

    override fun denyQuestion(id: String): Boolean = synchronized(store.lock) {
        val questions = store.questions
        val index = questions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        questions[index] = questions[index].copy(status = QuestionStatus.DENIED)
        if (store.displayedQuestion.value?.id == id) clearDisplay()
        store.emit(QAEvent.QuestionUpdated(questions[index]))
        store.save()
        true
    }

    override fun markDone(id: String): Boolean = synchronized(store.lock) {
        val questions = store.questions
        val index = questions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        questions[index] = questions[index].copy(status = QuestionStatus.DONE)
        if (store.displayedQuestion.value?.id == id) clearDisplay()
        store.emit(QAEvent.QuestionUpdated(questions[index]))
        store.save()
        true
    }

    override fun editQuestion(id: String, newText: String): Boolean = synchronized(store.lock) {
        if (newText.isBlank()) return@synchronized false
        val questions = store.questions
        val index = questions.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized false
        questions[index] = questions[index].copy(text = newText.trim())
        if (store.displayedQuestion.value?.id == id) store.displayedQuestion.value = questions[index]
        store.emit(QAEvent.QuestionUpdated(questions[index]))
        store.save()
        true
    }

    override fun deleteQuestion(id: String): Boolean = synchronized(store.lock) {
        val question = store.questions.firstOrNull { it.id == id } ?: return@synchronized false
        if (store.displayedQuestion.value?.id == id) clearDisplay()
        store.questions.removeAll { it.id == id }
        store.emit(QAEvent.QuestionUpdated(question.copy(status = QuestionStatus.DENIED)))
        store.save()
        true
    }

    override fun findQuestion(id: String): Question? = store.questions.firstOrNull { it.id == id }

    override fun displayQuestion(id: String): Boolean = synchronized(store.lock) {
        val question = store.questions.firstOrNull { it.id == id && it.status == QuestionStatus.APPROVED }
            ?: return@synchronized false
        // Auto-mark previous displayed question as done
        val prevId = store.displayedQuestion.value?.id
        if (prevId != null && prevId != id) {
            markDone(prevId)
        }
        store.displayedQuestion.value = question
        store.showQRCodeOnDisplay.value = false
        store.emit(QAEvent.DisplayChanged(question))
        true
    }

    override fun clearDisplay(): Unit = synchronized(store.lock) {
        store.displayedQuestion.value = null
        store.showQRCodeOnDisplay.value = false
        store.emit(QAEvent.DisplayChanged(null))
    }

    override fun toggleQRCodeDisplay(): Unit = synchronized(store.lock) {
        store.showQRCodeOnDisplay.value = !store.showQRCodeOnDisplay.value
        // Taking the question down has to be announced, or a connected phone and any follower go on
        // showing a question the operator has already retired.
        //
        // Emitted inline rather than by calling clearDisplay(): that also sets showQRCodeOnDisplay
        // back to false, which would undo the toggle being performed here.
        if (store.showQRCodeOnDisplay.value && store.displayedQuestion.value != null) {
            store.displayedQuestion.value = null
            store.emit(QAEvent.DisplayChanged(null))
        }
    }
}

internal class QAAudience(private val store: QAStore) : QAAudienceActions {

    override fun submitQuestion(
        text: String,
        name: String,
        clientIp: String,
        cooldownSeconds: Int,
        deviceId: String,
        timestamp: Long,
    ): Question? = synchronized(store.lock) {
        if (!store.sessionActive.value || text.isBlank()) return@synchronized null

        // Cooldown check
        if (clientIp.isNotEmpty() && cooldownSeconds > 0) {
            val now = System.currentTimeMillis()
            val lastTime = store.lastSubmission[clientIp]
            if (lastTime != null && (now - lastTime) < cooldownSeconds * MILLIS_PER_SECOND) return@synchronized null
            store.lastSubmission[clientIp] = now
        }

        val question = Question(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            submitterName = name.trim(),
            submitterDeviceId = deviceId,
            timestamp = timestamp
        )
        store.questions.add(question)
        store.emit(QAEvent.QuestionSubmitted(question))
        store.save()
        store.usage.record(UsageEvent.QA_QUESTION_RECEIVED)
        question
    }

    override fun addQuestion(text: String, timestamp: Long): Question? = synchronized(store.lock) {
        if (text.isBlank()) return@synchronized null
        val question = Question(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            timestamp = timestamp
        )
        store.questions.add(question)
        store.emit(QAEvent.QuestionSubmitted(question))
        store.save()
        question
    }

    override fun voteForQuestion(questionId: String, clientIp: String, direction: String): Boolean =
        synchronized(store.lock) {
            val questions = store.questions
            val index = questions.indexOfFirst { it.id == questionId }
            if (index < 0) return@synchronized false
            val question = questions[index]
            if (question.status != QuestionStatus.APPROVED) return@synchronized false
            val votes = store.votedIps.getOrPut(questionId) { ConcurrentHashMap() }
            val existing = votes[clientIp]
            // Calculate upvote/downvote changes
            var upDelta = 0
            var downDelta = 0
            val isUndo = existing == direction
            when {
                isUndo && direction == "up" -> upDelta = -1
                isUndo && direction == "down" -> downDelta = -1
                existing == null && direction == "up" -> upDelta = 1
                existing == null && direction == "down" -> downDelta = 1
                existing == "up" && direction == "down" -> { upDelta = -1; downDelta = 1 }
                existing == "down" && direction == "up" -> { downDelta = -1; upDelta = 1 }
            }
            if (isUndo) votes.remove(clientIp) else votes[clientIp] = direction
            val newUp = question.upvotes + upDelta
            val newDown = question.downvotes + downDelta
            questions[index] = question.copy(
                upvotes = newUp,
                downvotes = newDown,
                voteCount = newUp - newDown
            )
            store.emit(QAEvent.QuestionUpdated(questions[index]))
            store.save()
            true
        }

    override fun getVoteDirection(questionId: String, clientIp: String): String? =
        store.votedIps[questionId]?.get(clientIp)

    override fun getApprovedQuestions(): List<Question> =
        store.questions
            .filter { it.status == QuestionStatus.APPROVED }
            .sortedByDescending { it.voteCount }

    override fun isRateLimited(clientIp: String, cooldownSeconds: Int): Boolean {
        if (clientIp.isEmpty() || cooldownSeconds <= 0) return false
        val now = System.currentTimeMillis()
        val lastTime = store.lastSubmission[clientIp] ?: return false
        return (now - lastTime) < cooldownSeconds * MILLIS_PER_SECOND
    }
}

internal class QASessionControl(
    private val store: QAStore,
    private val moderation: QAModeration,
) : QASessionActions {

    override fun toggleSession(): Unit = synchronized(store.lock) {
        store.sessionActive.value = !store.sessionActive.value
        if (store.sessionActive.value) store.usage.record(UsageEvent.QA_SESSION_STARTED)
        if (!store.sessionActive.value) {
            store.showQRCodeOnDisplay.value = false
            store.history.addAll(store.questions)
            store.questions.clear()
            moderation.clearDisplay()
            store.lastSubmission.clear()
            store.votedIps.clear()
        }
        store.emit(QAEvent.SessionChanged(store.sessionActive.value))
        store.save()
    }

    override fun clearAll(): Unit = synchronized(store.lock) {
        moderation.clearDisplay()
        store.questions.clear()
        store.votedIps.clear()
        store.save()
    }

    override fun clearHistory(): Unit = synchronized(store.lock) {
        store.history.clear()
        store.save()
    }

    override fun restoreFromHistory(): Unit = synchronized(store.lock) {
        store.questions.addAll(store.history)
        store.history.clear()
        store.sessionActive.value = true
        store.emit(QAEvent.SessionChanged(true))
        store.save()
    }
}
