package org.churchpresenter.settings

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
    // NDI outputs are virtual in exactly the same way, and for the same reason are kept out of
    // screenAssignments: that list is reconciled against detected hardware and drives the
    // window-count arithmetic, and an NDI output maps to no display and opens no window.
    val ndiOutputs: List<ScreenAssignment> = emptyList(),
    // Custom NDI Runtime directory (empty = auto-detect), the exact counterpart of vlcPath. The
    // runtime is installed separately — this app ships no NDI binaries and may not.
    val ndiRuntimePath: String = "",
    // Number of simulated dev-fallback presenter windows to open when there is no real output
    // (single-monitor dev machine). Lets several independent outputs be simulated on one screen
    // for developing/testing per-output features. Only takes effect in the dev fallback; ignored
    // when real displays/DeckLink devices exist. Clamped to at least 1 at the use sites.
    val devWindowCount: Int = 1,
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

    fun getNdiOutput(index: Int): ScreenAssignment =
        ndiOutputs.getOrElse(index) { ScreenAssignment() }

    fun withNdiOutput(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = ndiOutputs.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(ndiOutputs = mutable)
    }

    fun addNdiOutput(): ProjectionSettings = copy(ndiOutputs = ndiOutputs + ScreenAssignment())

    fun removeNdiOutput(index: Int): ProjectionSettings =
        copy(ndiOutputs = ndiOutputs.filterIndexed { i, _ -> i != index })
}
