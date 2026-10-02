package org.churchpresenter.qa

import kotlinx.coroutines.flow.SharedFlow
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.sharedui.utils.UsageEventStore
import org.churchpresenter.sharedui.utils.UsageEvents

/**
 * The Q&A session: the questions a congregation sends from their phones, what the operator does
 * with them, and what is on screen.
 *
 * The work is split across parts sharing one [QAStore] and its one lock: moderation and the
 * display ([QAModeration]), what phones do ([QAAudience]) and the session itself
 * ([QASessionControl]). Their public functions are this class's own, by delegation.
 */
class QAManager private constructor(
    private val store: QAStore,
    moderation: QAModeration,
) : QAModerating by moderation,
    QAAudienceActions by QAAudience(store),
    QASessionActions by QASessionControl(store, moderation) {

    constructor(usage: UsageEventStore = UsageEvents) : this(QAStore(usage))

    private constructor(store: QAStore) : this(store, QAModeration(store))

    val questions: List<Question> get() = store.questions

    /** Questions from previous sessions. */
    val history: List<Question> get() = store.history

    val sessionActive: Boolean get() = store.sessionActive.value
    val displayedQuestion: Question? get() = store.displayedQuestion.value
    val showQRCodeOnDisplay: Boolean get() = store.showQRCodeOnDisplay.value

    /** Change events, for WebSocket broadcasts. */
    val events: SharedFlow<QAEvent> = store.events

    init {
        store.load()
    }

    /**
     * Suspends until every requested save has been written.
     *
     * For tests: the state file is written off-thread, so without this a test has to poll for the
     * shape it expects and time out when it guesses wrong — which is what made
     * `QAManagerStateTest` flaky. Joining the chain head is enough because each save waits for its
     * predecessor. Nothing in the app waits for a save; it is fire-and-forget by design. Public
     * because `:composeApp`'s server suites wait on it too.
     */
    suspend fun awaitPendingSave() = store.awaitPendingSave()
}

sealed class QAEvent {
    data class QuestionSubmitted(val question: Question) : QAEvent()
    data class QuestionUpdated(val question: Question) : QAEvent()
    data class SessionChanged(val active: Boolean) : QAEvent()
    data class DisplayChanged(val question: Question?) : QAEvent()
}
