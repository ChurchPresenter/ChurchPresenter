package org.churchpresenter.media

import androidx.compose.runtime.State
import org.churchpresenter.sharedui.models.Presenting

interface MediaOutput {
    val presentingMode: State<Presenting>
    val showPresenterWindow: State<Boolean>
    fun setPresentingMode(mode: Presenting)
    fun setShowPresenterWindow(show: Boolean)
    fun setCurrentMedia(url: String, type: String)
    fun requestClearDisplay()
}
