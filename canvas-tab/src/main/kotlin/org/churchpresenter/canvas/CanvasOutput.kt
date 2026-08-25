package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.Scene

/**
 * What the Canvas tab needs from the screens, and nothing more.
 *
 * One member. The tab used to take `PresenterManager` and the app's `Presenting` enum for three
 * calls that always happen together — set the scene, switch the mode, show the window — which is one
 * decision ("put this scene up") spelled three ways. `:composeApp` implements this over
 * `PresenterManager` in `PresenterCanvasOutput`.
 *
 * Unlike the other tabs' ports there is nothing to read back: the canvas never asks what is live.
 * Its Go Live button is enabled on whether a scene is selected, not on what the outputs are doing.
 */
fun interface CanvasOutput {

    /** Put [scene] on the screens and open the presenter window if it is closed. */
    fun goLive(scene: Scene)
}
