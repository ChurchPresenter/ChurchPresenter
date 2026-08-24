package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.ui.HEADLESS_PRESENTER_BOUNDS
import org.churchpresenter.ui.safeScreenDevices
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle

/**
 * The display questions that need a [ScreenAssignment] to answer.
 *
 * The rest — `presenterScreenBounds`, `presenterAspectRatio`, `formatAspectRatio`,
 * `rememberScreenDevices`, `findScreenIndexByBounds` — moved to `:ui-components`
 * (`ScreenGeometry.kt`), because five tabs ask them and none of them needs a settings type. These
 * two stayed because they do, and `:ui-components` must not gain a production dependency on
 * `:settings`.
 */

/**
 * Bounds of the display [assignment] targets, or 1080p when there is no display to ask (headless).
 *
 * Used for aspect-ratio comparisons against content that will be shown there — a scene built 16:9
 * on a 4:3 output is worth warning about before it goes live, and that check has to keep working on
 * a machine with no second screen attached.
 */
fun assignedDisplayBounds(assignment: ScreenAssignment): Rectangle {
    val screens = safeScreenDevices()
    if (screens.isEmpty()) return HEADLESS_PRESENTER_BOUNDS
    return try {
        assignedBoundsOf(screens, GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice, assignment)
    } catch (_: HeadlessException) {
        HEADLESS_PRESENTER_BOUNDS
    }
}

/**
 * Which of [screens] an assignment resolves to, most specific first: the screen whose top-left
 * corner matches the stored bounds, else the stored index, else any screen that is not [primary],
 * else [primary] itself.
 *
 * Stored bounds win over the index because a display's index shifts when another is plugged in or
 * removed, while its position on the desktop usually does not.
 */
internal fun assignedBoundsOf(
    screens: Array<GraphicsDevice>,
    primary: GraphicsDevice,
    assignment: ScreenAssignment,
): Rectangle {
    val matched = if (assignment.targetBoundsX != Int.MIN_VALUE) {
        screens.firstOrNull { device ->
            val bounds = device.defaultConfiguration.bounds
            bounds.x == assignment.targetBoundsX && bounds.y == assignment.targetBoundsY
        }
    } else null
    val device = matched
        ?: screens.getOrNull(assignment.targetDisplay)
        ?: screens.firstOrNull { it != primary }
        ?: primary
    return device.defaultConfiguration.bounds
}
