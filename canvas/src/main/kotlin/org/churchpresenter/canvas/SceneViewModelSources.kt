package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform

fun SceneViewModel.addSource(source: SceneSource) {
    val scene = currentScene ?: return
    updateScene(scene.id) { it.copy(sources = it.sources + source) }
    _selectedSourceId.value = source.id
}

fun SceneViewModel.removeSource(sourceId: String) {
    val scene = currentScene ?: return
    updateScene(scene.id) {
        it.copy(
            sources = it.sources.filter { s -> s.id != sourceId },
            alternate = it.alternate?.let { layout -> layout.copy(transforms = layout.transforms - sourceId) }
        )
    }
    if (_selectedSourceId.value == sourceId) {
        _selectedSourceId.value = null
    }
}

fun SceneViewModel.selectSource(sourceId: String?) {
    _selectedSourceId.value = sourceId
}

fun SceneViewModel.updateSource(sourceId: String, updater: (SceneSource) -> SceneSource) {
    val scene = currentScene ?: return
    updateScene(scene.id) {
        it.copy(sources = it.sources.map { s ->
            if (s.id == sourceId) updater(s) else s
        })
    }
}

fun SceneViewModel.moveSourceUp(sourceId: String) {
    val scene = currentScene ?: return
    val sources = scene.sources.toMutableList()
    val index = sources.indexOfFirst { it.id == sourceId }
    if (index > 0) {
        val temp = sources[index]
        sources[index] = sources[index - 1]
        sources[index - 1] = temp
        updateScene(scene.id) { it.copy(sources = sources) }
    }
}

fun SceneViewModel.moveSourceDown(sourceId: String) {
    val scene = currentScene ?: return
    val sources = scene.sources.toMutableList()
    val index = sources.indexOfFirst { it.id == sourceId }
    if (index >= 0 && index < sources.size - 1) {
        val temp = sources[index]
        sources[index] = sources[index + 1]
        sources[index + 1] = temp
        updateScene(scene.id) { it.copy(sources = sources) }
    }
}

fun SceneViewModel.toggleSourceVisibility(sourceId: String) {
    updateSource(sourceId) { source ->
        when (source) {
            is SceneSource.ImageSource -> source.copy(visible = !source.visible)
            is SceneSource.TextSource -> source.copy(visible = !source.visible)
            is SceneSource.ColorSource -> source.copy(visible = !source.visible)
            is SceneSource.VideoSource -> source.copy(visible = !source.visible)
            is SceneSource.BrowserSource -> source.copy(visible = !source.visible)
            is SceneSource.ShapeSource -> source.copy(visible = !source.visible)
            is SceneSource.ClockSource -> source.copy(visible = !source.visible)
            is SceneSource.QRCodeSource -> source.copy(visible = !source.visible)
            is SceneSource.CameraSource -> source.copy(visible = !source.visible)
            is SceneSource.ScreenCaptureSource -> source.copy(visible = !source.visible)
            is SceneSource.NdiSource -> source.copy(visible = !source.visible)
            is SceneSource.OmtSource -> source.copy(visible = !source.visible)
            is SceneSource.BibleSource -> source.copy(visible = !source.visible)
        }
    }
}

fun SceneViewModel.toggleSourceLock(sourceId: String) {
    updateSource(sourceId) { source ->
        when (source) {
            is SceneSource.ImageSource -> source.copy(locked = !source.locked)
            is SceneSource.TextSource -> source.copy(locked = !source.locked)
            is SceneSource.ColorSource -> source.copy(locked = !source.locked)
            is SceneSource.VideoSource -> source.copy(locked = !source.locked)
            is SceneSource.BrowserSource -> source.copy(locked = !source.locked)
            is SceneSource.ShapeSource -> source.copy(locked = !source.locked)
            is SceneSource.ClockSource -> source.copy(locked = !source.locked)
            is SceneSource.QRCodeSource -> source.copy(locked = !source.locked)
            is SceneSource.CameraSource -> source.copy(locked = !source.locked)
            is SceneSource.ScreenCaptureSource -> source.copy(locked = !source.locked)
            is SceneSource.NdiSource -> source.copy(locked = !source.locked)
            is SceneSource.OmtSource -> source.copy(locked = !source.locked)
            is SceneSource.BibleSource -> source.copy(locked = !source.locked)
        }
    }
}

/**
 * Moves or resizes [sourceId]. With [alternate] set the change goes to the scene's second layout
 * and the main one is left as it was; with no second layout it is ignored.
 */
fun SceneViewModel.updateTransform(sourceId: String, transform: SourceTransform, alternate: Boolean = false) {
    if (!alternate) {
        updateSource(sourceId) { it.withTransform(transform) }
        return
    }
    val scene = currentScene ?: return
    val layout = scene.alternate ?: return
    updateScene(scene.id) {
        it.copy(alternate = layout.copy(transforms = layout.transforms + (sourceId to transform)))
    }
}
