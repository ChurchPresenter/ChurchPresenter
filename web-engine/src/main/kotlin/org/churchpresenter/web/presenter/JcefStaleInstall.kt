package org.churchpresenter.web.presenter

import me.friwi.jcefmaven.impl.step.check.CefInstallationChecker
import java.io.File

/**
 * Clears an install of [installDir] that jcefmaven would discard, or says why it cannot.
 *
 * jcefmaven deletes an install that is not complete and then creates its directory again, but
 * its delete only logs a file it cannot remove and goes on: the directory survives, and the
 * create fails with "Could not create installation directory". That is a file held open by
 * another process -- a Chromium helper left running by a session that did not exit -- or, on
 * Windows, one marked read-only, which `File.delete` refuses. Read-only marks are cleared here
 * before anything is deleted. A file that still will not go is the machine, like the two
 * [JcefInstall.installBlocker] checks: "install_in_use", and the next root is tried.
 *
 * [isComplete] is jcefmaven's own judgement, so a working install is never touched; [delete] is
 * the one step a test cannot make fail on demand.
 */
internal fun clearIncompleteInstall(
    installDir: File,
    isComplete: (File) -> Boolean,
    delete: (File) -> Boolean = File::delete,
): String? {
    if (!installDir.exists() || isComplete(installDir)) return null
    installDir.walkTopDown().forEach { it.setWritable(true) }
    installDir.walkBottomUp().forEach { delete(it) }
    return if (installDir.exists()) "install_in_use" else null
}

/**
 * Whether jcefmaven would use [installDir] as it is -- true when it cannot say, so nothing is
 * cleared on a guess.
 */
internal fun installIsComplete(installDir: File): Boolean =
    runCatching { CefInstallationChecker.checkInstallation(installDir) }.getOrDefault(true)
