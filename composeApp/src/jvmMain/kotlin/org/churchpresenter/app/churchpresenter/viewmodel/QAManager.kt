package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.models.Question
import org.churchpresenter.app.churchpresenter.models.QuestionDto
import org.churchpresenter.app.churchpresenter.models.QuestionStatus
import org.churchpresenter.app.churchpresenter.models.toDto
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class QAState(
    val questions: List<QuestionDto> = emptyList(),
    val history: List<QuestionDto> = emptyList(),
    val votedIps: Map<String, Map<String, String>> = emptyMap()
)

class QAManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val stateFile = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")

    // ── Questions ────────────────────────────────────────────────────
    private val _questions = mutableStateListOf<Question>()
    val questions: List<Question> get() = _questions

    // ── History (previous sessions) ─────────────────────────────────
    private val _history = mutableStateListOf<Question>()
    val history: List<Question> get() = _history

    // ── Session state ────────────────────────────────────────────────
    private val _sessionActive = mutableStateOf(false)
    val sessionActive: Boolean get() = _sessionActive.value

    // ── Display state ────────────────────────────────────────────────
    private val _displayedQuestion = mutableStateOf<Question?>(null)
    val displayedQuestion: Question? get() = _displayedQuestion.value

    private val _showQRCodeOnDisplay = mutableStateOf(false)
    val showQRCodeOnDisplay: Boolean get() = _showQRCodeOnDisplay.value

    // ── Rate limiting (IP -> last submission timestamp) ────────────────
    private val _lastSubmission = ConcurrentHashMap<String, Long>()

    // ── Voting (questionId -> map of IP -> direction "up"/"down") ─────
    private val _votedIps = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()


    // ── Change events (for WebSocket broadcasts) ─────────────────────
    private val _events = MutableSharedFlow<QAEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<QAEvent> = _events

    init {
        loadState()
    }

    // ── Actions ──────────────────────────────────────────────────────

    fun submitQuestion(text: String, name: String = "", clientIp: String = "", cooldownSeconds: Int = 30): Question? {
        if (!_sessionActive.value || text.isBlank()) return null

        // Cooldown check
        if (clientIp.isNotEmpty() && cooldownSeconds > 0) {
            val now = System.currentTimeMillis()
            val lastTime = _lastSubmission[clientIp]
            if (lastTime != null && (now - lastTime) < cooldownSeconds * 1000L) return null
            _lastSubmission[clientIp] = now
        }

        val question = Question(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            submitterName = name.trim(),
            timestamp = System.currentTimeMillis()
        )
        _questions.add(question)
        emitEvent(QAEvent.QuestionSubmitted(question))
        saveState()
        return question
    }

    fun addQuestion(text: String): Question? {
        if (text.isBlank()) return null
        val question = Question(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        _questions.add(question)
        emitEvent(QAEvent.QuestionSubmitted(question))
        saveState()
        return question
    }

    fun approveQuestion(id: String): Boolean {
        val index = _questions.indexOfFirst { it.id == id }
        if (index < 0) return false
        _questions[index] = _questions[index].copy(status = QuestionStatus.APPROVED)
        emitEvent(QAEvent.QuestionUpdated(_questions[index]))
        saveState()
        return true
    }

    fun denyQuestion(id: String): Boolean {
        val index = _questions.indexOfFirst { it.id == id }
        if (index < 0) return false
        _questions[index] = _questions[index].copy(status = QuestionStatus.DENIED)
        if (_displayedQuestion.value?.id == id) clearDisplay()
        emitEvent(QAEvent.QuestionUpdated(_questions[index]))
        saveState()
        return true
    }

    fun markDone(id: String): Boolean {
        val index = _questions.indexOfFirst { it.id == id }
        if (index < 0) return false
        _questions[index] = _questions[index].copy(status = QuestionStatus.DONE)
        if (_displayedQuestion.value?.id == id) clearDisplay()
        emitEvent(QAEvent.QuestionUpdated(_questions[index]))
        saveState()
        return true
    }

    fun editQuestion(id: String, newText: String): Boolean {
        if (newText.isBlank()) return false
        val index = _questions.indexOfFirst { it.id == id }
        if (index < 0) return false
        _questions[index] = _questions[index].copy(text = newText.trim())
        if (_displayedQuestion.value?.id == id) _displayedQuestion.value = _questions[index]
        emitEvent(QAEvent.QuestionUpdated(_questions[index]))
        saveState()
        return true
    }

    fun deleteQuestion(id: String): Boolean {
        val question = _questions.firstOrNull { it.id == id } ?: return false
        if (_displayedQuestion.value?.id == id) clearDisplay()
        _questions.removeAll { it.id == id }
        emitEvent(QAEvent.QuestionUpdated(question.copy(status = QuestionStatus.DENIED)))
        saveState()
        return true
    }

    fun displayQuestion(id: String): Boolean {
        val question = _questions.firstOrNull { it.id == id && it.status == QuestionStatus.APPROVED }
            ?: return false
        // Auto-mark previous displayed question as done
        val prevId = _displayedQuestion.value?.id
        if (prevId != null && prevId != id) {
            markDone(prevId)
        }
        _displayedQuestion.value = question
        _showQRCodeOnDisplay.value = false
        emitEvent(QAEvent.DisplayChanged(question))
        return true
    }

    fun clearDisplay() {
        _displayedQuestion.value = null
        _showQRCodeOnDisplay.value = false
        emitEvent(QAEvent.DisplayChanged(null))
    }

    fun toggleQRCodeDisplay() {
        _showQRCodeOnDisplay.value = !_showQRCodeOnDisplay.value
        if (_showQRCodeOnDisplay.value) _displayedQuestion.value = null
    }

    fun toggleSession() {
        _sessionActive.value = !_sessionActive.value
        if (!_sessionActive.value) {
            _showQRCodeOnDisplay.value = false
            _history.addAll(_questions)
            _questions.clear()
            clearDisplay()
            _lastSubmission.clear()
            _votedIps.clear()
        }
        emitEvent(QAEvent.SessionChanged(_sessionActive.value))
        saveState()
    }

    fun clearAll() {
        clearDisplay()
        _questions.clear()
        _votedIps.clear()
        saveState()
    }

    fun clearHistory() {
        _history.clear()
        saveState()
    }

    fun restoreFromHistory() {
        _questions.addAll(_history)
        _history.clear()
        _sessionActive.value = true
        emitEvent(QAEvent.SessionChanged(true))
        saveState()
    }

    fun findQuestion(id: String): Question? = _questions.firstOrNull { it.id == id }

    // ── Voting ───────────────────────────────────────────────────────

    fun voteForQuestion(questionId: String, clientIp: String, direction: String = "up"): Boolean {
        val index = _questions.indexOfFirst { it.id == questionId }
        if (index < 0) return false
        val question = _questions[index]
        if (question.status != QuestionStatus.APPROVED) return false
        val votes = _votedIps.getOrPut(questionId) { ConcurrentHashMap() }
        val existing = votes[clientIp]
        if (existing == direction) return false // already voted same direction
        // Calculate upvote/downvote changes
        var upDelta = 0
        var downDelta = 0
        when {
            existing == null && direction == "up" -> upDelta = 1
            existing == null && direction == "down" -> downDelta = 1
            existing == "up" && direction == "down" -> { upDelta = -1; downDelta = 1 }
            existing == "down" && direction == "up" -> { downDelta = -1; upDelta = 1 }
        }
        votes[clientIp] = direction
        val newUp = question.upvotes + upDelta
        val newDown = question.downvotes + downDelta
        _questions[index] = question.copy(
            upvotes = newUp,
            downvotes = newDown,
            voteCount = newUp - newDown
        )
        emitEvent(QAEvent.QuestionUpdated(_questions[index]))
        saveState()
        return true
    }

    fun getVoteDirection(questionId: String, clientIp: String): String? {
        return _votedIps[questionId]?.get(clientIp)
    }

    fun getApprovedQuestions(): List<Question> {
        return _questions
            .filter { it.status == QuestionStatus.APPROVED }
            .sortedByDescending { it.voteCount }
    }

    fun isRateLimited(clientIp: String, cooldownSeconds: Int): Boolean {
        if (clientIp.isEmpty() || cooldownSeconds <= 0) return false
        val now = System.currentTimeMillis()
        val lastTime = _lastSubmission[clientIp] ?: return false
        return (now - lastTime) < cooldownSeconds * 1000L
    }

    // ── Persistence ─────────────────────────────────────────────────

    private fun saveState() {
        scope.launch(Dispatchers.IO) {
            try {
                val state = QAState(
                    questions = _questions.map { it.toDto() },
                    history = _history.map { it.toDto() },
                    votedIps = _votedIps.mapValues { entry -> entry.value.toMap() }
                )
                stateFile.parentFile?.mkdirs()
                stateFile.writeText(json.encodeToString(QAState.serializer(), state))
            } catch (_: Exception) { }
        }
    }

    private fun loadState() {
        try {
            if (!stateFile.exists()) return
            val state = json.decodeFromString(QAState.serializer(), stateFile.readText())
            _questions.addAll(state.questions.map { it.toQuestion() })
            _history.addAll(state.history.map { it.toQuestion() })
            state.votedIps.forEach { (qId, ipMap) ->
                val map = ConcurrentHashMap<String, String>()
                map.putAll(ipMap)
                _votedIps[qId] = map
            }
        } catch (_: Exception) { }
    }

    private fun emitEvent(event: QAEvent) {
        scope.launch { _events.emit(event) }
    }
}

private fun QuestionDto.toQuestion() = Question(
    id = id,
    text = text,
    submitterName = submitterName,
    timestamp = timestamp,
    status = try { QuestionStatus.valueOf(status) } catch (_: Exception) { QuestionStatus.PENDING },
    voteCount = voteCount,
    upvotes = upvotes,
    downvotes = downvotes,
)

sealed class QAEvent {
    data class QuestionSubmitted(val question: Question) : QAEvent()
    data class QuestionUpdated(val question: Question) : QAEvent()
    data class SessionChanged(val active: Boolean) : QAEvent()
    data class DisplayChanged(val question: Question?) : QAEvent()
}
