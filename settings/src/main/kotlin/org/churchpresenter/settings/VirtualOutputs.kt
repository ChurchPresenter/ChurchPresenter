package org.churchpresenter.settings

/**
 * The Browser Source and NDI output lists, read and written.
 *
 * Extensions in their own file rather than members of [ProjectionSettings], because the two
 * families are the same four operations twice and putting them here keeps that visible — and keeps
 * the settings class itself about what is persisted rather than about how two of its lists are
 * indexed.
 *
 * Both lists are *virtual* outputs: unlike `screenAssignments` they are never reconciled against
 * detected hardware, so a slot exists exactly because an operator added one, and reading past the
 * end is a default rather than an error.
 */

/**
 * [assignment] at [index], growing the list with defaults if it is short.
 *
 * The growth is what lets a settings row write slot 3 before slots 1 and 2 have ever been touched,
 * which is the ordinary case when an output is configured out of order.
 */
private fun List<ScreenAssignment>.withOutputAt(
    index: Int,
    assignment: ScreenAssignment,
): List<ScreenAssignment> {
    val mutable = toMutableList()
    while (mutable.size <= index) mutable.add(ScreenAssignment())
    mutable[index] = assignment
    return mutable
}

/** The Browser Source output at [index], or a default one for a slot never configured. */
fun ProjectionSettings.getBrowserSourceOutput(index: Int): ScreenAssignment =
    browserSourceOutputs.getOrElse(index) { ScreenAssignment() }

/** [assignment] as the Browser Source output at [index]. */
fun ProjectionSettings.withBrowserSourceOutput(index: Int, assignment: ScreenAssignment): ProjectionSettings =
    copy(browserSourceOutputs = browserSourceOutputs.withOutputAt(index, assignment))

/** One more Browser Source output, at the end. */
fun ProjectionSettings.addBrowserSourceOutput(): ProjectionSettings =
    copy(browserSourceOutputs = browserSourceOutputs + ScreenAssignment())

/** Removes the Browser Source output at [index], renumbering the ones after it. */
fun ProjectionSettings.removeBrowserSourceOutput(index: Int): ProjectionSettings =
    copy(browserSourceOutputs = browserSourceOutputs.filterIndexed { i, _ -> i != index })

/** The NDI output at [index], or a default one for a slot never configured. */
fun ProjectionSettings.getNdiOutput(index: Int): ScreenAssignment =
    ndiOutputs.getOrElse(index) { ScreenAssignment() }

/** [assignment] as the NDI output at [index]. */
fun ProjectionSettings.withNdiOutput(index: Int, assignment: ScreenAssignment): ProjectionSettings =
    copy(ndiOutputs = ndiOutputs.withOutputAt(index, assignment))

/** One more NDI output, at the end. */
fun ProjectionSettings.addNdiOutput(): ProjectionSettings =
    copy(ndiOutputs = ndiOutputs + ScreenAssignment())

/** Removes the NDI output at [index], renumbering the ones after it. */
fun ProjectionSettings.removeNdiOutput(index: Int): ProjectionSettings =
    copy(ndiOutputs = ndiOutputs.filterIndexed { i, _ -> i != index })
