package org.churchpresenter.helper

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.UndoStack
import org.churchpresenter.helper.display.DisplaySetupFlow
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.helper.suggest.closestRequests
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_undo_done
import org.churchpresenter.strings.generated.resources.helper_undo_stale
import org.churchpresenter.strings.generated.resources.helper_youre_welcome

/** What the helper's bubble is showing below its header. */
sealed interface HelperReply {
    /** Nothing asked: the bubble shows the current suggestion or tip. */
    data object Idle : HelperReply

    /** Waiting for the operator to say yes to [action]. */
    data class Confirm(val action: HelperAction) : HelperReply

    /** Waiting for the operator to pick one of [options]. */
    data class Clarify(val question: HelperText, val options: List<HelperAction>) : HelperReply

    /** [text], with an Undo when the last step can be taken back; [offer] is a follow-up it suggests. */
    data class Message(
        val text: HelperText,
        val canUndo: Boolean = false,
        val offer: HelperAction? = null,
    ) : HelperReply

    /** The key bound to [action]. */
    data class Shortcut(val action: ShortcutAction) : HelperReply

    /** The request was not understood; [closest] are the requests most like it, to pick from. */
    data class Unknown(val closest: List<SuggestedRequest>) : HelperReply

    /** Hello, and examples of what to ask. */
    data object Greeting : HelperReply

    /** Pointing at [tour]'s step [index]. */
    data class Touring(val tour: GuideTour, val index: Int) : HelperReply

    /** Walking through display setup. */
    data object DisplaySetup : HelperReply
}

/**
 * The helper's conversation: what the bubble shows, the tour in progress, and the changes it can
 * take back. A plain state holder rather than a view model — the app owns what the helper acts on,
 * and reaches it through the [HelperActionExecutor] each call is given.
 */
@Stable
class HelperState(val session: GuideSession = GuideSession()) {
    var isOpen by mutableStateOf(false)
    var input by mutableStateOf("")

    /**
     * The conversation so far. It outlives closing the bubble; only [clear] empties it. The reply
     * still waiting on the operator is [reply], drawn under it.
     */
    val thread = HelperThread()

    /** Asking before the lamp is hidden — a card under the conversation, not a new page of it. */
    var confirmingHide by mutableStateOf(false)
    var reply by mutableStateOf<HelperReply>(HelperReply.Idle)
        internal set
    var displayFlow by mutableStateOf(DisplaySetupFlow())

    /** Steps the operator moved through the tips by hand, on top of today's tip. */
    var tipOffset by mutableIntStateOf(0)

    internal val undoStack = UndoStack()

    /** What an Undo now would take back, or null. */
    val undoLabel: HelperText? get() = undoStack.latest?.label

    /** Back to the suggestion or tip, ending any tour. */
    fun reset() {
        session.activeTarget = null
        show(HelperReply.Idle)
    }

    /** Forgets the conversation and starts again. */
    fun clear() {
        session.activeTarget = null
        thread.clear()
        reply = HelperReply.Idle
    }

    /** The operator answered the reply on screen with [text]: both move into the conversation. */
    fun answer(text: String) {
        session.activeTarget = null
        show(HelperReply.Idle)
        thread.said(text)
    }

    /** Closes the bubble, ending any tour — a ring left behind with no bubble would mean nothing. */
    fun close() {
        isOpen = false
        confirmingHide = false
        reset()
    }

    /** Moves the reply on screen into the conversation and puts [next] in its place. */
    internal fun show(next: HelperReply) {
        reply.summary(undoLabel)?.let { thread.add(ThreadEntry.Wick(it)) }
        reply = next
    }



    /** Acts on what a typed request came to. */
    fun onResolved(resolution: Resolution, executor: HelperActionExecutor, input: String = "") {
        when (resolution) {
            is Resolution.Act -> request(resolution.action, executor)
            is Resolution.Clarify -> show(HelperReply.Clarify(resolution.question, resolution.options))
            Resolution.Unknown -> show(HelperReply.Unknown(closestRequests(input)))
        }
    }

    /** Asks first when [action] changes something; otherwise does it now. */
    fun request(action: HelperAction, executor: HelperActionExecutor) {
        session.activeTarget = null
        if (action.needsConfirmation) {
            show(
                if (action == HelperAction.UndoLast && undoStack.latest == null) {
                    HelperReply.Message(helperText(Res.string.helper_nothing_to_undo))
                } else {
                    HelperReply.Confirm(action)
                },
            )
        } else {
            run(action, executor)
        }
    }

    /** Carries out [action] — confirmed already, or one that needs no asking. */
    fun run(action: HelperAction, executor: HelperActionExecutor) {
        when (action) {
            is HelperAction.Highlight -> showStep(action.tour, 0, executor)
            is HelperAction.ShowShortcut -> show(HelperReply.Shortcut(action.action))
            HelperAction.UndoLast -> undo()
            HelperAction.Greet -> show(HelperReply.Greeting)
            HelperAction.Thanks -> show(HelperReply.Message(helperText(Res.string.helper_youre_welcome)))
            else -> onOutcome(executor.execute(action), executor)
        }
    }

    /** Takes back the newest change. */
    fun undo() {
        if (undoStack.latest == null) {
            show(HelperReply.Message(helperText(Res.string.helper_nothing_to_undo)))
            return
        }
        val reverted = undoStack.undo()
        val said = if (reverted) Res.string.helper_undo_done else Res.string.helper_undo_stale
        show(HelperReply.Message(helperText(said)))
    }

    private fun onOutcome(outcome: ActionOutcome, executor: HelperActionExecutor) {
        when (outcome) {
            is ActionOutcome.Done -> {
                outcome.undo?.let(undoStack::push)
                val said = outcome.message ?: helperText(Res.string.helper_done)
                show(HelperReply.Message(said, canUndo = outcome.undo != null))
            }
            is ActionOutcome.Refused -> show(HelperReply.Message(outcome.reason, offer = outcome.instead))
            is ActionOutcome.Guide -> showStep(outcome.tour, 0, executor)
            ActionOutcome.DisplaySetup -> {
                displayFlow = DisplaySetupFlow()
                show(HelperReply.DisplaySetup)
            }
        }
    }

}

