package org.churchpresenter.settings

import org.churchpresenter.settings.utils.AppDataDir
import java.io.File

/** The folder the calendar and its presets live in: the chosen one, or the app data folder when none is. */
fun AppSettings.calendarFolder(): File =
    calendarStorageDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: AppDataDir.resolve()
