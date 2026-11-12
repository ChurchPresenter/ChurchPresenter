package org.churchpresenter.web.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** A controller whose page asks for mobile pages while [mobile] says so, reloading when it changes. */
@Composable
internal fun rememberMobileNavController(mobile: Boolean): WebNavController {
    val nav = remember { WebNavController().apply { mobileMode = mobile } }
    LaunchedEffect(mobile) { if (nav.mobileMode != mobile) nav.setMobileEmulation(mobile) }
    return nav
}
