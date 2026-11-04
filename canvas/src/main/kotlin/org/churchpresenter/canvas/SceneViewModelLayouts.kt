package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.core.models.scene.alternateScene
import org.churchpresenter.core.models.scene.isLandscape

/*
 * Editing a layer in one of a scene's two layouts: which one the Properties panel works on, where the
 * layer sits in it, and taking a layer in the second layout back to where it starts on its own.
 */

/** Edits the selected layer in the scene's second layout when [alternate], its main one otherwise. */
fun SceneViewModel.editLayout(alternate: Boolean) {
    _editingAlternate.value = alternate && currentScene?.alternate != null
}

/** Where [sourceId] sits in [scene]'s second layout: where it was put there, or where it starts. */
internal fun Scene.alternateTransformOf(sourceId: String): SourceTransform? =
    alternateScene().sources.firstOrNull { it.id == sourceId }?.transform

/** Whether [sourceId] has been moved in [scene]'s second layout, rather than starting where it does. */
internal fun Scene.movedInAlternate(sourceId: String): Boolean =
    alternate?.transforms?.containsKey(sourceId) == true

/**
 * Takes [sourceId] in the current scene's second layout back to where a layer not yet moved there
 * starts: worked out from the main layout again (`turnedFor`).
 */
fun SceneViewModel.resetInAlternate(sourceId: String) {
    val scene = currentScene ?: return
    val layout = scene.alternate ?: return
    if (sourceId !in layout.transforms) return
    updateScene(scene.id) { it.copy(alternate = layout.copy(transforms = layout.transforms - sourceId)) }
}

/**
 * What the Properties panel needs to edit [sourceId] in either layout of the current scene; null
 * when the scene has one layout.
 */
fun SceneViewModel.layoutsFor(sourceId: String): SourceLayouts? {
    val scene = currentScene?.takeIf { it.alternate != null } ?: return null
    val alternateTransform = scene.alternateTransformOf(sourceId) ?: return null
    return SourceLayouts(
        mainIsLandscape = scene.isLandscape,
        editingAlternate = editingAlternate.value,
        alternateTransform = alternateTransform,
        movedInAlternate = scene.movedInAlternate(sourceId),
        actions = LayoutActions(
            onEditLayout = ::editLayout,
            onAlternateTransform = { updateTransform(sourceId, it, alternate = true) },
            onResetAlternate = { resetInAlternate(sourceId) },
        ),
    )
}
