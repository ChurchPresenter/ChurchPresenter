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


