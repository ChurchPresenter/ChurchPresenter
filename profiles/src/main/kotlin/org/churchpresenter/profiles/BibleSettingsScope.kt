package org.churchpresenter.profiles

/**
 * The basis a stored font size is expressed against.
 *
 * `BiblePresenter` scales every size by `min(widthPx / 1920, heightPx / 1080)` on whatever output it
 * draws, so "70" means "70 at 1920x1080" and auto-fit has to measure against that -- fitting against
 * a 4K panel's real pixels would hand back a size the presenter then scales up again.
 */
internal const val STYLING_BASIS_WIDTH = 1920
internal const val STYLING_BASIS_HEIGHT = 1080
