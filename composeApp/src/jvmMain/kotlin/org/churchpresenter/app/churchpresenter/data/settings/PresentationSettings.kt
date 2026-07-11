package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class PresentationSettings(
    val autoScrollInterval: Float = 5f, // seconds
    val isLooping: Boolean = true,
    val transitionDuration: Float = 500f, // milliseconds
    val animationType: String = Constants.ANIMATION_CROSSFADE,
    /**
     * Animated playback of Keynote decks uses a reverse-engineered parser (see the
     * presentation engine's keynote package); this switch drops .key files back to the
     * static-image path if a document ever renders wrong.
     */
    val animateKeynote: Boolean = true
)
