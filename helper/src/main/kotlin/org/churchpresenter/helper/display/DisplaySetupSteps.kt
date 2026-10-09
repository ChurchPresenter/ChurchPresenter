package org.churchpresenter.helper.display

import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction

/** Makes the screen picked in display setup the audience screen, then puts the numbers up to test it. */
fun HelperState.assignPickedScreen(executor: HelperActionExecutor) {
    val screen = displayFlow.picked ?: return
    when (val outcome = executor.execute(HelperAction.AssignAudienceScreen(screen))) {
        is ActionOutcome.Done -> {
            outcome.undo?.let(undoStack::push)
            displayFlow = displayFlow.assigned()
            identifyAgain(executor)
        }
        is ActionOutcome.Refused -> reply = HelperReply.Message(outcome.reason, offer = outcome.instead)
        else -> Unit
    }
}

/** Shows each screen's number, from the test step. */
fun HelperState.identifyAgain(executor: HelperActionExecutor) {
    val outcome = executor.execute(HelperAction.IdentifyScreens)
    if (outcome is ActionOutcome.Refused) reply = HelperReply.Message(outcome.reason, offer = outcome.instead)
}
