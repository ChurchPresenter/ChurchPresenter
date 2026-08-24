package org.churchpresenter.stt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A stand-in for the screens.
 *
 * The tab used to be driven through a real `PresenterManager`, which meant every test of a caption
 * or a connection state also stood up the app's whole output model. The port is two members, so the
 * fake is too: [live] is what the tab reads, and [goLiveCalls] counts what it asked for.
 *
 * [live] is backed by Compose state on purpose. In the app `PresenterSttOutput.isLive` reads
 * `PresenterManager.presentingMode`, which is observable, so the Go Live button dims itself on the
 * next recomposition. A plain `var` here would leave the button enabled after a click and the test
 * would be asserting something the app never does.
 *
 * Mirrors `FakeQaOutput` and `FakeAnnouncementsOutput`.
 */
internal class FakeSttOutput(live: Boolean = false) : SttOutput {

    /** What the outputs are showing. Set it to compose the tab already live. */
    var live: Boolean by mutableStateOf(live)

    /** How many times Go Live was pressed. */
    var goLiveCalls: Int = 0
        private set

    override val isLive: Boolean get() = live

    override fun goLive() {
        goLiveCalls++
        live = true
    }
}
