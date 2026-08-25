package org.churchpresenter.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.core.models.scene.Scene

@Composable
fun ScenePresenter(
    modifier: Modifier = Modifier,
    scene: Scene?
) {
    if (scene == null) return

    SceneCanvas(
        modifier = modifier.fillMaxSize(),
        scene = scene,
        selectedSourceId = null,
        onSourceSelected = {},
        onTransformChanged = { _, _ -> },
        isInteractive = false
    )
}
