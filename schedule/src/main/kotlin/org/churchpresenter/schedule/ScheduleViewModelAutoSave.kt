package org.churchpresenter.schedule

import java.util.Calendar
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Returns whether the auto-restore dialog should be shown, but only ever returns true
 * once per ViewModel lifetime (i.e. once per app session, since this ViewModel is hoisted
 * to survive schedule panel collapse/expand). Prevents collapsing and re-expanding the
 * schedule panel from re-showing the dialog on every remount of ScheduleTab.
 */
fun ScheduleViewModel.shouldPromptAutoRestore(): Boolean {
    if (autoRestorePrompted) return false
    autoRestorePrompted = true
    return autoSaveAvailable()
}

/** Removes an autosave [autoSaveAvailable] would refuse, so a stale one does not linger. */
internal fun ScheduleViewModel.discardUnreachableAutoSave() {
    try {
        if (autoSaveFile.exists() && !autoSaveAvailable()) autoSaveFile.delete()
    } catch (_: Exception) {
        // Best effort — a schedule must still open on a folder we cannot write to.
    }
}

/** Returns true if there is an autosave from today that is less than 4 hours old. */
fun ScheduleViewModel.autoSaveAvailable(): Boolean {
    if (!autoSaveFile.exists() || autoSaveFile.length() == 0L) return false
    val lastModified = autoSaveFile.lastModified()
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    cal.timeInMillis = lastModified
    val savedDay = cal.get(Calendar.DAY_OF_YEAR)
    val savedYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = now
    val isSameDay = savedDay == cal.get(Calendar.DAY_OF_YEAR) && savedYear == cal.get(Calendar.YEAR)
    val isWithin4Hours = (now - lastModified) < 4 * 60 * 60 * 1000L
    return isSameDay && isWithin4Hours
}

/** Epoch millis of last autosave write, or 0 if no autosave. */
fun ScheduleViewModel.autoSaveSavedAt(): Long = if (autoSaveFile.exists()) autoSaveFile.lastModified() else 0L

/** Loads the autosave into the current schedule. Returns true on success. */
fun ScheduleViewModel.restoreAutoSave(): Boolean {
    if (_isFollowingRemote.value || !autoSaveFile.exists()) return false
    return try {
        val raw = autoSaveFile.readText()
        val jsonText = try { ScheduleCipher.decrypt(raw) } catch (_: Exception) { raw }
        replaceSchedule(decodeSchedule(jsonText), filePath = null)
        true
    } catch (_: Exception) { false }
}

fun ScheduleViewModel.clearAutoSave() {
    try { autoSaveFile.delete() } catch (_: Exception) {}
}
