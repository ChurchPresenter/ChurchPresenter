package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.settings.utils.Constants
import androidx.compose.runtime.State
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.sharedui.models.Presenting

/**
 * Everything an off-screen output needs in order to draw the live content.
 *
 * A type rather than nine parameters repeated at every call site, because there are now three
 * renderers drawing the same thing — [BrowserSourceVideoRenderer], [NdiVideoRenderer] and
 * [OmtVideoRenderer] — and the
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
    /** Which output of its [kind] this is, numbered from 0. */
    val outputIndex: Int = 0,
    val sttManager: STTManager? = null,
    val mediaViewModel: MediaViewModel? = null,
    val qaDisplayUrlState: State<String>? = null,
    val serverUrlState: State<String>? = null,
    /**
     * Which of the virtual-output lists this belongs to.
     *
     * All are 0-based and independent, so the index alone does not say which output it is. The
     * kind decides which identify set to consult and how an unnamed output labels itself.
     */
    val kind: OffscreenOutputKind = OffscreenOutputKind.BROWSER_SOURCE,
)

/** The kinds of virtual output that render through [OffscreenOutputContent]. */
enum class OffscreenOutputKind(
    /** The list this kind's outputs are stored in, as an output key names it -- `ndi:0`. */
    val previewKind: String,
) {
    BROWSER_SOURCE(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE),
    NDI(Constants.PREVIEW_OUTPUT_NDI),
    OMT(Constants.PREVIEW_OUTPUT_OMT),
}
