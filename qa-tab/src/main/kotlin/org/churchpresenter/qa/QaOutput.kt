package org.churchpresenter.qa

import org.churchpresenter.core.models.qa.Question

/**
 * What the Q&A tab asks of the audience screens, and nothing more.
 *
 * The app drives its outputs through `PresenterManager` and describes what is live with the
 * `Presenting` enum. Both are `:composeApp` types — `Presenting` alone is named by three dozen files
 * there — so neither can come into this module, and depending on the app is not possible in any
 * case. This interface is the seam: it names the four things Q&A actually needs, `:composeApp`
 * implements it in `viewmodel/PresenterQaOutput.kt` as a pass-through, and `Presenting` never
 * crosses the boundary.
 *
 * **Do not widen this to mirror `PresenterManager`.** It is a list of what Q&A needs, not a view
 * onto the app's output state.
 *
 * Implementations read Compose state in their getters, so a composable that reads [outputIsClear] or
 * [lockedToQa] recomposes when the output changes underneath it.
 */
interface QaOutput {

    /**
     * True when nothing at all is live — the state Escape and Clear Display leave the output in.
     *
     * The tab watches this so that clearing the screen from anywhere else also drops the question it
     * was showing. A boolean rather than the live-content enum on purpose: Q&A only cares whether
     * the screen went empty, and the transitions between two other kinds of content are none of its
     * business.
     */
    val outputIsClear: Boolean

    /**
     * True when some screen is pinned to Q&A.
     *
     * A pinned screen keeps showing Q&A while the main output moves on, so [outputIsClear] going
     * true does *not* mean the question left the wall — and the tab must not clear its own state.
     */
    val lockedToQa: Boolean

    /** Puts [question] on the screens, or takes the current one off when it is `null`. */
    fun setDisplayedQuestion(question: Question?)

    /** Shows or hides the join QR code on the screens. */
    fun setShowQrCode(show: Boolean)

    /** Makes Q&A the live content. */
    fun goLive()

    /** Takes whatever is live off the screens. */
    fun clear()
}
