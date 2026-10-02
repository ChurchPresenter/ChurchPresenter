package org.churchpresenter.qa

import androidx.compose.runtime.State
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.sharedui.models.Presenting

/**
 * What the Q&A tab needs from the live output: what is on screen and which screens are locked, and
 * the calls that put a question or the join QR code up.
 *
 * The app's `PresenterQAOutput` is the one implementation, passing every call to `PresenterManager`.
 * The tab takes this rather than the manager itself so the module never reaches into the app, and
 * so a test can hand the tab a fake that records what it was told.
 */
interface QAOutput {
    val presentingMode: State<Presenting>
    val screenLocks: State<Map<Int, Presenting>>

    fun setDisplayedQuestion(question: Question?)
    fun setShowQRCodeOnDisplay(show: Boolean)
}
