package org.churchpresenter.media

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.sharedui.models.Presenting

class FakeMediaOutput : MediaOutput {
    override val presentingMode = mutableStateOf(Presenting.NONE)
    override val showPresenterWindow = mutableStateOf(false)
    val clearDisplayRequested = mutableStateOf(false)
    var currentMedia: Pair<String, String>? = null
        private set

    override fun setPresentingMode(mode: Presenting) {
        presentingMode.value = mode
        if (mode != Presenting.NONE) clearDisplayRequested.value = false
    }

    override fun setShowPresenterWindow(show: Boolean) {
        showPresenterWindow.value = show
    }

    override fun setCurrentMedia(url: String, type: String) {
        currentMedia = url to type
    }

    override fun requestClearDisplay() {
        if (presentingMode.value != Presenting.NONE) clearDisplayRequested.value = true
    }
}
