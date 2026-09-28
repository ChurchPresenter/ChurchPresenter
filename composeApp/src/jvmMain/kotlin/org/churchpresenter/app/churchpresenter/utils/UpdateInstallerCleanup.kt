package org.churchpresenter.app.churchpresenter.utils

import java.io.File

/** Name prefix of every installer the updater downloads, which is what [deleteLeftoverUpdateInstallers] matches. */
internal const val UPDATE_INSTALLER_PREFIX = "ChurchPresenter-update"

/**
 * Deletes the installers earlier updates left in [dir], and returns how many went.
 *
 * A download cannot be deleted when the app exits — the installer is launched as it does (see the
 * note on `deleteOnExit` in `UpdateAvailableDialog`), so the file is still in use. It is deleted on
 * the next launch instead, before any new download can start. Without this, every update leaves its
 * installer behind: macOS and Linux clear their temp directories on their own, Windows `%TEMP%`
 * does not.
 *
 * Only regular files named with [UPDATE_INSTALLER_PREFIX] are touched. One that cannot be deleted —
 * an `.msi` Windows still has locked — is left for the next launch, and nothing here throws.
 */
internal fun deleteLeftoverUpdateInstallers(
    dir: File = File(System.getProperty("java.io.tmpdir")),
): Int = runCatching {
    dir.listFiles { file -> file.isFile && file.name.startsWith(UPDATE_INSTALLER_PREFIX) }
        .orEmpty()
        .count { it.delete() }
}.getOrDefault(0)
