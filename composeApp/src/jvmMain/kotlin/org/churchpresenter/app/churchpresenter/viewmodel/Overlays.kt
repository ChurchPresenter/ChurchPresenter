package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.sharedui.models.Presenting

/**
 * The content types that go up *over* the slide rather than replacing it: lower thirds and
 * captions, each on a layer of its own above the slide (`docs/LAYER_MODEL.md`). Announcements are
 * full-screen messages and still replace the slide.
 */
internal val OVERLAY_MODES: Set<Presenting> = setOf(Presenting.STT, Presenting.LOWER_THIRD)

/** Whether this content type goes up over the slide rather than replacing it -- see [OVERLAY_MODES]. */
internal val Presenting.isOverlay: Boolean get() = this in OVERLAY_MODES
