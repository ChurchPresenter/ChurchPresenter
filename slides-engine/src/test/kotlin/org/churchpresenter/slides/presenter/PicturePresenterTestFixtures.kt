package org.churchpresenter.slides.presenter

import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.IntSize

internal val zeroSizeWindowInfo = object : WindowInfo {
    override val isWindowFocused: Boolean = true
    override val containerSize: IntSize = IntSize.Zero
}
