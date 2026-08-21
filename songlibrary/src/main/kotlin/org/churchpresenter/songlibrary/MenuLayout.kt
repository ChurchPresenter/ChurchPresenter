package org.churchpresenter.songlibrary

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The gap kept under an open menu, so it never sits flush against the bottom of the window. */
val MENU_BOTTOM_MARGIN: Dp = 10.dp

/**
 * The tallest any menu is drawn, whatever the window says.
 *
 * Two jobs. It is the shape the design wants — a panel past this is a list pretending to be a page,
 * and scrolling it is easier than reading it. And it is the cap that holds when the window
 * measurement does not: [menuMaxHeight] is given a height read from `LocalWindowInfo`, and a menu
 * whose only cap came from that was cut off exactly as before when the number came back wrong.
 */
val MENU_MAX_HEIGHT: Dp = 420.dp

/**
 * How tall a menu opened under [anchorBottom] may be, in a window of [windowHeight].
 *
 * A song library holds as many song books as someone made folders, and the menu listing them was
 * laid out at whatever height that came to: a `Popup` is outside the layout, so the rows past the
 * bottom of the window were simply never drawn, with nothing on screen to say the list went on.
 *
 * The room under the button is the real limit; [MENU_MAX_HEIGHT] is the limit that does not depend
 * on measuring anything. A [windowHeight] of zero — the frame before the window has been
 * measured — means "not known yet" rather than "no room", and falls back to the fixed cap; without
 * that the menu would open at zero height on its first frame.
 */
fun menuMaxHeight(windowHeight: Dp, anchorBottom: Dp): Dp {
    if (windowHeight <= MENU_BOTTOM_MARGIN) return MENU_MAX_HEIGHT
    val roomBelow = (windowHeight - anchorBottom - MENU_BOTTOM_MARGIN).coerceAtLeast(0.dp)
    return minOf(roomBelow, MENU_MAX_HEIGHT)
}
