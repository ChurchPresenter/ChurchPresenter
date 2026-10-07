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
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done
import org.churchpresenter.strings.generated.resources.helper_nothing_to_undo
import org.churchpresenter.strings.generated.resources.helper_undo_done
import org.churchpresenter.strings.generated.resources.helper_undo_stale

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

    /** The request was not understood. */
    data object Unknown : HelperReply

    /** Pointing at [tour]'s step [index]. */
    data class Touring(val tour: GuideTour, val index: Int) : HelperReply

    /** Walking through display setup. */
    data object DisplaySetup : HelperReply

    /** Asking before the lamp is hidden, and saying how to get it back. */
    data object ConfirmHide : HelperReply
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
        reply = HelperReply.Idle
    }

    /** Closes the bubble, ending any tour — a ring left behind with no bubble would mean nothing. */
    fun close() {
        isOpen = false
        reset()
    }

    /** Acts on what a typed request came to. */
    fun onResolved(resolution: Resolution, executor: HelperActionExecutor) {
        when (resolution) {
            is Resolution.Act -> request(resolution.action, executor)
            is Resolution.Clarify -> reply = HelperReply.Clarify(resolution.question, resolution.options)
            Resolution.Unknown -> reply = HelperReply.Unknown
        }
    }

    /** Asks first when [action] changes something; otherwise does it now. */
    fun request(action: HelperAction, executor: HelperActionExecutor) {
        session.activeTarget = null
        if (action.needsConfirmation) {
            reply = if (action == HelperAction.UndoLast && undoStack.latest == null) {
                HelperReply.Message(helperText(Res.string.helper_nothing_to_undo))
            } else {
                HelperReply.Confirm(action)
            }
        } else {
            run(action, executor)
        }
    }

    /** The operator said yes to the pending confirmation. */
    fun confirm(executor: HelperActionExecutor) {
        val pending = reply as? HelperReply.Confirm ?: return
        run(pending.action, executor)
    }

    /** Carries out [action] — confirmed already, or one that needs no asking. */
    fun run(action: HelperAction, executor: HelperActionExecutor) {
        when (action) {
            is HelperAction.Highlight -> showStep(action.tour, 0, executor)
            is HelperAction.ShowShortcut -> reply = HelperReply.Shortcut(action.action)
            HelperAction.UndoLast -> undo()
            else -> onOutcome(executor.execute(action), executor)
        }
    }

    /** Takes back the newest change. */
    fun undo() {
        if (undoStack.latest == null) {
            reply = HelperReply.Message(helperText(Res.string.helper_nothing_to_undo))
            return
        }
        val reverted = undoStack.undo()
        val said = if (reverted) Res.string.helper_undo_done else Res.string.helper_undo_stale
        reply = HelperReply.Message(helperText(said))
    }

    private fun onOutcome(outcome: ActionOutcome, executor: HelperActionExecutor) {
        when (outcome) {
            is ActionOutcome.Done -> {
                outcome.undo?.let(undoStack::push)
                val said = outcome.message ?: helperText(Res.string.helper_done)
                reply = HelperReply.Message(said, canUndo = outcome.undo != null)
            }
            is ActionOutcome.Refused -> reply = HelperReply.Message(outcome.reason, offer = outcome.instead)
            is ActionOutcome.Guide -> showStep(outcome.tour, 0, executor)
            ActionOutcome.DisplaySetup -> {
                displayFlow = DisplaySetupFlow()
                reply = HelperReply.DisplaySetup
            }
        }
    }

    // ---- Tours ----

    /** Moves the tour on, or ends it after its last step. */
    fun nextStep(executor: HelperActionExecutor) {
        val touring = reply as? HelperReply.Touring ?: return
        if (touring.index + 1 < touring.tour.steps.size) {
            showStep(touring.tour, touring.index + 1, executor)
        } else {
            reset()
        }
    }

    /** Rings [tour]'s step [index], opening first what it needs open. */
    private fun showStep(tour: GuideTour, index: Int, executor: HelperActionExecutor) {
        val step = tour.steps.getOrNull(index) ?: return
        step.before?.let { executor.execute(it) }
        session.activeTarget = step.target
        reply = HelperReply.Touring(tour, index)
    }
}
