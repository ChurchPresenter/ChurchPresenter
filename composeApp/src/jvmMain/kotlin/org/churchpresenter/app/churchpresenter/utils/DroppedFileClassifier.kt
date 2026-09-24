package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.utils.Constants

/**
 * Which schedule action a dropped file maps to, decided by its extension. Extracted from
 * ScheduleTab's `handleDroppedFiles` so the extension → action rule is tested without touching the
 * filesystem or the view model. The folder-vs-file and image-count handling stays in the tab (it is
 * genuine File I/O); only this pure classification moves here.
 *
 * ## The media lists are the player's own
 *
 * [Constants.AUDIO_EXTENSIONS] and [Constants.VIDEO_EXTENSIONS] are what the Media tab opens, and
 * they are read here rather than copied. They used to be copied, and the copy had drifted: dropping
 * a `.wmv`, `.m4v`, `.flv`, `.m4a`, `.wma`, `.aac`, `.ogg`, `.opus` or `.aiff` fell through to
 * [DroppedFileAction.NONE] and the schedule did nothing at all — no row, no message — even though
 * the Media tab plays every one of them. Nine formats, and `.wmv` among them, which is why this
 * reads on Windows as drag-and-drop not being supported (issue #606).
 */

/** Image extensions — also used by the tab to count pictures inside a dropped folder. */
internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
private val PRESENTATION_EXTENSIONS = setOf("ppt", "pptx", "key", "pdf")

internal enum class DroppedFileAction { PRESENTATION, MEDIA, PICTURE, LOWER_THIRD, NONE }

/** The action a single dropped file (already lower-cased [extension]) should be added as. */
internal fun classifyDroppedFile(extension: String): DroppedFileAction = when {
    extension in PRESENTATION_EXTENSIONS -> DroppedFileAction.PRESENTATION
    extension in Constants.VIDEO_EXTENSIONS || extension in Constants.AUDIO_EXTENSIONS ->
        DroppedFileAction.MEDIA
    extension in IMAGE_EXTENSIONS -> DroppedFileAction.PICTURE
    extension == "json" -> DroppedFileAction.LOWER_THIRD
    else -> DroppedFileAction.NONE
}
