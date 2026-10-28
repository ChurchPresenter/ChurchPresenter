package org.churchpresenter.settings

import org.churchpresenter.settings.utils.AppDataDir
import java.io.File

/** The folder the calendar and its presets live in: the chosen one, or the app data folder when none is. */
fun AppSettings.calendarFolder(): File =
    calendarStorageDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: AppDataDir.resolve()

/**
 * [calendarFolder] for a screen that only prints it: the app data folder as this run already
 * resolved it ([AppDataDir.resolvedThisRun]), so drawing the path does not probe the disk again.
 */
fun AppSettings.calendarFolderShown(): File =
    calendarStorageDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: AppDataDir.resolvedThisRun()

/** Where the signage setup lives: the chosen folder, or the app data folder's `signage` folder. */
fun AppSettings.signageFolder(): File =
    signageStorageDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: File(AppDataDir.resolve(), SIGNAGE_DIR)

/** [signageFolder] for a screen that only prints it, as [calendarFolderShown] is to the calendar. */
fun AppSettings.signageFolderShown(): File =
    signageStorageDirectory.takeIf { it.isNotBlank() }?.let(::File) ?: File(AppDataDir.resolvedThisRun(), SIGNAGE_DIR)

/** The signage setup's folder inside the app data folder, as the signage hub has always named it. */
const val SIGNAGE_DIR = "signage"
