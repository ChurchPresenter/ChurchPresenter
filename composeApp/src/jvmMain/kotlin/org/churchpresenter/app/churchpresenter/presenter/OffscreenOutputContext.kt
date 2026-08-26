package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.stt.STTManager

/**
 * Everything an off-screen output needs in order to draw the live content.
 *
 * A type rather than nine parameters repeated at every call site, because there are now two
 * renderers drawing the same thing — [BrowserSourceVideoRenderer] and [NdiVideoRenderer] — and the
 * list was already long enough that the two `State` parameters of the same type could be swapped
 * silently.
 *
 * [presenterManager] is held rather than reached for. That is the rendering-bridge exception
 * AGENT.md allows: this is the panel the renderers draw, and nothing about the manager escapes
 * beyond them.
 */
data class OffscreenOutputContext(
    val presenterManager: PresenterManager,
    val appSettingsState: State<AppSettings>,
    val screenAssignmentState: State<ScreenAssignment>,
    val effectiveModeState: State<Presenting>,
    /**
     * Which output this is, for the identify overlay. Browser Source outputs are numbered from 0;
     * an NDI output passes [NO_IDENTIFY], which matches no entry in the identifying set, because
     * NDI has no identify button of its own.
     */
    val outputIndex: Int = 0,
    val sttManager: STTManager? = null,
    val mediaViewModel: MediaViewModel? = null,
    val qaDisplayUrlState: State<String>? = null,
    val serverUrlState: State<String>? = null,
) {
    companion object {
        /** An output index that no identify request can ever name. */
        const val NO_IDENTIFY = -1
    }
}
