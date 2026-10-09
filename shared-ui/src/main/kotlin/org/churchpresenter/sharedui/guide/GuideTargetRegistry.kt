package org.churchpresenter.sharedui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * Where each tagged control sits in **one window**, in that window's root coordinates.
 *
 * One per window, because bounds from two windows cannot be drawn in one: the Settings dialog is a
 * window of its own and keeps its own registry.
 */
@Stable
class GuideTargetRegistry {
    private val bounds = mutableStateMapOf<GuideTarget, Rect>()

    /** Where [target] is, or null when nothing tagged with it is laid out in this window. */
    fun boundsOf(target: GuideTarget): Rect? = bounds[target]

    /** Records that [target] is at [rect]. */
    fun report(target: GuideTarget, rect: Rect) {
        if (bounds[target] != rect) bounds[target] = rect
    }

    /** Forgets [target], whose control has left the composition. */
    fun remove(target: GuideTarget) {
        bounds.remove(target)
    }
}

/**
 * What the helper is pointing at, app-wide — shared by every window's spotlight, so a tour can move
 * from a main-window button into the Settings dialog.
 */
@Stable
class GuideSession {
    /** The control the spotlight rings, or null when nothing is being pointed at. */
    var activeTarget by mutableStateOf<GuideTarget?>(null)

    /** The line said beside [activeTarget], in the operator's language, or null for none. */
    var activeHint by mutableStateOf<(@Composable () -> String)?>(null)

    /** How many times the operator pressed the active target — a tour moves on when this changes. */
    var activePresses by mutableIntStateOf(0)
        private set

    /** Called by the spotlight host when [target] is pressed; only a press on the active target counts. */
    fun pressed(target: GuideTarget) {
        if (target == activeTarget) activePresses++
    }
}

/** This window's registry; null outside a spotlight host, which makes [guideTarget] do nothing. */
val LocalGuideTargetRegistry = staticCompositionLocalOf<GuideTargetRegistry?> { null }

/** The app's guide session; null when no helper is running. */
val LocalGuideSession = staticCompositionLocalOf<GuideSession?> { null }

/** The color a tagged control is highlighted in while a tour points at it; set by the spotlight host. */
val LocalGuideRingColor = staticCompositionLocalOf { Color.Unspecified }
