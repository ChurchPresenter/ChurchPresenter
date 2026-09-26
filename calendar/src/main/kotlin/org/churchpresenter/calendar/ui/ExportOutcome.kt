package org.churchpresenter.calendar.ui

import java.io.File

/** How the last PDF export ended -- what [ExportToast] reports. */
sealed interface ExportOutcome {
    data class Saved(val file: File) : ExportOutcome

    data class Failed(val reason: String) : ExportOutcome
}
