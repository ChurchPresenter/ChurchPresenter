package org.churchpresenter.helper

import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction

/**
 * What the app does for the helper. The helper decides *what* to do and asks first; the app owns
 * the settings, the output and the windows, so it is the one that carries an action out.
 */
fun interface HelperActionExecutor {
    /** Carries out [action], already confirmed by the operator. */
    fun execute(action: HelperAction): ActionOutcome
}
