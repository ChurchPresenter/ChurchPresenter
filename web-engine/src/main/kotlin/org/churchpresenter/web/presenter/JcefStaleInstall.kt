package org.churchpresenter.web.presenter

import me.friwi.jcefmaven.impl.step.check.CefInstallationChecker
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException

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

/**
 * Whether [cause], or anything it wraps, is a file under [installDir] the install could not open --
 * held by another process after [clearIncompleteInstall] had looked, as a Chromium helper still
 * running from a session that did not exit holds `chrome_elf.dll`. Like the check before it, that
 * is the machine and not a defect.
 *
 * Decided by exception type and by the path alone. A [FileNotFoundException]'s message is
 * "<path> (<reason>)", the reason in the OS's own language, so only its path is compared; an
 * [AccessDeniedException] names its file outright.
 */
internal fun lockedInInstall(cause: Throwable, installDir: File): Boolean {
    val prefix = normalized(installDir.absolutePath).trimEnd('/') + "/"
    return generateSequence(cause) { it.cause.takeIf { next -> next !== it } }
        .take(MAX_CAUSES)
        .mapNotNull { t ->
            when (t) {
                is AccessDeniedException -> t.file
                is FileNotFoundException -> t.message
                else -> null
            }
        }
        .any { normalized(it).startsWith(prefix, ignoreCase = true) }
}

/** [path] with Windows' separators as `/`, so one prefix comparison works on either. */
private fun normalized(path: String): String = path.replace('\\', '/')

/** How deep a cause chain is followed; far past any real wrapping, short of a cycle. */
private const val MAX_CAUSES = 8
