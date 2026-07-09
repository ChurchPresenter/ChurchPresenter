package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True only inside off-screen Browser Source scenes (see BrowserSourceVideoRenderer):
 * "no background" then means genuinely transparent pixels — so OBS/vMix can key the
 * program video underneath the overlay — rather than the solid black a projector
 * window paints (where black is what "nothing" looks like on a physical display).
 *
 * Consulted wherever the render path would otherwise force black for a disabled or
 * Transparent background: PresenterScreen's background layer, plus BiblePresenter's
 * and SongPresenter's own per-content background layers.
 */
val LocalTransparentBlanking = staticCompositionLocalOf { false }
