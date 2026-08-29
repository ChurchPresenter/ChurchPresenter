package org.churchpresenter.app.churchpresenter.composables

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_camera_error_decklink_in_use
import churchpresenter.composeapp.generated.resources.canvas_camera_error_device_busy
import churchpresenter.composeapp.generated.resources.canvas_camera_error_device_not_found
import churchpresenter.composeapp.generated.resources.canvas_camera_error_no_frames
import churchpresenter.composeapp.generated.resources.canvas_camera_error_permission_denied
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unknown
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unsupported_format
import churchpresenter.composeapp.generated.resources.canvas_camera_error_unsupported_framerate
import org.jetbrains.compose.resources.StringResource

/**
 * What the operator is told about [failure].
 *
 * This lives apart from [CameraFailure] itself so the classifier stays free of generated
 * resources — it is a function over ffmpeg's stderr and is tested as one — and apart from the
 * renderer so it can be tested without composing anything.
 *
 * The `when` is exhaustive with no `else` on purpose: a new [CameraFailure] constant should fail
 * to compile here rather than quietly reach the screen as "could not be opened".
 */
internal fun cameraFailureStringRes(failure: CameraFailure): StringResource = when (failure) {
    CameraFailure.PERMISSION_DENIED -> Res.string.canvas_camera_error_permission_denied
    CameraFailure.UNSUPPORTED_PIXEL_FORMAT -> Res.string.canvas_camera_error_unsupported_format
    CameraFailure.UNSUPPORTED_FRAMERATE -> Res.string.canvas_camera_error_unsupported_framerate
    CameraFailure.DEVICE_BUSY -> Res.string.canvas_camera_error_device_busy
    CameraFailure.DEVICE_NOT_FOUND -> Res.string.canvas_camera_error_device_not_found
    CameraFailure.NO_FRAMES -> Res.string.canvas_camera_error_no_frames
    CameraFailure.DECKLINK_INPUT_IN_USE -> Res.string.canvas_camera_error_decklink_in_use
    CameraFailure.UNKNOWN -> Res.string.canvas_camera_error_unknown
}
