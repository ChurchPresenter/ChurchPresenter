package org.churchpresenter.sharedui.utils

/**
 * Whether [fileName] is a file the operating system or an editor leaves beside the operator's own,
 * rather than one of theirs: a macOS AppleDouble `._` companion (written onto every FAT/exFAT and
 * network drive a Mac touches) or a Microsoft Office / WPS `~$` lock file.
 *
 * Both carry the extension of the file they shadow, so a folder scan by extension picks them up as
 * a picture or a Bible -- and then fails to read them (CHURCH-PRESENTER-DESKTOP-9V/9W, 9S/9T).
 */
fun isSystemArtifact(fileName: String): Boolean =
    fileName.startsWith(APPLE_DOUBLE_PREFIX) || fileName.startsWith(OFFICE_LOCK_PREFIX)

private const val APPLE_DOUBLE_PREFIX = "._"
private const val OFFICE_LOCK_PREFIX = "~$"
