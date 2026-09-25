package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Where content that has no band of its own sits on a lower-third output.
 *
 * Bible verses and song lyrics draw their own band. Everything else — a video, a slide, a picture,
 * a web page, a canvas scene — has none, and until this existed always covered the whole output.
 */
@Serializable
enum class LowerThirdPlacement {
    /** The whole output, exactly as on a full-screen one. What every lower third did before. */
    FULL_SCREEN,

    /** Inside the band's rectangle at the bottom of the output, leaving the area above it clear. */
    IN_BAND,
}

/** The content a lower-third profile can place — see [LowerThirdPlacement]. */
@Serializable
enum class PlaceableContent { MEDIA, PRESENTATION, PICTURES, WEBSITE, CANVAS }
