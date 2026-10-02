package org.churchpresenter.sharedui.presenter

import androidx.compose.ui.Alignment
import org.churchpresenter.settings.utils.Constants

fun sttPositionToAlignment(position: String): Alignment = when (position) {
    Constants.TOP_LEFT -> Alignment.TopStart
    Constants.TOP_CENTER -> Alignment.TopCenter
    Constants.TOP_RIGHT -> Alignment.TopEnd
    Constants.CENTER_LEFT -> Alignment.CenterStart
    Constants.CENTER -> Alignment.Center
    Constants.CENTER_RIGHT -> Alignment.CenterEnd
    Constants.BOTTOM_LEFT -> Alignment.BottomStart
    Constants.BOTTOM_CENTER -> Alignment.BottomCenter
    Constants.BOTTOM_RIGHT -> Alignment.BottomEnd
    Constants.BOTTOM -> Alignment.BottomCenter
    Constants.TOP -> Alignment.TopCenter
    Constants.MIDDLE -> Alignment.Center
    else -> Alignment.BottomCenter
}
