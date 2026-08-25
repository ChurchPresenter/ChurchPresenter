package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.stt.SttOutput

/**
 * `:composeApp`'s implementation of [SttOutput], over [PresenterManager].
 *
 * The whole of the app's side of the Live Captions port: the tab asks whether the captions are live
 * and asks to make them live, and this turns both into the `Presenting` enum that `:stt-tab` cannot
 * see. Kept as a pass-through with no state of its own — anything that needs remembering belongs in
 * `PresenterManager` or in the tab, not here.
 *
 * Mirrors `PresenterAnnouncementsOutput` and `PresenterQaOutput`.
 */
class PresenterSttOutput(
    private val presenterManager: PresenterManager,
    private val presenting: (Presenting) -> Unit,
) : SttOutput {

    override val isLive: Boolean
        get() = presenterManager.presentingMode.value == Presenting.STT

    override fun goLive() = presenting(Presenting.STT)
}
