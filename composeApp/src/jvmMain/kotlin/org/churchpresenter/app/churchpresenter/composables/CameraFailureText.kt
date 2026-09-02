package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_camera_error_config_refused
import churchpresenter.composeapp.generated.resources.canvas_camera_error_decklink_in_use
import churchpresenter.composeapp.generated.resources.canvas_camera_error_device_busy
import churchpresenter.composeapp.generated.resources.canvas_camera_error_device_not_found
import churchpresenter.composeapp.generated.resources.canvas_camera_error_device_not_found_windows
import churchpresenter.composeapp.generated.resources.canvas_camera_error_ffmpeg_missing
import churchpresenter.composeapp.generated.resources.canvas_camera_error_no_frames
import churchpresenter.composeapp.generated.resources.canvas_camera_error_permission_denied
import churchpresenter.composeapp.generated.resources.canvas_camera_error_permission_denied_windows
import churchpresenter.composeapp.generated.resources.canvas_camera_error_permission_or_unavailable
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unknown
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unsupported_format
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unsupported_framerate
import org.jetbrains.compose.resources.StringResource

/**
 * What the operator is told about [failure] on [osName].
 *
 * This lives apart from [CameraFailure] itself so the classifier stays free of generated
 * resources — it is a function over ffmpeg's stderr and is tested as one — and apart from the
 * renderer so it can be tested without composing anything.
 *
 * The `when` is exhaustive with no `else` on purpose: a new [CameraFailure] constant should fail
 * to compile here rather than quietly reach the screen as "could not be opened".
 *
 * Two failures read differently per platform, because their remedy is a place in that platform's
 * settings and naming the wrong one is worse than saying nothing. [osName] defaults to the real OS;
 * tests pass one explicitly rather than faking the system property, which skiko latches.
 */
internal fun cameraFailureStringRes(
    failure: CameraFailure,
    osName: String = System.getProperty("os.name", ""),
): StringResource {
    val windows = osName.lowercase().contains("win")
    return when (failure) {
        CameraFailure.PERMISSION_DENIED ->
            if (windows) Res.string.canvas_camera_error_permission_denied_windows
            else Res.string.canvas_camera_error_permission_denied
        CameraFailure.UNSUPPORTED_PIXEL_FORMAT -> Res.string.canvas_camera_error_unsupported_format
        CameraFailure.UNSUPPORTED_FRAMERATE -> Res.string.canvas_camera_error_unsupported_framerate
        CameraFailure.DEVICE_BUSY -> Res.string.canvas_camera_error_device_busy
        CameraFailure.DEVICE_CONFIG_REFUSED -> Res.string.canvas_camera_error_config_refused
        // Only reachable on macOS — it is what an avfoundation I/O error classifies to — so it does
        // not branch on [osName] the way the two above it do.
        CameraFailure.PERMISSION_OR_UNAVAILABLE ->
            Res.string.canvas_camera_error_permission_or_unavailable
        CameraFailure.DEVICE_NOT_FOUND ->
            // Windows gets its own sentence because there the likeliest cause is not an unplugged
            // camera but a name that DirectShow does not answer to — see `windowsCamerasFrom`.
            if (windows) Res.string.canvas_camera_error_device_not_found_windows
            else Res.string.canvas_camera_error_device_not_found
        CameraFailure.NO_FRAMES -> Res.string.canvas_camera_error_no_frames
        CameraFailure.DECKLINK_INPUT_IN_USE -> Res.string.canvas_camera_error_decklink_in_use
        CameraFailure.FFMPEG_MISSING -> Res.string.canvas_camera_error_ffmpeg_missing
        CameraFailure.UNKNOWN -> Res.string.canvas_camera_error_unknown
    }
}
