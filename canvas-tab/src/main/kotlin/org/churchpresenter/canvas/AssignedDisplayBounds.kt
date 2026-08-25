package org.churchpresenter.canvas

import androidx.compose.runtime.Composable
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.ui.HEADLESS_PRESENTER_BOUNDS
import org.churchpresenter.ui.assignedBoundsOf
import org.churchpresenter.ui.safeScreenDevices
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle

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
