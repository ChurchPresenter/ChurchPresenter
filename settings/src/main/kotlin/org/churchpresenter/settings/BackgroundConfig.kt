package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

@Serializable
data class BackgroundConfig(
    val backgroundType: String = Constants.BACKGROUND_COLOR, // "Default", "Color", "Image", "Video"
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
    val blur: Int = 0
)
