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
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.qa.toDto
import org.churchpresenter.sharedui.utils.UsageEventStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Serializable
private data class QAState(
    val questions: List<QuestionDto> = emptyList(),
    val history: List<QuestionDto> = emptyList(),
    val votedIps: Map<String, Map<String, String>> = emptyMap()
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

    /** Questions from previous sessions. */
    val history = mutableStateListOf<Question>()

    val sessionActive = mutableStateOf(false)
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
            history = history.map { it.toDto() },
            votedIps = votedIps.mapValues { entry -> entry.value.toMap() }
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

    fun load() {
        try {
            if (!stateFile.exists()) return
            val state = json.decodeFromString(QAState.serializer(), stateFile.readText())
            questions.addAll(state.questions.map { it.toQuestion() })
            history.addAll(state.history.map { it.toQuestion() })
            state.votedIps.forEach { (qId, ipMap) ->
                val map = ConcurrentHashMap<String, String>()
                map.putAll(ipMap)
                votedIps[qId] = map
            }
        } catch (_: Exception) { }
    }
}

private fun QuestionDto.toQuestion() = Question(
    id = id,
    text = text,
    submitterName = submitterName,
    submitterDeviceId = submitterDeviceId,
    timestamp = timestamp,
    status = try { QuestionStatus.valueOf(status) } catch (_: Exception) { QuestionStatus.PENDING },
    voteCount = voteCount,
    upvotes = upvotes,
    downvotes = downvotes,
)
