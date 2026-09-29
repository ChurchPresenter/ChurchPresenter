package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

// The OMT output list, read and written: `VirtualOutputs.kt`'s four operations over `omtOutputs`,
// in a file of their own so that one stays the Browser Source and NDI pair it was written as.

/** The OMT output at [index], or a default one for a slot never configured. */
fun ProjectionSettings.getOmtOutput(index: Int): ScreenAssignment =
    omtOutputs.getOrElse(index) { ScreenAssignment(activeProfileId = fallbackProfileId) }

/** [assignment] as the OMT output at [index]. */
fun ProjectionSettings.withOmtOutput(index: Int, assignment: ScreenAssignment): ProjectionSettings =
    copy(omtOutputs = omtOutputs.withOutputAt(index, assignment, fallbackProfileId))

/** One more OMT output, at the end. */
fun ProjectionSettings.addOmtOutput(): ProjectionSettings =
    copy(omtOutputs = omtOutputs + ScreenAssignment(activeProfileId = fallbackProfileId))

/** Removes the OMT output at [index], renumbering the ones after it. */
fun ProjectionSettings.removeOmtOutput(index: Int): ProjectionSettings =
    copy(omtOutputs = omtOutputs.filterIndexed { i, _ -> i != index })
        .shiftPreviewMembers(Constants.PREVIEW_OUTPUT_OMT, index)
