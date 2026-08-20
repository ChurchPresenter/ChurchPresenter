package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class PictureSettings(
    val storageDirectory: String = "",
    val autoScrollInterval: Float = 5f, // seconds
    val isLooping: Boolean = true,
    val transitionDuration: Float = 500f, // milliseconds
    val animationType: String = Constants.ANIMATION_CROSSFADE
)
