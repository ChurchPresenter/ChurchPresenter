package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.sharedui.models.Presenting

/**
 * The content types that can go up *over* the slide rather than replacing it: captions, lower thirds
 * and announcements, each on a layer of its own above the slide (`docs/LAYER_MODEL.md`). Whether one
 * does is each output's choice ([drawsOverContent]); by default it replaces the slide, as it always did.
 */
internal val OVERLAY_MODES: Set<Presenting> = setOf(Presenting.STT, Presenting.LOWER_THIRD, Presenting.ANNOUNCEMENTS)

/** Whether this content type is held as an overlay rather than as the slide -- see [OVERLAY_MODES]. */
internal val Presenting.isOverlay: Boolean get() = this in OVERLAY_MODES

/** Whether this output draws the overlay [overlay] over its content rather than in place of it. */
internal fun OutputProfile.drawsOverContent(overlay: Presenting): Boolean = when (overlay) {
    Presenting.LOWER_THIRD -> lowerThirdOverContent
    Presenting.ANNOUNCEMENTS -> announcementsOverContent
    Presenting.STT -> captionsOverContent
    else -> false
}

/**
 * What an output with [profile] shows on its slide layers when it follows the live content: the
 * newest overlay up that replaces content on this output, or else the slide.
 */
internal fun PresenterManager.unlockedModeFor(profile: OutputProfile): Presenting =
    overlays.value.lastOrNull { !profile.drawsOverContent(it) } ?: slideContent.value

/**
 * What an output with [profile] shows on its slide layers, given [effectiveMode] -- its screen lock,
 * or the slide's mode when it has none. A lock wins; otherwise [unlockedModeFor] decides.
 */
internal fun PresenterManager.shownModeFor(profile: OutputProfile, effectiveMode: Presenting): Presenting =
    if (effectiveMode == slideContent.value) unlockedModeFor(profile) else effectiveMode

/**
 * The layer a remote client names when it clears one -- `POST /api/clear?layer=...` or the
 * WebSocket `clear` command's `layer` -- or null for a name that is not one, which clears nothing.
 * The background follows the slide's content and is not cleared on its own.
 */
internal fun layerForName(name: String): Layer? = when (name.trim().lowercase()) {
    "lowerthird", "lower_third", "lower-third", "graphics" -> Layer.GRAPHICS
    "captions", "stt" -> Layer.CAPTIONS
    "announcements", "announcement" -> Layer.ANNOUNCEMENTS
    "slide" -> Layer.SLIDE
    "media" -> Layer.MEDIA
    "messages", "message" -> Layer.MESSAGES
    else -> null
}
