package org.churchpresenter.app.churchpresenter.composables

/**
 * Which tool named the cameras a listing is made of.
 *
 * The distinction is the whole point: a device ffmpeg named can be opened by that name, and a device
 * only the platform's own inventory named cannot — see `windowsCamerasFrom` and [parseMacCameras].
 * When a camera fails, knowing which of those produced it separates "the hardware went away" from
 * "we offered a name nothing answers to", and those have opposite remedies.
 */
internal enum class CameraEnumerator {
    /** ffmpeg's DirectShow listing, on Windows. Openable. */
    DSHOW,

    /** The PowerShell PnP inventory, on Windows. Only reached when ffmpeg listed nothing. */
    PNP_FALLBACK,

    /** ffmpeg's AVFoundation listing, on macOS. Openable. */
    AVFOUNDATION,

    /** `system_profiler`, on macOS. Only reached when ffmpeg listed nothing. */
    SYSTEM_PROFILER_FALLBACK,

    /** `/dev/video*` and sysfs, on Linux. Needs no ffmpeg to enumerate. */
    V4L2_SYSFS,

    /** An OS none of the enumerators has a branch for. */
    UNSUPPORTED_OS,

    /** Nothing has enumerated in this process yet — a real state on the presenter restore path. */
    NOT_RUN,

    ;

    /**
     * Whether devices this enumerator named can be opened at all.
     *
     * The fallbacks name hardware the platform knows about, in a namespace ffmpeg does not accept —
     * a PnP friendly name is not a DirectShow filter name, and a `system_profiler` position is not
     * an AVFoundation index. Capture goes through ffmpeg either way, so those entries are decoration
     * whatever else is true of the machine.
     *
     * One property rather than the same `== PNP_FALLBACK || == SYSTEM_PROFILER_FALLBACK` in two
     * places: the hint and the blind-ffmpeg report ask the identical question, and they disagreed
     * once already — the report was Windows-only while the hint keyed on something else entirely.
     */
    val listsUnopenableDevices: Boolean
        get() = this == PNP_FALLBACK || this == SYSTEM_PROFILER_FALLBACK
}

/**
 * What one enumeration found, in shapes rather than names.
 *
 * Exists so a camera that fails to open can be reported with the context that explains *why* — which
 * tool named it, whether ffmpeg was there at all, how many devices each tool saw. Without this a
 * report says only "could not open", and the two causes behind issue #462 (ffmpeg absent; ffmpeg
 * present but the device came from the platform inventory) are indistinguishable, which is what
 * forced us to ask a reporter to run `ffmpeg -list_devices` by hand.
 *
 * Every field except [names] is a count or an enum, and those are what reports carry.
 */
internal data class CameraEnumerationFacts(
    val enumerator: CameraEnumerator,
    /** How many devices ffmpeg named — DirectShow or AVFoundation. */
    val ffmpegListedCount: Int,
    /** How many the fallback inventory named. Non-zero only when ffmpeg named none. */
    val fallbackListedCount: Int,
    val deckLinkCount: Int,
    val ffmpegAvailable: Boolean,
    val enumeratedAtMs: Long,
    /**
     * The device names of this listing, lower-cased.
     *
     * **Local only. This never leaves the process, and must never be put in a tag, an extra, or a
     * message.** It is here for exactly one question — was the device we are failing on in the last
     * listing at all — whose *answer* is a boolean and is what gets reported. A camera is often
     * named after the person or room it belongs to, and the reporting stance for this subsystem is
     * shapes and counts only.
     */
    val names: Set<String>,
)

/** A listing and the facts about how it was produced, returned together so neither can drift. */
internal data class CameraListing(
    val devices: List<CameraDevice>,
    val facts: CameraEnumerationFacts,
)

/** Facts for a process where enumeration has not run — honest, and itself diagnostic. */
internal fun notEnumeratedFacts() = CameraEnumerationFacts(
    enumerator = CameraEnumerator.NOT_RUN,
    ffmpegListedCount = 0,
    fallbackListedCount = 0,
    deckLinkCount = 0,
    ffmpegAvailable = false,
    enumeratedAtMs = 0L,
    names = emptySet(),
)
