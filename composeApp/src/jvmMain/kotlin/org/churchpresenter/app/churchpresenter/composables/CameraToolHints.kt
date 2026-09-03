package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_camera_ffmpeg_required
import churchpresenter.composeapp.generated.resources.canvas_camera_none_found
import churchpresenter.composeapp.generated.resources.canvas_camera_v4l2_hint
import org.jetbrains.compose.resources.StringResource

/**
 * What to tell the operator about the tools a camera needs, given what enumeration found.
 *
 * One function rather than a block of conditions at each picker, because the two pickers disagreed
 * and one of them was wrong. `CameraPickerRow` only ever mentioned ffmpeg when the device list came
 * back *empty* — but on Windows without ffmpeg the list is not empty. The PnP fallback fills it with
 * names that cannot be opened, so the operator saw an ordinary dropdown, picked their camera, and
 * got a black rectangle with nothing on screen explaining why. That is the shape of the
 * "USB camera is not detecting" report.
 *
 * So the ffmpeg sentence is **not** conditioned on emptiness: on any platform that opens cameras
 * through ffmpeg, not having it is worth saying whether or not a name happens to be listed.
 *
 * [devices] null is "nothing has enumerated yet" and is deliberately distinct from "there are none"
 * — saying "No cameras found" before anything has looked is a wrong answer that the operator acts
 * on, and enumeration takes a noticeable moment.
 *
 * [ffmpegAvailable] is a parameter rather than a call to [isFfmpegAvailable] so this is a pure
 * decision a test can drive. `FfmpegBinary` caches what it resolved and must not grow a mutable
 * seam for testing — its `recheck` is for the settings row, not for a test.
 */
internal fun cameraHintStringRes(
    osName: String,
    devices: List<CameraDevice>?,
    ffmpegAvailable: Boolean,
): List<StringResource> {
    if (devices == null) return emptyList()

    val os = osName.lowercase()
    val hints = mutableListOf<StringResource>()

    if (devices.isEmpty()) hints += Res.string.canvas_camera_none_found

    // Linux opens cameras through v4l2 and needs ffmpeg only for virtual devices, so it keeps its
    // own hint and is never told to install ffmpeg.
    if (os.contains("linux")) {
        if (devices.isEmpty()) hints += Res.string.canvas_camera_v4l2_hint
    } else if (!ffmpegAvailable) {
        hints += Res.string.canvas_camera_ffmpeg_required
    }

    return hints
}
