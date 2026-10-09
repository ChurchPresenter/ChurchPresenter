package org.churchpresenter.settings

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import org.churchpresenter.settings.AppSettings.Companion.CURRENT_SETTINGS_VERSION
import org.churchpresenter.settings.utils.AppDataDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

private const val VERSION_HIDDEN_TABS = 5

/** The Schedule toolbar gained a Calendar button that starts hidden. */
private const val VERSION_CALENDAR_BUTTON = 11

/** A quick-tray tile's lower-third half may inherit the output's band — see [SettingsManager]. */
private const val VERSION_QUICK_BACKGROUND_INHERITS = 17

/** Version 20: the preview panel's groups became a layout. */
private const val VERSION_PREVIEW_LAYOUTS = 20

// Load, migrate, save, import and export, all against one file. The per-version steps it runs are
// in
// the Settings*Migrations.kt files beside it, the repairs in SettingsRepair.kt and the backups in
// SettingsBackups.kt.
class SettingsManager {
    private val appDataDir = AppDataDir.resolve()
    private val settingsFile = File(appDataDir, "settings.json")
    private val settingsTmpFile = File(appDataDir, "settings.json.tmp")
    val lottiePresetsDir: File = File(appDataDir, "lottie_presets")
    /** Where the lower-third band generator saves its templates, and where the picker starts. */
    val bibleLowerThirdsDir: File = bibleLowerThirdsDir(appDataDir)

    private var cachedSettings: AppSettings? = null

    init {
        // Create app data directory if it doesn't exist
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
        if (!lottiePresetsDir.exists()) {
            lottiePresetsDir.mkdirs()
        }
        if (!bibleLowerThirdsDir.exists()) {
            bibleLowerThirdsDir.mkdirs()
        }
    }

    /**
     * The raw-JSON migration steps, in the order they must be applied, each tagged with the schema
     * version it produces. A step only runs when the document's version is below its target, so a
     * file already at [CURRENT_SETTINGS_VERSION] is decoded without any rewriting at all — where
     * previously every step re-scanned the whole document on every single load.
     *
     * These are listed in the exact order the previous nested-call chain applied them, which is not
     * their chronological order — preserved verbatim so this rework changes no behaviour. Note one
     * pre-existing consequence of that order, left as-is rather than silently altered: version 1
     * rewrites entries *inside* `screenAssignments`, but version 2 is what creates that array from
     * the older `screen1-4Assignment` fields. A document old enough to still carry those numbered
     * fields therefore never gets its `showBible`/`showSongs` booleans converted. Such a document
     * is unlikely to exist (the booleans postdate the array), and reordering would change what
     * those users load, so the behaviour is documented rather than "fixed" on assumption.
     *
     * Versions 5 ([migrateHiddenTabs]) and 6 (the bible translation list) have no entry here — they
     * operate on the decoded object rather than the raw text, and so run separately in
     * [migrateAndDecode].
     */
    private val rawMigrations: List<Pair<Int, (String) -> String>> = listOf(
        1 to ::migrateScreenAssignmentModes,
        2 to ::migrateProjectionSettings,
        3 to ::migrateCompanionSatelliteStartPage,
        4 to ::migrateCompanionSatelliteRowColumnRangeBackToCount,
        7 to ::migrateStageMonitorChords,
        // Also version 7: the stage monitor's chord switch and its zone names moved in the same
        // unreleased change, so they share a version rather than spending two on one release.
        7 to ::migrateStageMonitorZoneNames,
        8 to ::migrateLowerThirdHeight,
        9 to ::migrateSongNumberCorner,
        10 to ::migrateSparseOutputOverrides,
        // Also version 12, and before the profiles are made: they are copies of these two sections,
        // so they must be copies of what the outputs were actually drawing.
        12 to ::migrateRepairedSongAndBible,
        12 to ::migrateOutputProfiles,
        // Also version 12, after the profiles exist, so theirs are rewritten along with the document's.
        12 to ::migrateBibleArrangementAsDrawn,
        // Version 13 lifted a profile's dictionary styling onto the document while the dictionary was
        // briefly one per install. Profiles own it again, so the step is gone: all it could still do
        // is overwrite the document's copy with one screen's.
        14 to ::migrateStylingIntoProfiles,
        15 to ::migrateScaleModesIntoProfiles,
        16 to ::migrateTitleSlideNumberStyle,
        18 to ::migrateSectionLabelStyle,
        19 to ::migrateBibleOffsetsToBoxes,
        21 to ::migrateTopLevelStylingOut,
        22 to ::migrateBibleAndSongsOut,
        23 to ::migrateShowSwitchesIntoLooks,
    )

    /**
     * [raw] without the document-level styling the profiles own: every save goes through this, since
     * the encoder writes every field back. The profiles' own copies are untouched.
     */
    internal fun stripProfileOwnedStyling(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val stripped = root.toMutableMap()
        stripped.stripOverlayStyling()
        stripped.stripBibleAndSongStyling()
        return JsonObject(stripped).toString()
    }

    fun loadSettings(): AppSettings {
        cachedSettings?.let { return it }
        pruneBackupsOnce(appDataDir)
        return try {
            if (settingsFile.exists()) {
                val raw = settingsFile.readText()
                try {
                    migrateAndDecode(raw, backupSource = settingsFile)
                } catch (_: Exception) {
                    // The document is unreadable — malformed JSON, a truncated write from a
                    // hard power-off, a bad hand-edit. Returning defaults here silently discards
                    // the user's entire configuration, so keep a copy they (or we) can recover
                    // from before the next save overwrites the original.
                    preserveUnreadableFile(appDataDir, settingsFile)
                    AppSettings()
                }
            } else {
                AppSettings() // Return default settings
            }
        } catch (_: Exception) {
            AppSettings() // Return default settings on error
        }.also { cachedSettings = it }
    }

    /**
     * Brings a settings document up to [CURRENT_SETTINGS_VERSION] and decodes it. Shared by the
     * normal startup load and by Settings → Import, so an exported file from an older build is
     * migrated on import rather than silently losing every field a migration would have converted.
     *
     * @param backupSource when non-null, the on-disk file to snapshot before any migration or
     *   version-downgrade rewrite. Import passes null — the user's chosen source file is not ours
     *   to write next to, and it is left untouched regardless.
     * @throws Exception if the document cannot be parsed; callers decide how to recover.
     */
    fun migrateAndDecode(raw: String, backupSource: File? = null): AppSettings {
        val fromVersion = readSettingsVersion(raw)

        if (fromVersion > CURRENT_SETTINGS_VERSION) {
            // Written by a NEWER build than this one — a downgrade, or a config copied from a
            // machine that is further ahead. `ignoreUnknownKeys` drops whatever this build doesn't
            // recognise, and the next save writes that stripped document back permanently, so
            // snapshot the full-fidelity original first. The decoded version field deliberately
            // keeps its higher number: the newer build's own migrations have already run against
            // this data and must not run a second time when it is loaded there again.
            backupSource?.let { backupBeforeRewrite(appDataDir, it, fromVersion) }
            return settingsJson.decodeFromString<AppSettings>(raw).repaired()
        }

        if (fromVersion == CURRENT_SETTINGS_VERSION) {
            return settingsJson.decodeFromString<AppSettings>(raw).repaired()
        }

        backupSource?.let { backupBeforeRewrite(appDataDir, it, fromVersion) }
        var migrated = raw
        for ((toVersion, step) in rawMigrations) {
            if (toVersion > fromVersion) migrated = step(migrated)
        }
        var settings = settingsJson.decodeFromString<AppSettings>(migrated)
        if (fromVersion < VERSION_HIDDEN_TABS) settings = migrateHiddenTabs(settings, raw)
        if (fromVersion < VERSION_CALENDAR_BUTTON) {
            // The Schedule toolbar's Calendar button is new and starts hidden -- see
            // AppSettings.hiddenScheduleButtons. A file written before it existed has an explicit
            // list that cannot mention it, so without this every existing install would open with
            // a button nobody asked for.
            settings = settings.copy(hiddenScheduleButtons = settings.hiddenScheduleButtons + "CALENDAR")
        }
        if (fromVersion < VERSION_QUICK_BACKGROUND_INHERITS) {
            settings = migrateQuickBackgroundLowerThird(settings)
        }
        if (fromVersion < VERSION_PREVIEW_LAYOUTS) settings = migratePreviewGroupsToLayout(settings)
        // The primary/secondary-bible output shorthand ("primary"/"secondary" bibleMode, converted
        // to a position in the stack) used to be migrated here as a typed, per-[ScreenAssignment]
        // step gated on `fromVersion < 6`. An output no longer carries `bibleMode` at all -- that
        // field now lives only on [OutputProfile] -- so the conversion moved into
        // [migrateOutputProfiles] (raw-JSON, version 12) itself, which runs before typed decode and
        // so is the last point anything can still read the field off an assignment's own JSON.
        return settings.copy(settingsVersion = CURRENT_SETTINGS_VERSION).repaired()
    }

    /** Keeps the newest few of each settings-backup family -- see [pruneBackupsOnce]. */
    internal fun pruneBackups() = pruneBackups(appDataDir)

    fun saveSettings(settings: AppSettings) {
        cachedSettings = settings
        try {
            val json = stripProfileOwnedStyling(settingsJson.encodeToString(settings))
            // Write to a temp file first, then atomically swap it into place — a process kill
            // mid-write (e.g. during the self-updater's exit race) leaves the temp file
            // incomplete but never touches the live settings.json.
            settingsTmpFile.writeText(json)
            try {
                Files.move(
                    settingsTmpFile.toPath(), settingsFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Some network and FUSE filesystems cannot promise atomicity; a plain replace is
                // still a rename rather than a truncate-in-place.
                Files.move(settingsTmpFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) {
            // Silently handle error
        }
    }

    companion object {
        /**
         * The band templates folder under [appDataDir], for a caller with no manager at hand —
         * the per-output customise dialog sits several composables away from the one that has it.
         */
        fun bibleLowerThirdsDir(appDataDir: File = AppDataDir.resolve()): File = File(appDataDir, "bible_lower_thirds")
    }
}
