package org.churchpresenter.helper

import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.WickPlace

/** Moves the tour on, or ends it after its last step. */
fun HelperState.nextStep(executor: HelperActionExecutor, said: String? = null) {
    val touring = reply as? HelperReply.Touring ?: return
    said?.let(::answer)
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
    session.activeHint = { step.hint.resolve() }
    show(HelperReply.Touring(tour, index))
}

/**
 * [tour] as it goes from where the lamp is: in Settings, without its opening "press Settings" — that
 * button is in the main window, behind the dialog the operator is already in.
 */
internal fun HelperState.fromHere(tour: GuideTour): GuideTour {
    if (place !is WickPlace.Settings) return tour
    val rest = tour.steps.dropWhile { it.target == GuideTargets.SETTINGS_BUTTON }
    return if (rest.isEmpty()) tour else GuideTour(rest)
}
