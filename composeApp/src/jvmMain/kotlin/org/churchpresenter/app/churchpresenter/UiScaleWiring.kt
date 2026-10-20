package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.ui.theme.themeCustomizationFrom
import org.churchpresenter.sharedui.utils.usableScreenArea
import org.churchpresenter.theme.UI_SCALES
import org.churchpresenter.theme.maxUiScale
import org.churchpresenter.theme.steppedUiScale
import java.awt.Dimension
import java.awt.Window

/**
 * The room the main window's layout needs with the schedule and preview panels open, in dp. The side
 * panels keep their widths in dp as the interface grows, so below this the middle tab is what gives
 * way: at 1067dp wide the Bible tab's verse column is a few letters across.
 */
private const val UI_SCALE_LAYOUT_WIDTH = 1280f
private const val UI_SCALE_LAYOUT_HEIGHT = 720f

/**
 * The largest interface size the screen [near] holds — the primary screen when null — so the main
 * window keeps the room its layout needs (see [maxUiScale]). Every step where no screen can be asked.
 */
internal fun screenMaxUiScale(near: Window?): Float = usableScreenArea(near)?.let {
    maxUiScale(it.width.value, it.height.value, UI_SCALE_LAYOUT_WIDTH, UI_SCALE_LAYOUT_HEIGHT)
} ?: UI_SCALES.last()

/**
 * The main window's minimum size at [uiScale]: the room its layout needs, grown with what is drawn in
 * it, and never more than [maxUiScale] allows — which is what keeps it on the screen.
 */
internal fun scaledMainWindowMinimum(uiScale: Float, maxUiScale: Float): Dimension {
    val scale = uiScale.coerceAtMost(maxUiScale)
    return Dimension((MIN_MAIN_WINDOW_WIDTH * scale).toInt(), (MIN_MAIN_WINDOW_HEIGHT * scale).toInt())
}

/** Saves [scale] as the interface size and redraws every operator window at it. */
internal fun MainWindowScope.applyUiScale(scale: Float) {
    with(root) {
        appSettings = appSettings.copy(uiScale = scale)
        settingsManager.saveSettings(appSettings)
        onThemeCustomizationChange(themeCustomizationFrom(appSettings, screenMaxUiScale(window)))
    }
}

/** View → Zoom In / Zoom Out: one step of [UI_SCALES], as far as the main window's screen allows. */
internal fun MainWindowScope.stepUiScale(zoomIn: Boolean) {
    applyUiScale(steppedUiScale(root.appSettings.uiScale, zoomIn, screenMaxUiScale(window)))
}
