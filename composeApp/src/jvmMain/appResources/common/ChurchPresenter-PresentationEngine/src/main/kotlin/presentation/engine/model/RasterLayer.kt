package presentation.engine.model

import java.awt.image.BufferedImage

/**
 * A rasterized slide layer: transparent ARGB pixels cropped to the layer's padded bounds, plus
 * the pixel offset of the crop within the full slide frame. Compositing every layer of a slide
 * at its offset (in list order) reproduces the full slide.
 */
data class RasterLayer(
    val spec: LayerSpec,
    val image: BufferedImage,
    val offsetXPx: Int,
    val offsetYPx: Int
)
