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
    val lowerThirdHeightPercent: Int = 33 // 10-60, used by Bible & Song presenters
) {
    fun getAssignment(index: Int): ScreenAssignment =
        screenAssignments.getOrElse(index) { ScreenAssignment() }

    fun withAssignment(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = screenAssignments.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(screenAssignments = mutable)
    }
}
