package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_left
import org.churchpresenter.icons.generated.resources.ic_arrow_right
import org.churchpresenter.strings.generated.resources.tooltip_collapse_schedule
import org.churchpresenter.strings.generated.resources.tooltip_expand_schedule
import org.churchpresenter.app.churchpresenter.composables.PanelResizeHandle
import org.churchpresenter.app.churchpresenter.composables.resizedPanelWidth
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val PANEL_COLLAPSE_ANIM_MS = 220
private val SCHEDULE_MIN_WIDTH = 160.dp
private val PREVIEW_MIN_WIDTH = 150.dp
private val HANDLE_WIDTH = 16.dp
private val MIN_MAIN_WIDTH = 200.dp
private val PANEL_ABS_MAX_WIDTH = 600.dp

/**
 * The main screen's three panels: the schedule on the left, the tabs in the middle, the preview on
 * the right, with a drag handle and a collapse toggle between each.
 *
 * Every choice below is load-bearing and each carries its reason: the widths are keyed only on the
 * window mode, the width is measured with a plain Box rather than a subcomposition, the panels are
 * sized with a custom layout rather than AnimatedVisibility, and the caps are applied in a
 * SideEffect. See AGENT.md's sidebar-resize debugging notes.
 */
@Composable
internal fun MainDesktopScope.MainDesktopPanels() {
    Column(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val onSettingsChangeState = rememberUpdatedState(onSettingsChange)

        val windowState = LocalMainWindowState.current
        val isMaximized = isMaximizedPlacement(windowState?.placement)
        val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

        var scheduleCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.schedulePanelCollapsed) }

        // Schedule panel width — loaded from settings, local state for smooth dragging.
        // Keyed ONLY on isMaximized (not the persisted width, which saveScheduleWidth()
        // rewrites after every single drag gesture) — recreating this MutableState after
        // every gesture was found to correlate with every subsequent gesture's remeasure
        // freezing until an unrelated recompose forced its way through; see AGENT.md's
        // sidebar-resize debugging notes. The object should persist for the whole
        // windowed/maximized session, only reloading when that mode's saved width should
        // legitimately take over (switching between windowed and maximized).
        val schedulePanelPxState = rememberPanelWidthPx(isMaximized, currentLayout.schedulePanelWidthDp)
        var schedulePanelPx by schedulePanelPxState
        var previewCollapsed by remember(isMaximized) { mutableStateOf(currentLayout.previewPanelCollapsed) }
        val previewPanelPxState = rememberPanelWidthPx(isMaximized, currentLayout.previewPanelWidthDp)
        var previewPanelPx by previewPanelPxState

        // Drives the collapse/expand slide manually (replaces AnimatedVisibility, which
        // was found to stop remeasuring its content after the first drag gesture settles —
        // see AGENT.md's sidebar-resize debugging notes). While settled at 0f/1f the
        // rendered width below tracks schedulePanelPx/previewPanelPx with no extra lag.
        val scheduleVisibleFraction = rememberCollapseFraction(scheduleCollapsed)
        val previewVisibleFraction = rememberCollapseFraction(previewCollapsed)

        fun saveScheduleWidth() {
            val widthDp = with(density) { schedulePanelPx.toDp().value.toInt() }
            onSettingsChangeState.value { s -> withScheduleWidth(s, isMaximized, widthDp) }
        }

        fun savePreviewWidth() {
            val widthDp = with(density) { previewPanelPx.toDp().value.toInt() }
            onSettingsChangeState.value { s -> withPreviewWidth(s, isMaximized, widthDp) }
        }

        // Plain Box + onSizeChanged (not BoxWithConstraints/SubcomposeLayout): subcomposed
        // content here was found to stop remeasuring after the first drag gesture on a
        // given handle settles, only catching up when an unrelated, larger recomposition
        // forced its way through — see AGENT.md's sidebar-resize debugging notes. Ordinary
        // composition/layout (no subcomposition boundary) doesn't exhibit that freeze.
        var availablePx by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { availablePx = it.width.toFloat() }
        ) {
            // Keep the resize handles on screen at any window size (e.g. when the
            // window is snapped to a half/quadrant): cap each side panel's width so
            // both 16dp handles plus a minimum slice of main content always fit.
            // Render/drag clamp only — saved widths are untouched and restore when
            // the window grows again.
            val reservePx = with(density) { (HANDLE_WIDTH + HANDLE_WIDTH + MIN_MAIN_WIDTH).toPx() }
            val absMaxPx = with(density) { PANEL_ABS_MAX_WIDTH.toPx() }
            // availablePx is 0f until onSizeChanged fires on the first layout pass — treat
            // that as "unknown" (uncapped) rather than clamping panels to 0 in the interim,
            // since nothing here ever raises schedulePanelPx/previewPanelPx back up once the
            // SideEffect below has clamped them down.
            fun panelCapPx(otherPanelPx: Float) = computePanelCapPx(availablePx, otherPanelPx, reservePx, absMaxPx)

            val maxSchedulePx = panelCapPx(if (previewCollapsed) 0f else previewPanelPx)
            val maxPreviewPx = panelCapPx(if (scheduleCollapsed) 0f else schedulePanelPx)

            // Drag handlers below live across many separate gestures (their
            // pointerInput key no longer churns per-drag — see comment there), so
            // they must read these caps live rather than from a captured local val.
            val maxScheduleState = rememberUpdatedState(maxSchedulePx)
            val maxPreviewState = rememberUpdatedState(maxPreviewPx)

            // Keep the drag-base state in sync with the live cap — otherwise the
            // first drag after a shrink jumps/snaps instead of tracking the cursor,
            // since the drag delta would be applied to a stale, out-of-range base.
            // Uses SideEffect (not LaunchedEffect): these caps are plain vals that
            // change on nearly every recomposition during an active drag, and a
            // LaunchedEffect keyed on a value that churns that fast cancels/relaunches
            // its coroutine constantly, starving live recomposition until the drag ends.
            SideEffect {
                schedulePanelPx = clampPanelWidth(schedulePanelPx, maxSchedulePx)
                previewPanelPx = clampPanelWidth(previewPanelPx, maxPreviewPx)
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Collapsible schedule panel
                if (isPanelRendered(scheduleCollapsed, scheduleVisibleFraction.value)) {
                    ScheduleSidebar(
                        modifier = Modifier
                            .sidePanelWidth {
                                val fraction = scheduleVisibleFraction.value
                                panelRenderWidthPx(schedulePanelPx, maxScheduleState.value, fraction)
                            }
                            .fillMaxHeight()
                    )
                }

                // Drag handle + collapse toggle between schedule and main content
                SidePanelHandle(
                    collapsed = scheduleCollapsed,
                    panelPx = schedulePanelPxState,
                    capPx = maxScheduleState,
                    minWidth = SCHEDULE_MIN_WIDTH,
                    invert = false,
                    onResizeEnd = ::saveScheduleWidth,
                    onToggleCollapsed = {
                        scheduleCollapsed = !scheduleCollapsed
                        onSettingsChangeState.value { s ->
                            withSchedulePanelCollapsed(s, isMaximized, scheduleCollapsed)
                        }
                    },
                    collapsedIcon = IconRes.drawable.ic_arrow_right,
                    expandedIcon = IconRes.drawable.ic_arrow_left,
                )

                MainTabArea(modifier = Modifier.weight(1f).fillMaxHeight())

                // Right drag handle + collapse toggle for preview panel.
                // Inverted: this panel is on the right, so dragging left widens it.
                SidePanelHandle(
                    collapsed = previewCollapsed,
                    panelPx = previewPanelPxState,
                    capPx = maxPreviewState,
                    minWidth = PREVIEW_MIN_WIDTH,
                    invert = true,
                    onResizeEnd = ::savePreviewWidth,
                    onToggleCollapsed = {
                        previewCollapsed = !previewCollapsed
                        onSettingsChangeState.value { s ->
                            withPreviewPanelCollapsed(s, isMaximized, previewCollapsed)
                        }
                    },
                    collapsedIcon = IconRes.drawable.ic_arrow_left,
                    expandedIcon = IconRes.drawable.ic_arrow_right,
                )

                // Collapsible preview panel (right sidebar)
                PreviewSidebar(
                    collapsed = previewCollapsed,
                    visibleFraction = previewVisibleFraction.value,
                    previewPanelPx = previewPanelPx,
                    maxPreviewPx = maxPreviewState.value,
                    presenterManager = presenterManager,
                    mediaViewModel = mediaViewModel,
                    instanceLinkSendClear = link.sendClear,
                    livePreviewAppSettings = livePreviewAppSettings,
                    activeQuickBackground = activeQuickBackground,
                    onQuickBackgroundPicked = onQuickBackgroundPicked,
                    onSettingsChange = onSettingsChange,
                    appSettings = appSettings,
                    serverUrl = web.serverUrl,
                    qaDisplayUrl = web.qaDisplayUrl,
                    sttManager = sttManager,
                    companionSatelliteViewModel = companionSatelliteViewModel,
                )
            }
        }
    }
}

/**
 * Sizes a side panel to exactly [widthPx]. The width is read in the layout pass, not in composition,
 * so a drag re-lays the panel out without recomposing it.
 */
internal fun Modifier.sidePanelWidth(widthPx: () -> Int): Modifier = layout { measurable, constraints ->
    val width = widthPx()
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

/** A side panel's width in px, loaded from [widthDp] once per window mode — see [MainDesktopPanels]. */
@Composable
private fun rememberPanelWidthPx(isMaximized: Boolean, widthDp: Int): MutableState<Float> {
    val density = LocalDensity.current
    return remember(isMaximized) { mutableStateOf(with(density) { widthDp.dp.toPx() }) }
}

/** How far open a side panel is drawn, animated towards 0 when [collapsed] and 1 when not. */
@Composable
private fun rememberCollapseFraction(collapsed: Boolean): Animatable<Float, *> {
    val fraction = remember { Animatable(if (collapsed) 0f else 1f) }
    LaunchedEffect(collapsed) {
        fraction.animateTo(if (collapsed) 0f else 1f, animationSpec = tween(PANEL_COLLAPSE_ANIM_MS))
    }
    return fraction
}

/**
 * A side panel's drag handle and collapse toggle. Drags resize [panelPx] between [minWidth] and the
 * live [capPx]; [invert] is for the right-hand panel, which dragging left widens.
 */
@Composable
private fun SidePanelHandle(
    collapsed: Boolean,
    panelPx: MutableState<Float>,
    capPx: State<Float>,
    minWidth: Dp,
    invert: Boolean,
    onResizeEnd: () -> Unit,
    onToggleCollapsed: () -> Unit,
    collapsedIcon: DrawableResource,
    expandedIcon: DrawableResource,
) {
    val density = LocalDensity.current
    PanelResizeHandle(
        collapsed = collapsed,
        onResize = { amount ->
            val cap = capPx.value
            panelPx.value = resizedPanelWidth(
                currentPx = panelPx.value,
                dragAmount = amount,
                invert = invert,
                minPx = with(density) { minWidth.toPx() },
                maxPx = cap,
            )
        },
        onResizeEnd = onResizeEnd,
        onToggleCollapsed = onToggleCollapsed,
        icon = painterResource(if (collapsed) collapsedIcon else expandedIcon),
        contentDescription = stringResource(
            if (collapsed) Res.string.tooltip_expand_schedule
            else Res.string.tooltip_collapse_schedule
        ),
    )
}
