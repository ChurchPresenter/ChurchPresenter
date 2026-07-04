package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class ProjectionSettings(
    val windowTop: Int = 32,
    val windowLeft: Int = 32,
    val windowRight: Int = 32,
    val windowBottom: Int = 32,
    val screenAssignments: List<ScreenAssignment> = listOf(ScreenAssignment()),
    val audioOutputDeviceId: String = "", // empty = system default
    val vlcPath: String = "", // custom VLC installation directory (empty = auto-detect)
    val lowerThirdHeightPercent: Int = 33, // 10-60, used by Bible & Song presenters
    // Browser Source outputs are virtual (no physical display/DeckLink device), so unlike
    // screenAssignments they are not auto-synced to detected hardware — added/removed freely.
    val browserSourceOutputs: List<ScreenAssignment> = emptyList(),
) {
    fun getAssignment(index: Int): ScreenAssignment =
        screenAssignments.getOrElse(index) { ScreenAssignment() }

    fun withAssignment(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = screenAssignments.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(screenAssignments = mutable)
    }

    fun getBrowserSourceOutput(index: Int): ScreenAssignment =
        browserSourceOutputs.getOrElse(index) { ScreenAssignment() }

    fun withBrowserSourceOutput(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = browserSourceOutputs.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(browserSourceOutputs = mutable)
    }

    fun addBrowserSourceOutput(): ProjectionSettings =
        copy(browserSourceOutputs = browserSourceOutputs + ScreenAssignment())

    fun removeBrowserSourceOutput(index: Int): ProjectionSettings =
        copy(browserSourceOutputs = browserSourceOutputs.filterIndexed { i, _ -> i != index })
}
