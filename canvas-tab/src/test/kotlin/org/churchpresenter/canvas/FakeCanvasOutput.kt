package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.Scene

/**
 * A stand-in for the screens.
 *
 * The port is one member, so this records what was sent to it. The tab used to be driven through a
 * real `PresenterManager`, which meant every test of a source editor or a drag handle also stood up
 * the app's whole output model.
 *
 * Mirrors `FakeQaOutput`, `FakeAnnouncementsOutput`, `FakeSttOutput` and `FakeWebOutput`.
 */
internal class FakeCanvasOutput : CanvasOutput {

    /** Every scene sent live, in order. */
    val live = mutableListOf<Scene>()

    override fun goLive(scene: Scene) {
        live += scene
    }
}
