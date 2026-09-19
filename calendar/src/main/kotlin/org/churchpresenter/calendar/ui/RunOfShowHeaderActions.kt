package org.churchpresenter.calendar.ui

/** What the header's controls do -- one bundle, so the pane's signature stays readable. */
internal data class RunOfShowHeaderActions(
    /** Steps the preview clock five minutes on; from nothing, starts it shortly before the service. */
    val onClockStep: () -> Unit,
    /** Drops the preview clock, back to the wall clock or to none. */
    val onClockReset: () -> Unit,
    val onArmed: (Boolean) -> Unit,
    /** Opens the settings on the Automation tab, where the cues are listed and edited. */
    val onOpenAutomation: () -> Unit,
    val onCopy: () -> Unit,
    val onSaveTemplate: () -> Unit,
)
