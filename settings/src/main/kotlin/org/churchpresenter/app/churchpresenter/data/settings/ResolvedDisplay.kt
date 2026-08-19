package org.churchpresenter.app.churchpresenter.data.settings

/**
 * A physical display an output slot can be sent to, already resolved from `GraphicsEnvironment`.
 *
 * [deviceIndex] is the position in the full `screenDevices` array — *not* in the non-primary list —
 * because that is what [ScreenAssignment.targetDisplay] stores and what the presenter windows look
 * up later. Passing the non-primary index here would send every output to the wrong screen on any
 * machine whose primary is not device 0.
 */
data class ResolvedDisplay(
    val deviceIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
