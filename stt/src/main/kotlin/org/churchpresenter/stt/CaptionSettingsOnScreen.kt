package org.churchpresenter.stt

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.resolvedFor

/**
 * The caption settings the STT tab's own transcript reads by: those of the first profile that an
 * output is using and that shows captions, else of the first profile that shows captions at all,
 * else of the first profile, else the document's.
 *
 * Captions are styled per profile, so there is no one install-wide answer to "how many segments,
 * which languages, in-progress text or not" any more. The tab is the operator's view of what is
 * going out, so it follows the screen that is putting captions out. The document itself saves only
 * the caption server, so it is the last resort.
 */
internal fun AppSettings.captionSettingsOnScreen(): STTSettings {
    val projection = projectionSettings
    val inUse = (
        projection.screenAssignments + projection.browserSourceOutputs +
            projection.ndiOutputs + projection.omtOutputs
        )
        .mapNotNull { it.activeProfileId }
        .toSet()
    val captioning = projection.outputProfiles.filter { it.showSTT }
    val profile = captioning.firstOrNull { it.id in inUse }
        ?: captioning.firstOrNull()
        ?: projection.outputProfiles.firstOrNull()
        ?: return sttSettings
    return resolvedFor(profile).sttSettings
}
