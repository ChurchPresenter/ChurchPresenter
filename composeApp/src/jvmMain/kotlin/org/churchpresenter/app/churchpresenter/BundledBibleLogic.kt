package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.BibleSettings
import java.io.File

/**
 * Whether the bundled KJV has to be written out and made primary.
 *
 * Both halves matter: a folder chosen but no translation picked yet is a half-finished setup the
 * operator is in the middle of, and dropping a Bible into it would pick for them.
 */
internal fun shouldBundleDefaultBible(settings: BibleSettings): Boolean =
    settings.storageDirectory.isEmpty() && settings.primaryBible.isEmpty()

/**
 * Makes sure [dir] is a directory that can be written to, and names the problem when it cannot be.
 *
 * The bundling code used to call `mkdirs()` and ignore what it answered, so a folder that could not
 * be created surfaced a sentence later as `FileNotFoundException: …/Bibles/kjv1769.spb (No such
 * file or directory)` — a message about a file, for a problem with its parent, which reads as a
 * missing resource in the app rather than a home directory the process cannot write into.
 *
 * The reason is a fixed phrase, never the path: it is reported to the crash service, and a user's
 * home directory carries their name.
 *
 * @return null when the directory is ready, otherwise why it is not.
 */
internal fun bundledBibleDirProblem(dir: File): String? = when {
    dir.isDirectory -> if (dir.canWrite()) null else "not writable"
    dir.exists() -> "occupied by a file"
    dir.mkdirs() -> null
    else -> "could not be created"
}

/**
 * Why the bundled Bible cannot be installed into [dir], or null when it can.
 *
 * A folder that cannot be written to only blocks the bundle when [fileName] is not already sitting
 * in it. A read-only Bibles folder holding the copy from an earlier launch — a managed install, or
 * one locked down after the fact and whose settings were later reset — is a working setup, and
 * skipping it would throw that configuration away and send the user to the setup wizard instead.
 * `canWrite()` on a directory is unreliable on Windows besides, so this branch can fire spuriously.
 *
 * The reason is a fixed phrase, never the path: it is reported to the crash service, and a user's
 * home directory carries their name.
 */
internal fun bundledBibleSkipReason(dir: File, fileName: String): String? {
    val problem = bundledBibleDirProblem(dir) ?: return null
    return if (File(dir, fileName).isFile) null else problem
}

/**
 * Whether generated lower thirds have somewhere to be written.
 *
 * Checked against the filesystem rather than the setting alone: a folder configured once and since
 * moved or deleted would otherwise send the generator to a path it cannot save into.
 */
internal fun isUsableOutputDir(path: String): Boolean = path.isNotEmpty() && File(path).isDirectory

/** Where a translation lives, as an absolute path, so a follower can be handed the file itself. */
internal fun bibleFilePath(storageDirectory: String, translation: String): String =
    File(storageDirectory, translation).absolutePath
