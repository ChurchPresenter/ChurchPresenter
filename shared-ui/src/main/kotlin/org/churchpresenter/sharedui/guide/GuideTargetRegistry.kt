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
import androidx.compose.ui.Modifier
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

    /** Called by a tagged control when it is pressed; only a press on the active target counts. */
    fun pressed(target: GuideTarget) {
        if (target == activeTarget) activePresses++
    }

    /**
     * Where the Profiles settings page should open, set by a tour just before it opens Settings; the
     * page takes it once it is shown and puts it back to null.
     */
    var profileFocus by mutableStateOf<ProfileFocus?>(null)

    /**
     * The shortcut, by its `ShortcutAction` name, the Keyboard Shortcuts window should show when a tour
     * opens it: the window clears its search and selects that shortcut's section, then puts it back.
     */
    var shortcutFocus by mutableStateOf<String?>(null)

    /**
     * Whether the operator is typing to Wick. A tab's focus rescue leaves the keyboard alone while this is
     * true: focus in Wick's text box is not lost, and taking it back would send the typing to the tab.
     */
    var wickTyping by mutableStateOf(false)

    /**
     * How many windows other than the main one are showing Wick's lamp. While any is, the main window's
     * steps aside: Settings blocks the main window, so a bubble there could not be typed into.
     */
    var otherLamps by mutableIntStateOf(0)
}

/**
 * A place in the Profiles settings page: which profile, which of its pages, which row.
 *
 * [page] is the page's stable name: `GENERAL`, `OUTPUTS`, `CONTENT`, or an appearance pane's name
 * (`SONGS`, `BIBLE`, `STAGE_MONITOR`, …). A null [profileId] keeps the profile already selected; a
 * null [page] lets [rowKey] decide, by the page that lists that row.
 */
data class ProfileFocus(val profileId: String? = null, val page: String? = null, val rowKey: String? = null)

/** This window's registry; null outside a spotlight host, which makes [guideTarget] do nothing. */
val LocalGuideTargetRegistry = staticCompositionLocalOf<GuideTargetRegistry?> { null }

/** The app's guide session; null when no helper is running. */
val LocalGuideSession = staticCompositionLocalOf<GuideSession?> { null }

/**
 * Wick's lamp, for a window other than the main one: its spotlight host draws it in the window's corner,
 * given the modifier that places it, so Wick can be asked while that window is in front. Null when
 * Wick is off.
 */
val LocalWickCorner = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** The color a tagged control is highlighted in while a tour points at it; set by the spotlight host. */
val LocalGuideRingColor = staticCompositionLocalOf { Color.Unspecified }
