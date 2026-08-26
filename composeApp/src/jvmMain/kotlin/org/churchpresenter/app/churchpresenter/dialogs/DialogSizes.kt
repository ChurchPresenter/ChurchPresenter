package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The size each fixed-size dialog opens at, named once so the window and the test that measures it
 * cannot drift apart.
 *
 * `DialogViewportTest` asserts that a dialog's content fits the window it is given. While both sides
 * spelled the number out as a literal, that assertion held the *content* to a number and nothing
 * held the *window* to it: shrinking a dialog back to a size its content no longer fits broke no
 * test, which is the one regression the suite exists to catch. `MemoryMonitorWindow` demonstrated
 * this — it could be returned to the 440dp that clipped its Force GC row with the suite still green.
 *
 * These are the sizes of dialogs that cannot be resized, or whose height is hand-computed to fit
 * particular content. Sizes derived at runtime from the display — see `dialogSizeWithin` — do not
 * belong here; there is no fixed number to pin.
 */

/** `AddLabelDialog` — 640dp of height for the recent-colors list. See the note at its call site. */
internal val ADD_LABEL_DIALOG_WIDTH: Dp = 500.dp
internal val ADD_LABEL_DIALOG_HEIGHT: Dp = 640.dp

/** `AddWebsiteDialog`. */
internal val ADD_WEBSITE_DIALOG_WIDTH: Dp = 500.dp
internal val ADD_WEBSITE_DIALOG_HEIGHT: Dp = 440.dp

/** `AboutDialog`. */
internal val ABOUT_DIALOG_WIDTH: Dp = 420.dp
// 520 rather than 490 since the NDI trademark line joined the copyright: DialogViewportTest measured
// the content at 504dp with its text 30% larger, i.e. 14dp off the bottom of the old window.
internal val ABOUT_DIALOG_HEIGHT: Dp = 520.dp

/** `KonamiEasterEggDialog`. */
internal val KONAMI_DIALOG_WIDTH: Dp = 420.dp
internal val KONAMI_DIALOG_HEIGHT: Dp = 340.dp

/**
 * `MemoryMonitorWindow` — 500dp, not the 440dp it shipped with: the content measures 414dp at normal
 * text size and 466dp at the 1.3x growth the viewport suite treats as the headroom a fixed window
 * must absorb, so 440 opened already clipped for anyone with OS font scaling on.
 */
internal val MEMORY_MONITOR_WINDOW_WIDTH: Dp = 460.dp
internal val MEMORY_MONITOR_WINDOW_HEIGHT: Dp = 500.dp

/**
 * `RemoteEventDialog`, whose height is chosen per call: the queued form carries an extra
 * "N behind this one" line, and the 40dp between the two is exactly what that line costs.
 */
internal val REMOTE_EVENT_DIALOG_WIDTH: Dp = 500.dp
internal val REMOTE_EVENT_DIALOG_HEIGHT: Dp = 290.dp
internal val REMOTE_EVENT_DIALOG_HEIGHT_QUEUED: Dp = 330.dp

internal val SHARE_STORY_DIALOG_WIDTH: Dp = 930.dp
internal val SHARE_STORY_DIALOG_HEIGHT: Dp = 470.dp
