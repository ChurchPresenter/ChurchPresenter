package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.liveoutput.lottieBandPath
import org.churchpresenter.telemetry.isLiveOutput
import org.churchpresenter.telemetry.isMultiTranslationPresentation
import org.churchpresenter.telemetry.isSplitScreenBible
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents

/**
 * A verse has just gone live from the Bible tab: the multi-translation, split-screen and lottie-band
 * presentations it went out in are recorded.
 */
fun recordBibleWentLive(appSettings: AppSettings) {
    val translationCount = appSettings.bibleSettings.translationList().size
    val proj = appSettings.projectionSettings
    val outputs = proj.screenAssignments
        .filter { it.isLiveOutput(proj.unusedScreens) }
        .mapNotNull { proj.profileFor(it) }
    if (isMultiTranslationPresentation(translationCount, outputs)) {
        UsageEvents.record(UsageEvent.BIBLE_MULTI_TRANSLATION)
    }
    if (isSplitScreenBible(translationCount, outputs)) {
        UsageEvents.record(UsageEvent.BIBLE_SPLIT_SCREEN)
    }
    if (lottieBandPath(appSettings, Presenting.BIBLE) != null) {
        UsageEvents.record(UsageEvent.BIBLE_LOTTIE_BAND)
    }
}
