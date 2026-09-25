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
     * [aboveBandOpacity], `Image`, `Video` and `Camera` fill it with [aboveBandImage],
     * [aboveBandVideo] or [aboveBandCamera] at that same opacity, and `Transparent` paints
     * nothing. The media types are what let a lower-third output carry a picture or a live feed
     * above its band — a Lottie band included, which draws only the band itself.
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
    /**
     * Whether [aboveBandColor] is painted behind the band too, not just above it.
     *
     * On (the default): the wash sits behind the whole output, so wherever the band's own fill
     * fades toward transparent — the top edge of any gradient style, or a band opacity under
     * 100% — the wash shows through instead of raw black. Off restores the original, stricter
     * split: the wash stops exactly at the band's own top edge and nothing is painted behind the
     * band itself, which matters when the band is meant to key clean on its own (a `Transparent`
     * band with a wash configured above it, composited for OBS/NDI) and any colour bleeding
     * through a translucent part of it would be wrong for that specific setup.
     */
    val aboveBandFillsBehindBand: Boolean = true,
    /**
     * The Lottie template a [Constants.BACKGROUND_LOTTIE] lower third plays. Unlike every other
     * type this is not a backdrop under the text: the file carries the text layers and the
     * presenter fills them in, so the band's whole look — motion included — comes from here.
     * Empty, or a file that no longer exists, falls back to the classic band. Appended last for
     * the same positional-construction reason as [camera].
     */
    val backgroundLottie: String = "",
    /**
     * The picture, clip and device an [aboveBandType] of `Image`, `Video` or `Camera` draws.
     * Appended last for the same positional-construction reason as [camera].
     */
    val aboveBandImage: String = "",
    val aboveBandVideo: String = "",
    val aboveBandCamera: CameraDeviceRef = CameraDeviceRef(),
)
