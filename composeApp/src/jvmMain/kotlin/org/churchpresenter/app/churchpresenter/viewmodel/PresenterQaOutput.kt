package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.qa.QaOutput

/**
 * The app's implementation of [QaOutput], over [PresenterManager] and the live-content callback.
 *
 * This is the only place the two sides meet. `:qa-tab` names four operations and two flags;
 * everything to do with [Presenting] — which is a `:composeApp` type, and one that three dozen files
 * here reach for — is translated here and goes no further.
 *
 * The two flags are read during composition and are backed by `PresenterManager`'s own
 * `State` objects, so a composable that reads them recomposes when the output changes.
 *
 * @param presenting sets which tab's content is live. `MainDesktop` owns that state, not
 *   `PresenterManager`, which is why it arrives separately.
 */
class PresenterQaOutput(
    private val presenterManager: PresenterManager,
    private val presenting: (Presenting) -> Unit,
) : QaOutput {

    override val outputIsClear: Boolean
        get() = presenterManager.presentingMode.value == Presenting.NONE

    override val lockedToQa: Boolean
        get() = presenterManager.screenLocks.value.values.any { it == Presenting.QA }

    override fun setDisplayedQuestion(question: Question?) =
        presenterManager.setDisplayedQuestion(question)

    override fun setShowQrCode(show: Boolean) =
        presenterManager.setShowQRCodeOnDisplay(show)

    override fun goLive() = presenting(Presenting.QA)

    override fun clear() = presenting(Presenting.NONE)
}
