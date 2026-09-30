package org.churchpresenter.app.churchpresenter

import kotlin.math.roundToInt
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.settings.AppSettings

/*
 * The main screen's side panels: how wide each may be, how wide it is drawn, and how its width and
 * collapsed state are saved per window mode. Pure; see MainDesktopPanels for where they are used.
 */

internal fun computePanelCapPx(availablePx: Float, otherPanelPx: Float, reservePx: Float, absMaxPx: Float): Float =
    if (availablePx <= 0f) Float.MAX_VALUE
    else (availablePx - otherPanelPx - reservePx).coerceIn(0f, absMaxPx)

internal fun withScheduleWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(schedulePanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(schedulePanelWidthDp = widthDp))

internal fun withPreviewWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(previewPanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(previewPanelWidthDp = widthDp))

internal fun withSchedulePanelCollapsed(settings: AppSettings, isMaximized: Boolean, collapsed: Boolean): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(schedulePanelCollapsed = collapsed))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(schedulePanelCollapsed = collapsed))

internal fun withPreviewPanelCollapsed(settings: AppSettings, isMaximized: Boolean, collapsed: Boolean): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(previewPanelCollapsed = collapsed))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(previewPanelCollapsed = collapsed))

/**
 * The width a collapsible side panel actually renders at, in pixels.
 *
 * Order matters: the cap is applied to the requested width *before* the collapse fraction scales it.
 * Capping afterwards would make a panel that is only wide because the window shrank animate open
 * from its old, too-large width and overshoot past the resize handles on the way.
 *
 * [visibleFraction] runs 0f (collapsed) to 1f (open) and is driven by an `Animatable`, which can
 * overshoot slightly below zero mid-spring — hence the floor, since a negative width is not a legal
 * measurement constraint.
 */
internal fun panelRenderWidthPx(requestedPx: Float, capPx: Float, visibleFraction: Float): Int =
    (requestedPx.coerceAtMost(capPx) * visibleFraction).roundToInt().coerceAtLeast(0)

/**
 * Which stored layout a window's placement reads from. Floating is the windowed one; every other
 * placement — maximized and fullscreen alike — shares the maximized layout, so a fullscreen window
 * does not silently start editing the windowed sizes.
 */
internal fun isMaximizedPlacement(placement: WindowPlacement?): Boolean =
    placement != WindowPlacement.Floating

/**
 * A panel width brought back inside its cap.
 *
 * Applied on every recomposition during a drag rather than once on release: leaving a width beyond
 * its cap means the next drag applies its delta to a stale base, and the panel jumps instead of
 * tracking the cursor.
 */
internal fun clampPanelWidth(currentPx: Float, capPx: Float): Float =
    if (currentPx > capPx) capPx else currentPx

/**
 * Whether a collapsible panel still has to be composed. A panel that is collapsed but mid-animation
 * is still on screen, so it is composed until the animation has actually run out.
 */
internal fun isPanelRendered(collapsed: Boolean, visibleFraction: Float): Boolean =
    !collapsed || visibleFraction > 0f
