package org.churchpresenter.helper.display

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.helperText
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_screen_label
import org.churchpresenter.strings.generated.resources.helper_screen_label_named

/**
 * A connected display as the helper shows it. The app maps its own screen detection onto this, so
 * the helper never reaches AWT itself.
 *
 * @property index the device index outputs are assigned by
 * @property name the operator's name for it, or blank
 */
data class HelperScreen(
    val index: Int,
    val isPrimary: Boolean,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val name: String = "",
    val isAudience: Boolean = false,
)

/** Where the display setup is. */
enum class DisplayStep { DETECT, PICK, CONFIRM, TEST, DONE }

/**
 * The helper's display setup, as plain state: which step it is on and which screen was picked.
 * The steps move by [next]; the app does the assigning and identifying.
 */
data class DisplaySetupFlow(
    val step: DisplayStep = DisplayStep.DETECT,
    val picked: HelperScreen? = null,
) {
    /** The flow after the screens were detected: straight to picking when there is a second one. */
    fun detected(screens: List<HelperScreen>): DisplaySetupFlow = when {
        step != DisplayStep.DETECT && step != DisplayStep.PICK -> this
        screens.any { !it.isPrimary } -> copy(step = DisplayStep.PICK)
        else -> copy(step = DisplayStep.DETECT, picked = null)
    }

    /** The flow after the operator picked [screen], asking before it is assigned. */
    fun picking(screen: HelperScreen): DisplaySetupFlow = copy(step = DisplayStep.CONFIRM, picked = screen)

    /** The flow after the picked screen was made the audience screen. */
    fun assigned(): DisplaySetupFlow = copy(step = DisplayStep.TEST)

    /** The flow after the operator said the test number showed where it should. */
    fun confirmed(): DisplaySetupFlow = copy(step = DisplayStep.DONE)

    /** The flow after the operator said it did not — back to picking another screen. */
    fun retry(): DisplaySetupFlow = copy(step = DisplayStep.PICK, picked = null)
}

/** "Display 2 (1920×1080)", or the operator's own name for it. */
fun HelperScreen.screenLabel(): HelperText =
    if (name.isNotBlank()) {
        helperText(Res.string.helper_screen_label_named, name, width, height)
    } else {
        helperText(Res.string.helper_screen_label, index + 1, width, height)
    }
