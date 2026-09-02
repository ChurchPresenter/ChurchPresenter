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
    // Custom ffmpeg executable (empty = use the copy bundled with the app). Unlike vlcPath and
    // ndiRuntimePath this is an override rather than a way to find something we do not ship: the
    // app carries its own ffmpeg, and this exists for an operator who wants a different build.
    val ffmpegPath: String = "",
    // Number of simulated dev-fallback presenter windows to open when there is no real output
    // (single-monitor dev machine). Lets several independent outputs be simulated on one screen
    // for developing/testing per-output features. Only takes effect in the dev fallback; ignored
    // when real displays/DeckLink devices exist. Clamped to at least 1 at the use sites.
    val devWindowCount: Int = 1,
    /**
     * What the operator calls each physical monitor -- "Sanctuary Left", "Foyer TV", "Balcony".
     *
     * Keyed by [screenKey], the monitor's own geometry, rather than by its index in the device list
     * or by the output slot pointing at it. Both of those are positions: unplugging the middle
     * monitor renumbers the ones after it, and re-targeting an output at another display would drag
     * the name across to hardware it was never chosen for. The bounds are what the assignments
     * themselves already match on -- see `ScreenAssignment.targetBounds*`, stored "for reliable
     * mapping" for the same reason.
     *
     * A monitor with no entry has never been renamed and falls back to its numbered default, which
     * is what makes clearing the field the way to undo a rename.
     */
    val screenNames: Map<String, String> = emptyMap(),
) {
    /** [key]'s name as the operator typed it, or blank for a monitor never renamed. */
    fun screenName(key: String): String = screenNames[key]?.trim().orEmpty()

    /**
     * Records [key]'s name, dropping the entry entirely once it is blank.
     *
     * Stored exactly as typed: this runs on every keystroke of the settings field and the field
     * shows back what it stored, so trimming here would delete the space as it is pressed and a
     * two-word name could never be typed. Whitespace is trimmed by [screenName], where it is read.
     */
    fun withScreenName(key: String, name: String): ProjectionSettings {
        if (key.isEmpty()) return this
        return copy(
            screenNames = if (name.isBlank()) screenNames - key else screenNames + (key to name),
        )
    }

    /**
     * What this output is called: the monitor's name if it drives one that has been renamed, else
     * the name given to the slot itself, else [default] — the numbered "Screen N" label, which is
     * localized and so has to be resolved by the caller.
     *
     * The monitor wins because it is the more specific of the two: a slot name is what a row falls
     * back to while it drives no monitor at all. The mirror of
     * [ScreenAssignment.browserSourceLabelOr] for hardware outputs.
     */
    fun screenLabelOr(assignment: ScreenAssignment, default: String): String =
        screenName(assignment.targetScreenKey)
            .ifBlank { assignment.screenName.trim() }
            .ifBlank { default }

    fun getAssignment(index: Int): ScreenAssignment =
        screenAssignments.getOrElse(index) { ScreenAssignment() }

    fun withAssignment(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = screenAssignments.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment())
        mutable[index] = assignment
        return copy(screenAssignments = mutable)
    }
}
