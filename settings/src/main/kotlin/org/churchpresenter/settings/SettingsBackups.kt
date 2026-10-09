package org.churchpresenter.settings

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

// The copies kept of the settings file: before a migration rewrites it, and when it cannot be read
// at all.

/** How many of each settings-backup family survive a prune. */
private const val BACKUPS_KEPT = 3

private const val BACKUP_PREFIX = "settings.json"

private const val CORRUPT_PREFIX = "settings.json.corrupt-"

/** Per process, not per instance: several call sites build a SettingsManager of their own. */
private val backupsPruned = AtomicBoolean(false)

/** Snapshots [source] as `settings.json.v<version>.bak` before this build rewrites it into a
 * different schema. Never overwrites an existing snapshot: the oldest copy for a given version
 * is the one taken before any lossy rewrite, so it is the one worth keeping. */
internal fun backupBeforeRewrite(appDataDir: File, source: File, version: Int) {
    try {
        val target = File(appDataDir, "settings.json.v$version.bak")
        if (!target.exists()) Files.copy(source.toPath(), target.toPath())
    } catch (_: Exception) {
        // A failed backup must never block startup — carry on with the load.
    }
}

/**
 * Keeps the newest [BACKUPS_KEPT] of each backup family and deletes the rest, once per process.
 *
 * Both families are written once per event and never cleaned: one `settings.json.v<n>.bak` per
 * schema version a machine has ever migrated through, and one timestamped
 * `settings.json.corrupt-<stamp>` per failed load. Each is a full copy of the settings
 * document, and the oldest of them describe a schema no build in service still reads. The
 * newest few are the ones worth recovering from; a machine carrying `.v0.bak` alongside
 * `.v6.bak` and a legacy `.bak` is just carrying clutter through every backup and sync.
 */
internal fun pruneBackupsOnce(appDataDir: File) {
    if (!backupsPruned.compareAndSet(false, true)) return
    pruneBackups(appDataDir)
}

internal fun pruneBackups(appDataDir: File) {
    try {
        val files = appDataDir.listFiles() ?: return
        val schemaBackups = files.filter {
            it.isFile && it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".bak")
        }
        val corruptCopies = files.filter { it.isFile && it.name.startsWith(CORRUPT_PREFIX) }
        for (family in listOf(schemaBackups, corruptCopies)) {
            family.sortedByDescending { it.lastModified() }.drop(BACKUPS_KEPT).forEach { it.delete() }
        }
    } catch (_: Exception) {
        // Housekeeping must never stop the settings from loading.
    }
}

/** Copies (never moves) an undecodable settings.json aside so the original survives the
 * default-settings save that follows. Timestamped, so repeated failed launches don't collapse
 * into a single copy. */
internal fun preserveUnreadableFile(appDataDir: File, settingsFile: File) {
    try {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = File(appDataDir, "settings.json.corrupt-$stamp")
        if (!target.exists()) Files.copy(settingsFile.toPath(), target.toPath())
    } catch (_: Exception) {
        // Best effort only.
    }
}
