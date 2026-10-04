package org.churchpresenter.settings

import kotlinx.serialization.Serializable

@Serializable
data class ProjectionSettings(
    val windowTop: Int = 32,
    val windowLeft: Int = 32,
    val windowRight: Int = 32,
    val windowBottom: Int = 32,
    val screenAssignments: List<ScreenAssignment> = listOf(
        ScreenAssignment(activeProfileId = DEFAULT_OUTPUT_PROFILE_ID),
    ),
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
    // OMT outputs: virtual, and kept out of screenAssignments, for the reasons ndiOutputs gives.
    val omtOutputs: List<ScreenAssignment> = emptyList(),
    // A libomt directory to use instead of the copy bundled with the app (empty = the bundled one).
    // An override, like ffmpegPath, rather than a search like ndiRuntimePath: OMT is MIT and ships
    // inside the installer.
    val omtLibraryPath: String = "",
    // An OMT discovery server, `omt://host:port`, for a network where DNS-SD does not reach — blank
    // uses DNS-SD. Applies to finding sources on the Canvas and to being found as an output alike.
    val omtDiscoveryServer: String = "",
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
     * Which output each content tab previews, keyed by tab.
     *
     * Content can be routed to several outputs at once and they need not be the same shape -- a
     * 16:9 projector and a 4:3 foyer TV can both show media, and Browser Source and NDI outputs
     * carry resolutions of their own. So a tab preview has no single correct shape to take, and
     * the operator picks; this remembers the pick so a rig is set up once rather than every
     * service.
     *
     * Keys are the tab ids in `Constants.PREVIEW_TAB_*` and values the output keys built by
     * `Constants.previewOutputKey`. An entry naming an output that no longer exists is ignored
     * rather than repaired -- a monitor unplugged for one week should still be the choice when it
     * comes back.
     */
    val previewOutputSelections: Map<String, String> = emptyMap(),
    /**
     * How the preview panel was arranged before [previewLayouts]: each group a grid of previews
     * (see [PreviewGroup]). Settings version 20 turns them into a layout; they are kept only so a
     * file taken back to an older build still reads as it did there.
     */
    val previewGroups: List<PreviewGroup> = emptyList(),
    /**
     * The preview panel's named layouts, one of them drawn -- see [activeLayout]. Empty is the
     * panel as it always was: every output listed one per row.
     */
    val previewLayouts: List<PreviewLayout> = emptyList(),
    /** The [PreviewLayout.id] of the layout the panel draws; the first where this names none. */
    val activePreviewLayout: String = "",
    /**
     * True: a layout is stretched to the panel's height as well as its width, and nothing scrolls.
     * False, the default and what groups always did: it keeps the shape its previews give it, scaling
     * with the panel's width and scrolling when taller than the panel.
     */
    val previewLayoutFillsPanel: Boolean = false,
    /** Whether outputs no area of the layout shows are listed under it, one per row, or left out. */
    val listUnplacedOutputs: Boolean = true,
    /** Whether each preview in the panel carries its output's name. */
    val showOutputLabels: Boolean = true,
    /** Whether each preview names its display type -- Full Screen, Lower Third or Stage Monitor. */
    val showOutputModes: Boolean = true,
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
    // Launch with the output windows hidden; the operator reveals them with the toolbar's display
    // toggle when ready to present.
    val startOutputsHidden: Boolean = false,
    // Never show the mouse pointer over a full-screen output, so the congregation does not see it
    // crossing the projector while the operator works on the main screen. On unless turned off.
    val hideCursorOnOutputs: Boolean = true,
    // When a lower third or an announcement ends on its own -- a lower third's timed run, an
    // announcement's last loop -- clear the whole display (on, as it always did) or take down only
    // that overlay and leave the slide under it.
    val overlayEndClearsDisplay: Boolean = true,
    // Preview mode: a new song, picture, lower third or announcement is cued on Preview and goes on air
    // with Take, rather than going live straight away. Off, as it always was.
    val previewModeEnabled: Boolean = false,
    /**
     * Named, reusable [OutputProfile]s, any of which an output can follow via
     * [ScreenAssignment.activeProfileId] -- see `AppSettings.resolvedFor(profile)`
     * (`OutputProfileResolution.kt`). A fresh install starts with exactly one, factory-default
     * profile -- see [DEFAULT_OUTPUT_PROFILE_ID] -- so every output has something to point at
     * before the operator has made anything of their own.
     */
    val outputProfiles: List<OutputProfile> = listOf(factoryOutputProfile()),
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

    /**
     * A newly appearing slot's starting profile: the first one that exists, so a monitor plugged in
     * after a migrated install (which seeds its own profiles rather than one literally named
     * [DEFAULT_OUTPUT_PROFILE_ID]) still gets a real profile rather than a dangling reference.
     */
    val fallbackProfileId: String
        get() = outputProfiles.firstOrNull()?.id ?: DEFAULT_OUTPUT_PROFILE_ID

    /**
     * Every output pointed at a profile that exists, and at least one profile for them to point at.
     *
     * An invariant, not a migration: `SettingsManager` applies it on every load. An output whose
     * [ScreenAssignment.activeProfileId] is null or names no profile follows nothing -- its picker
     * shows no selection and it draws with factory defaults -- and such outputs were saved by the
     * startup reconcile before it learned to assign one, and by a migration of a document with no
     * outputs, which left no profiles at all. Each is repointed at [fallbackProfileId]. A valid
     * reference -- [BLANK_OUTPUT_PROFILE_ID] included, a deliberate choice -- is never touched.
     */
    fun withProfileReferencesRepaired(): ProjectionSettings {
        val profiles = outputProfiles.ifEmpty { listOf(factoryOutputProfile()) }
        val ids = profiles.mapTo(hashSetOf(BLANK_OUTPUT_PROFILE_ID)) { it.id }
        val fallback = profiles.first().id
        fun List<ScreenAssignment>.repaired() =
            map { if (it.activeProfileId in ids) it else it.copy(activeProfileId = fallback) }
        return copy(
            outputProfiles = profiles,
            screenAssignments = screenAssignments.repaired(),
            browserSourceOutputs = browserSourceOutputs.repaired(),
            ndiOutputs = ndiOutputs.repaired(),
            omtOutputs = omtOutputs.repaired(),
        )
    }

    fun getAssignment(index: Int): ScreenAssignment =
        screenAssignments.getOrElse(index) { ScreenAssignment(activeProfileId = fallbackProfileId) }

    fun withAssignment(index: Int, assignment: ScreenAssignment): ProjectionSettings {
        val mutable = screenAssignments.toMutableList()
        while (mutable.size <= index) mutable.add(ScreenAssignment(activeProfileId = fallbackProfileId))
        mutable[index] = assignment
        return copy(screenAssignments = mutable)
    }
}

/** The one profile a fresh install starts with, and what an install left with none is given. */
fun factoryOutputProfile(): OutputProfile = OutputProfile(id = DEFAULT_OUTPUT_PROFILE_ID, name = "Default")
