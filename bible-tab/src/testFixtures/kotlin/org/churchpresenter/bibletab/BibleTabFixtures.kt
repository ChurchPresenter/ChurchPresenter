package org.churchpresenter.bibletab

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings

/** [bible] on every output profile as well as the install-wide settings. */
fun AppSettings.withBibleEverywhere(bible: BibleSettings): AppSettings = copy(
    bibleSettings = bible,
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(bibleSettings = bible) },
    ),
)
