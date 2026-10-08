package org.churchpresenter.liveshow

/**
 * The fixed stack every output composes, bottom to top. Each layer holds at most one [Cue] and is
 * independent of the others: setting one leaves the rest alone.
 *
 * [AUDIO] is not drawn; it is last only so the drawn layers keep their order. [MESSAGES] is the
 * exception to independence: a message going live clears every other layer first, as a full-screen
 * notice does today.
 */
enum class Layer {
    BACKGROUND,
    MEDIA,
    SLIDE,
    CAPTIONS,
    GRAPHICS,
    /** Persistent overlays -- a logo bug, a clock, a badge -- that survive the content changing under them. */
    PROPS,
    ANNOUNCEMENTS,
    MESSAGES,
    AUDIO,
}
