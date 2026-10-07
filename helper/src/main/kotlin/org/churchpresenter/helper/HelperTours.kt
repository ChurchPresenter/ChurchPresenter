package org.churchpresenter.helper

import org.churchpresenter.helper.action.GuideTour

/** Moves the tour on, or ends it after its last step. */
fun HelperState.nextStep(executor: HelperActionExecutor) {
    val touring = reply as? HelperReply.Touring ?: return
    if (touring.index + 1 < touring.tour.steps.size) {
        showStep(touring.tour, touring.index + 1, executor)
    } else {
        reset()
    }
}

/** Rings [tour]'s step [index], opening first what it needs open. */
internal fun HelperState.showStep(tour: GuideTour, index: Int, executor: HelperActionExecutor) {
    val step = tour.steps.getOrNull(index) ?: return
    step.before?.let { executor.execute(it) }
    session.activeTarget = step.target
    show(HelperReply.Touring(tour, index))
}
