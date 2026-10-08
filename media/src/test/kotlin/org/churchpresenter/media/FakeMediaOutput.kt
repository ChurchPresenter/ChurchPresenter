package org.churchpresenter.media

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.sharedui.models.Presenting

class FakeMediaOutput : MediaOutput {
    val onAir = mutableStateOf(Presenting.NONE)

    override fun isLive(mode: Presenting): Boolean = mode != Presenting.NONE && onAir.value == mode
    override val showPresenterWindow = mutableStateOf(false)
    val clearDisplayRequested = mutableStateOf(false)
    var currentMedia: Pair<String, String>? = null
        private set

    override fun setPresentingMode(mode: Presenting) {
        onAir.value = mode
        if (mode != Presenting.NONE) clearDisplayRequested.value = false
    }

    override fun setShowPresenterWindow(show: Boolean) {
        showPresenterWindow.value = show
    }

    override val mediaOnAir: String get() = currentMedia?.first.orEmpty()

    override fun setCurrentMedia(url: String, type: String) {
        currentMedia = url to type
    }

    override fun requestClearDisplay() {
        if (onAir.value != Presenting.NONE) clearDisplayRequested.value = true
    }
}
