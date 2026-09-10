package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.utils.Constants

@Serializable
data class BackgroundConfig(
    // "Default", "Color", "Image", "Video", "Camera", "Transparent", "Gradient"
    val backgroundType: String = Constants.BACKGROUND_COLOR,
    val backgroundColor: String = "#000000", // Black
    val backgroundImage: String = "",
    val backgroundVideo: String = "",
    val backgroundOpacity: Float = 1.0f,
    val gradientEnabled: Boolean = false,
    val gradientTopColor: String = "#000000",
    val gradientTopOpacity: Float = 0.0f,
    val gradientBottomColor: String = "#000000",
    val gradientBottomOpacity: Float = 0.8f,
    val gradientPosition: Float = 0.5f,
    /**
     * Percent of black washed over the background, 0-100, and the blur radius in the 1920x1080
     * reference space — the same two the per-song background carries, so a background configured
     * here and one a song brings with it can be made to look alike.
     */
    val dim: Int = 0,
    val blur: Int = 0,
    /**
     * The device a [Constants.BACKGROUND_CAMERA] background draws, appended for the same reason
     * dim and blur were: these are same-typed parameters, and re-ordering them would silently
     * change the meaning of any positional construction.
     */
    val camera: CameraDeviceRef = CameraDeviceRef(),
    /**
     * What covers the part of the output a lower-third band does *not* — the two thirds above it.
     *
     * Shaped like [backgroundType] and read the same way, because it is the same decision one
     * rectangle up: `Default` defers to the surface above, `Color` paints [aboveBandColor] at
     * [aboveBandOpacity], `Transparent` paints nothing. Only those three — the band is where a
     * picture, a clip or a camera belongs, and the area above it is a flat wash or nothing.
     *
     * `Default` is the out-of-the-box value, so Bible and Songs follow the Default Lower Third
     * until they are given a wash of their own. **The chain ends transparent**, which is what
     * every lower third did before this existed and what a Browser Source or NDI alpha output
     * needs in order to key.
     *
     * All three are meaningless on a full-screen surface, which paints the whole output already.
     */
    val aboveBandType: String = Constants.BACKGROUND_DEFAULT,
    val aboveBandColor: String = "#000000",
    val aboveBandOpacity: Float = 1.0f,
)
