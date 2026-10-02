package org.churchpresenter.slides.presenter

import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.LayerState
import org.churchpresenter.presentationengine.model.TransitionType

/**
 * One rasterized layer placed in the slide frame: bitmap pixels at [offsetXPx]/[offsetYPx],
 * transformed each frame by [state].
 */
data class PlacedLayer(
    val spec: LayerSpec,
    val bitmap: ImageBitmap,
    val offsetXPx: Int,
    val offsetYPx: Int,
    val state: LayerState
)

/**
 * An in-flight slide transition: the outgoing slide's last layers plus progress. The presenter
 * composites [fromLayers] under/over the incoming layers according to [type]/[direction].
 */
data class TransitionOverlay(
    val type: TransitionType,
    /** Movement direction of the incoming slide (directional types only). */
    val direction: Direction?,
    /** 0..1. */
    val progress: Float,
    val fromLayers: List<PlacedLayer>
)

/** The fully evaluated animated frame all output windows draw. */
data class PresentationFrame(
    val slideIndex: Int,
    /** Full-slide pixel geometry at the player's render scale. */
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    /** Pixels per slide point — converts LayerState point offsets to pixels. */
    val scalePxPerPt: Float,
    val layers: List<PlacedLayer>,
    /** Build progress for the operator UI: completed steps / total steps. */
    val completedSteps: Int,
    val stepCount: Int,
    /** Non-null while the deck-defined transition into this slide is still running. */
    val transition: TransitionOverlay? = null
)
