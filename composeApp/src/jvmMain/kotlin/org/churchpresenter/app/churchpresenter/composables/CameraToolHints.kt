package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_camera_ffmpeg_required
import churchpresenter.composeapp.generated.resources.canvas_camera_none_found
import churchpresenter.composeapp.generated.resources.canvas_camera_unopenable_listing
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
 * Since ffmpeg is bundled with the app, "not having it" stopped being the interesting case and a
 * second one took its place. A fallback enumerator only runs when ffmpeg listed nothing, and its
 * devices are named by the platform's own inventory rather than by ffmpeg — so they cannot be opened
 * whether or not ffmpeg is present. With a bundled binary [ffmpegAvailable] is true, which used to
 * suppress the hint entirely and left the operator with a dropdown of cameras that silently show
 * nothing. What matters is therefore **whether the listing is openable**, which [enumerator] says,
 * not whether the tool is installed.
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
    enumerator: CameraEnumerator? = null,
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
    } else if (devices.isNotEmpty() && enumerator != null && enumerator.listsUnopenableDevices) {
        // ffmpeg is here and still listed nothing, so what is in the dropdown came from the
        // platform's inventory and none of it will open. Saying so is the whole point: the picker
        // looks completely ordinary otherwise.
        hints += Res.string.canvas_camera_unopenable_listing
    }

    return hints
}
