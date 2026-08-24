package org.churchpresenter.qa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.qa.Question

/**
 * A stand-in for the app's outputs that records what the tab asked of them.
 *
 * **Every readable member is Compose state.** The real adapter reads `PresenterManager`'s own
 * `State` objects in its getters, so a composable reading them recomposes when the output changes.
 * A fake built on plain `var` would not, and the tab would keep rendering the frame before the
 * change — which looks like the tab ignoring a click rather than like a broken fake.
 */
internal class FakeQaOutput : QaOutput {

    override var outputIsClear: Boolean by mutableStateOf(true)
    override var lockedToQa: Boolean by mutableStateOf(false)

    /**
     * The question currently on the screens, or `null` for none.
     *
     * Backing fields rather than `displayedQuestion`/`showQrCode` properties: Kotlin would compile
     * those to `setDisplayedQuestion`/`setShowQrCode`, which are the interface's own methods, and
     * the two clash on the JVM. Tests read [shownQuestion] and [qrShown].
     */
    var shownQuestion: Question? by mutableStateOf(null)
        private set

    /** Whether the join QR code is on the screens. */
    var qrShown: Boolean by mutableStateOf(false)
        private set

    /** Every call to [goLive] and [clear], in order — `true` for live, `false` for cleared. */
    val liveChanges = mutableListOf<Boolean>()

    override fun setDisplayedQuestion(question: Question?) {
        shownQuestion = question
    }

    override fun setShowQrCode(show: Boolean) {
        qrShown = show
    }

    override fun goLive() {
        liveChanges += true
        outputIsClear = false
    }

    override fun clear() {
        liveChanges += false
        outputIsClear = true
    }
}
