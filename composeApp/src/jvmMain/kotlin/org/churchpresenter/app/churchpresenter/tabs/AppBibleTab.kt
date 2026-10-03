package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.lottieBandPath
import org.churchpresenter.app.churchpresenter.utils.isLiveOutput
import org.churchpresenter.app.churchpresenter.utils.isMultiTranslationPresentation
import org.churchpresenter.app.churchpresenter.utils.isSplitScreenBible
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
    val outputs = proj.screenAssignments.filter { it.isLiveOutput() }.mapNotNull { proj.profileFor(it) }
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
