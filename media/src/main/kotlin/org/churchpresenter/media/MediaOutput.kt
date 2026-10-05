package org.churchpresenter.media

import androidx.compose.runtime.State
import org.churchpresenter.sharedui.models.Presenting

interface MediaOutput {
    /** Whether [mode] is on air, on the slide layers or as an overlay. */
    fun isLive(mode: Presenting): Boolean
    val showPresenterWindow: State<Boolean>
    fun setPresentingMode(mode: Presenting)
    fun setShowPresenterWindow(show: Boolean)
    fun setCurrentMedia(url: String, type: String)
    fun requestClearDisplay()
}
