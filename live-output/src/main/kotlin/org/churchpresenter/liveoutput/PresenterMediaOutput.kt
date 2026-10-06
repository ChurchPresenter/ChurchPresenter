package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import org.churchpresenter.media.MediaOutput
import org.churchpresenter.sharedui.models.Presenting

/** [PresenterManager] as the `:media` tab sees it: every call goes straight through. */
class PresenterMediaOutput(private val manager: PresenterManager) : MediaOutput {
    override fun isLive(mode: Presenting): Boolean = manager.isLive(mode)
    override val showPresenterWindow: State<Boolean> get() = manager.showPresenterWindow

    override fun setPresentingMode(mode: Presenting) = manager.setPresentingMode(mode)
    override fun setShowPresenterWindow(show: Boolean) = manager.setShowPresenterWindow(show)
    override fun setCurrentMedia(url: String, type: String) = manager.setCurrentMedia(url, type)
    override fun requestClearDisplay() = manager.requestClearDisplay()
}
