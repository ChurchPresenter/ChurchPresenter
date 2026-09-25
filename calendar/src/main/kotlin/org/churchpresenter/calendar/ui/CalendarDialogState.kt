package org.churchpresenter.calendar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * Which of the window's sheets is open, and what each was opened on.
 *
 * One object rather than a flag and a closer per sheet, so [CalendarApp] reads as the layout it is
 * and [CalendarDialogs] takes one value. Remembered once per window; nothing here is saved.
 */
internal class CalendarDialogState {
    var editingService by mutableStateOf<PlannedService?>(null)
    var creatingService by mutableStateOf(false)
    /** Whether the new-service sheet opens with the Schedule tab's rows chosen to start from. */
    var startFromSchedule by mutableStateOf(false)
    var addingItem by mutableStateOf(false)
    /** The run-of-show row the picker is about to replace, or null when it is appending. */
    var replacing by mutableStateOf<ScheduleItem?>(null)
    var loadConfirmFor by mutableStateOf<PlannedService?>(null)
    var settingsOpen by mutableStateOf(false)
    var settingsTab by mutableStateOf(SettingsTab.SECTIONS)
    /** The service Copy or Template was pressed on, or null while that sheet is closed. */
    var copyFrom by mutableStateOf<PlannedService?>(null)
    var templateFrom by mutableStateOf<PlannedService?>(null)

    fun openSettings() {
        settingsTab = SettingsTab.SECTIONS
        settingsOpen = true
    }

    fun openPicker(replacing: ScheduleItem?) {
        this.replacing = replacing
        addingItem = true
    }

    fun closeServiceSheet() {
        creatingService = false
        editingService = null
        startFromSchedule = false
    }

    fun closePicker() {
        addingItem = false
        replacing = null
    }
}
