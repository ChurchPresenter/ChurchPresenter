package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.qa.QAOutput
import org.churchpresenter.sharedui.models.Presenting

/**
 * [PresenterManager] as the `:qa` tab sees it: every call goes straight through.
 *
 * A separate class rather than `PresenterManager : QAOutput`, for the same reason as
 * [PresenterSlidesOutput]: the manager's own declaration stays untouched.
 */
class PresenterQAOutput(private val manager: PresenterManager) : QAOutput {
    override val presentingMode: State<Presenting> get() = manager.presentingMode
    override val screenLocks: State<Map<Int, Presenting>> get() = manager.screenLocks

    override fun setDisplayedQuestion(question: Question?) = manager.setDisplayedQuestion(question)
    override fun setShowQRCodeOnDisplay(show: Boolean) = manager.setShowQRCodeOnDisplay(show)
}
