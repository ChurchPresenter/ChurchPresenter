package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.canvas.CanvasOutput
import org.churchpresenter.core.models.scene.Scene

/**
 * `:composeApp`'s implementation of [CanvasOutput], over [PresenterManager].
 *
 * The three calls the tab used to make — set the scene, switch the mode, show the window — are one
 * decision, so the port is one method and this is where it is spelled out again.
 *
 * Mirrors `PresenterAnnouncementsOutput`, `PresenterQaOutput`, `PresenterSttOutput` and
 * `PresenterWebOutput`.
 */
class PresenterCanvasOutput(
    private val presenterManager: PresenterManager,
    private val presenting: (Presenting) -> Unit,
) : CanvasOutput {

    override fun goLive(scene: Scene) {
        presenterManager.setActiveScene(scene)
        presenting(Presenting.CANVAS)
        presenterManager.setShowPresenterWindow(true)
    }
}
