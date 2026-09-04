package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.CrashReporter
import java.util.concurrent.atomic.AtomicBoolean

/*
 * What a camera failure report says about the enumeration behind it.
 *
 * Apart from `CameraDiagnostics.kt`, which is about reading ffmpeg's own output, and apart from the
 * capture cache, which is about opening devices — these are pure functions over what enumeration
 * found, so the tag vocabulary can be asserted whole in a test without a camera, a process or Sentry.
 */


/** Tag names, in one place so the report and its test cannot drift apart. */
internal object CameraReportTag {
    const val FFMPEG = "camera.ffmpeg"
    const val ENUMERATOR = "camera.enumerator"
    const val LISTED = "camera.listed"
    const val DEVICE_LISTED = "camera.device_listed"
}

/**
 * What a camera report says about the enumeration behind it, as tags.
 *
 * **Tag values are not scrubbed.** `CrashReporter.scrubPii` reaches messages, string extras,
 * breadcrumbs and contexts — not tags — so every value here is an enum name or a count, and none of
 * it is derived from what the operator's hardware is called. `CameraReportFactsTest` asserts that.
 *
 * Together these settle, from the event alone, which of the two causes behind issue #462 is in play:
 * `camera.ffmpeg=absent` is one, and `camera.enumerator=pnp_fallback` is the other — the fallback
 * runs only when ffmpeg listed nothing, so the enumerator name alone proves the DirectShow count was
 * zero.
 *
 * [ffmpegAvailable] is passed rather than read from [facts] because the capture path knows it even
 * when nothing has enumerated: it has just tested it.
 */
internal fun cameraEnumerationTags(
    facts: CameraEnumerationFacts?,
    deviceName: String,
    ffmpegAvailable: Boolean,
): Map<String, String> {
    val enumerator = facts?.enumerator ?: CameraEnumerator.NOT_RUN
    val listed = when (enumerator) {
        CameraEnumerator.PNP_FALLBACK, CameraEnumerator.SYSTEM_PROFILER_FALLBACK ->
            facts?.fallbackListedCount ?: 0
        else -> facts?.ffmpegListedCount ?: 0
    }
    return mapOf(
        CameraReportTag.FFMPEG to if (ffmpegAvailable) "present" else "absent",
        CameraReportTag.ENUMERATOR to enumerator.name.lowercase(),
        CameraReportTag.LISTED to listed.toString(),
        CameraReportTag.DEVICE_LISTED to deviceListedValue(facts, deviceName),
    )
}

/**
 * Whether the device being captured was in the last listing.
 *
 * "no" is the interesting answer: a settings file carried from another machine, a camera unplugged
 * since the picker was opened, or a name nothing answers to. Distinct from "not_enumerated", which
 * says only that we never looked in this process.
 */
private fun deviceListedValue(facts: CameraEnumerationFacts?, deviceName: String): String = when {
    facts == null || facts.enumerator == CameraEnumerator.NOT_RUN -> "not_enumerated"
    deviceName.lowercase() in facts.names -> "yes"
    else -> "no"
}

/**
 * The same facts as a readable block, for the report's one extra.
 *
 * Counts and enum names only, for the reason given on [cameraEnumerationTags]. An extra *is*
 * scrubbed, but relying on that for data we chose not to send would be the wrong way round.
 */
internal fun cameraEnumerationExtra(
    facts: CameraEnumerationFacts?,
    deviceName: String,
    ffmpegAvailable: Boolean,
    nowMs: Long = System.currentTimeMillis(),
): String {
    val enumerator = facts?.enumerator ?: CameraEnumerator.NOT_RUN
    val ageSeconds = if (facts == null || facts.enumeratedAtMs <= 0L) "never"
    else ((nowMs - facts.enumeratedAtMs) / MILLIS_PER_SECOND).toString()
    return listOf(
        "enumerator=${enumerator.name.lowercase()}",
        "ffmpeg=${if (ffmpegAvailable) "present" else "absent"}",
        "ffmpeg_listed=${facts?.ffmpegListedCount ?: 0}",
        "fallback_listed=${facts?.fallbackListedCount ?: 0}",
        "decklink=${facts?.deckLinkCount ?: 0}",
        "device_listed=${deviceListedValue(facts, deviceName)}",
        "enumerated_age_s=$ageSeconds",
    ).joinToString("\n")
}

private const val MILLIS_PER_SECOND = 1000L

/**
 * Whether a listing is the "ffmpeg works, Windows sees a camera, ffmpeg does not" state.
 *
 * A pure predicate rather than a report call so the decision is testable without standing up Sentry.
 * All three clauses matter: ffmpeg resolved (so this is not simply a machine without it), the
 * fallback won (so ffmpeg listed no DirectShow device), and the fallback found something (so this is
 * not a machine with no camera at all — reporting that would make the operator's own hardware
 * inventory look like a defect).
 */
internal fun shouldReportBlindFfmpeg(facts: CameraEnumerationFacts?): Boolean =
    facts != null &&
        facts.ffmpegAvailable &&
        facts.enumerator == CameraEnumerator.PNP_FALLBACK &&
        facts.fallbackListedCount > 0

/**
 * A gate that lets exactly one report through per process.
 *
 * For facts that cannot change while the app runs — whether ffmpeg resolved is a `by lazy` — so a
 * second event would carry nothing the first did not. Atomic because captures start on
 * `Dispatchers.Default` and two canvas tiles can reach the same gate at once; a plain `var` would
 * let both through.
 */
internal class ReportOnce {
    private val fired = AtomicBoolean(false)

    /** True the first time only. */
    fun claim(): Boolean = fired.compareAndSet(false, true)
}

/**
 * Says once that a chosen camera cannot open because ffmpeg is not installed.
 *
 * Top-level rather than a member of the capture cache so that object stays under its function
 * threshold, and because it is pure apart from the one report call.
 *
 * The title is constant so every affected operator lands in one issue rather than one per machine;
 * what differs between them is in the tags.
 */
internal fun reportCameraFfmpegMissing(
    source: SceneSource.CameraSource,
    facts: CameraEnumerationFacts?,
    gate: ReportOnce,
) {
    if (!gate.claim()) return
    CrashReporter.reportWarning(
        "Camera: ffmpeg is not installed, so the selected camera cannot be opened",
        tags = mapOf(
            "subsystem" to "camera",
            "device_scheme" to deviceScheme(source.devicePath),
            "failure_cause" to CameraFailure.FFMPEG_MISSING.name.lowercase(),
        ) + cameraEnumerationTags(facts, source.deviceName, ffmpegAvailable = false),
        extras = mapOf(
            "camera_enumeration" to cameraEnumerationExtra(facts, source.deviceName, ffmpegAvailable = false)
        )
    )
}

/**
 * Says once that a saved AVFoundation index was refused because the name it was stored under is no
 * longer at it.
 *
 * The tag worth having is [refusedName]: an index that now holds `Capture screen 0` means the app
 * was one call away from recording the operator's display and asking macOS for permission to do it
 * on every launch — issue #478 — while an index holding another camera, or nothing, is the ordinary
 * "that device is gone". They are one decision (see `resolveAvfoundationDevice`) and two very
 * different reports.
 *
 * Once per process, like [reportCameraFfmpegMissing], because a canvas layer and a background on
 * the same stale device would otherwise each send one.
 */
internal fun reportAvfIndexDrift(
    source: SceneSource.CameraSource,
    refusedName: String,
    facts: CameraEnumerationFacts?,
    gate: ReportOnce,
) {
    if (!gate.claim()) return
    CrashReporter.reportWarning(
        "Camera: saved AVFoundation index no longer holds the saved device",
        tags = mapOf(
            "subsystem" to "camera",
            "device_scheme" to deviceScheme(source.devicePath),
            "failure_cause" to CameraFailure.DEVICE_NOT_FOUND.name.lowercase(),
            "avf_index_now" to when {
                refusedName.isBlank() -> "out_of_range"
                isScreenCaptureDevice(refusedName) -> "screen_capture"
                else -> "other_camera"
            },
        ) + cameraEnumerationTags(facts, source.deviceName, ffmpegAvailable = true),
        extras = mapOf(
            "camera_enumeration" to cameraEnumerationExtra(facts, source.deviceName, ffmpegAvailable = true)
        )
    )
}
