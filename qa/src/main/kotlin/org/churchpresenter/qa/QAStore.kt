package org.churchpresenter.qa

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionDto
import org.churchpresenter.core.models.qa.toDto
import org.churchpresenter.sharedui.utils.UsageEventStore
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Serializable
private data class QAState(
    val questions: List<QuestionDto> = emptyList(),
    /** The flat archive kept before sessions had names; read once and filed as one session. */
    val history: List<QuestionDto> = emptyList(),
    val votedIps: Map<String, Map<String, String>> = emptyMap(),
    val sessionId: String = "",
    val sessionName: String = "",
    val sessionStartedAt: Long = 0L,
    val sessions: List<QASessionDto> = emptyList(),
)

/**
 * Everything [QAManager] and its parts read and write, and the one [lock] every change is made
 * under -- the parts are separate classes, but a session is one piece of state and is changed one
 * action at a time, as it was when every action was `synchronized` on the manager itself.
 */
internal class QAStore(val usage: UsageEventStore) {

    /** Held for every change to the session; reentrant, so one action may call another. */
    val lock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val stateFile = File(System.getProperty("user.home"), ".churchpresenter/qa_state.json")

    val questions = mutableStateListOf<Question>()

    /** Finished sessions, newest first. */
    val sessions = mutableStateListOf<QASession>()

    val sessionActive = mutableStateOf(false)

    /** The running session: its id, its name and when it started. */
    val sessionId = mutableStateOf("")
    val sessionName = mutableStateOf("")
    val sessionStartedAt = mutableStateOf(0L)
    val displayedQuestion = mutableStateOf<Question?>(null)
    val showQRCodeOnDisplay = mutableStateOf(false)

    /** Rate limiting: IP -> last submission timestamp. */
    val lastSubmission = ConcurrentHashMap<String, Long>()

    /** Voting: questionId -> map of IP -> direction "up"/"down". */
    val votedIps = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    /** Change events, for WebSocket broadcasts. */
    val events = MutableSharedFlow<QAEvent>(extraBufferCapacity = 64)

    /** The most recently requested save, or null when every save has finished. */
    private val pendingSave = AtomicReference<Job?>(null)

    fun emit(event: QAEvent) {
        scope.launch { events.emit(event) }
    }

    /**
     * Writes the session to disk after every action that changes it.
     *
     * Two things here are load-bearing, and this used to do neither.
     *
     * The snapshot is taken **on the calling thread**, not inside the coroutine. Taken inside, it
     * described whatever the state happened to be when the coroutine was scheduled, so the file
     * could record a moment that never corresponded to the action that triggered the save.
     *
     * The writes are then **chained**, so they land in the order they were requested. Each save
     * used to be an independent `launch`, and two actions in quick succession put two writes in
     * flight with nothing ordering them: if the older one landed last, the file kept a stale
     * snapshot for good. That is not only a test problem — moderating quickly during a live
     * session could persist a state the operator had already moved on from, and because the write
     * is wrapped in a `try`/`catch` that swallows everything, it failed silently and only showed
     * up after a restart.
     */
    fun save() {
        val state = QAState(
            questions = questions.map { it.toDto() },
            votedIps = votedIps.mapValues { entry -> entry.value.toMap() },
            sessionId = sessionId.value,
            sessionName = sessionName.value,
            sessionStartedAt = sessionStartedAt.value,
            sessions = sessions.map { it.toDto() },
        )
        val previous = pendingSave.get()
        val job = scope.launch(Dispatchers.IO) {
            previous?.join()
            try {
                stateFile.parentFile?.mkdirs()
                stateFile.writeTextAtomically(json.encodeToString(QAState.serializer(), state))
            } catch (_: Exception) { }
        }
        // Only clear the chain head when it is still the job we put there, so a save started on
        // another thread in the meantime keeps its place in the order.
        pendingSave.set(job)
        job.invokeOnCompletion { pendingSave.compareAndSet(job, null) }
    }

    /** Suspends until every requested save has been written; see [QAManager.awaitPendingSave]. */
    suspend fun awaitPendingSave() {
        pendingSave.get()?.join()
    }

    /**
     * Files the live [questions] as a finished session, newest first, and empties them. Nothing is
     * filed when there are no questions: an empty session is not worth a row in the history.
     */
    fun fileCurrentSession(endedAt: Long) {
        if (questions.isNotEmpty()) {
            val startedAt = sessionStartedAt.value.takeIf { it > 0 } ?: questions.minOf { it.timestamp }
            sessions.add(
                0,
                QASession(
                    id = sessionId.value.ifEmpty { UUID.randomUUID().toString() },
                    name = sessionName.value.ifBlank { defaultSessionName(startedAt) },
                    startedAt = startedAt,
                    endedAt = endedAt,
                    questions = questions.toList(),
                )
            )
            questions.clear()
        }
        sessionId.value = ""
        sessionName.value = ""
        sessionStartedAt.value = 0L
    }

    fun load() {
        try {
            if (!stateFile.exists()) return
            val state = json.decodeFromString(QAState.serializer(), stateFile.readText())
            questions.addAll(state.questions.map { it.toQuestion() })
            sessionId.value = state.sessionId
            sessionName.value = state.sessionName
            sessionStartedAt.value = state.sessionStartedAt
            sessions.addAll(state.sessions.map { it.toSession() })
            if (state.history.isNotEmpty()) sessions.add(legacyHistorySession(state.history))
            // A session never survives a restart as running, so one that was open when the app
            // quit is filed, ready to resume, rather than left to merge into the next one.
            fileCurrentSession(endedAt = questions.maxOfOrNull { it.timestamp } ?: 0L)
            state.votedIps.forEach { (qId, ipMap) ->
                val map = ConcurrentHashMap<String, String>()
                map.putAll(ipMap)
                votedIps[qId] = map
            }
        } catch (_: Exception) { }
    }
}

/** The pre-sessions archive, filed as one session dated by its oldest and newest question. */
private fun legacyHistorySession(history: List<QuestionDto>) = QASession(
    id = UUID.randomUUID().toString(),
    name = LEGACY_HISTORY_SESSION_NAME,
    startedAt = history.minOf { it.timestamp },
    endedAt = history.maxOf { it.timestamp },
    questions = history.map { it.toQuestion() },
)
