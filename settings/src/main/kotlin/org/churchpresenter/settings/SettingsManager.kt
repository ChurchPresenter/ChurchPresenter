package org.churchpresenter.settings

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.decodeFromString
import org.churchpresenter.settings.AppSettings.Companion.CURRENT_SETTINGS_VERSION
import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.settings.utils.Constants
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The key the band height is stored under, in all three of its homes. */
private const val LOWER_THIRD_HEIGHT_KEY = "lowerThirdHeightPercent"

private const val VERSION_HIDDEN_TABS = 5

/** The Schedule toolbar gained a Calendar button that starts hidden. */
private const val VERSION_CALENDAR_BUTTON = 11

/** The three placement-field prefixes used throughout companionSatelliteConnections[] entries
 * (tabRows, leftSidebarRows, rightSidebarRows, etc.) — shared by the migrations below. */
private val CompanionSurfacePlacementPrefixes = listOf("tab", "leftSidebar", "rightSidebar")

/** How many of each settings-backup family survive a prune. */
private const val BACKUPS_KEPT = 3
private const val BACKUP_PREFIX = "settings.json"
private const val CORRUPT_PREFIX = "settings.json.corrupt-"

/** Per process, not per instance: several call sites build a SettingsManager of their own. */
private val backupsPruned = AtomicBoolean(false)

// Load, migrate, save, import, export and the per-version migration steps, all against one file.
@Suppress("TooManyFunctions")
class SettingsManager {
    private val appDataDir = AppDataDir.resolve()
    private val settingsFile = File(appDataDir, "settings.json")
    private val settingsTmpFile = File(appDataDir, "settings.json.tmp")
    val lottiePresetsDir: File = File(appDataDir, "lottie_presets")
    /** Where the lower-third band generator saves its templates, and where the picker starts. */
    val bibleLowerThirdsDir: File = bibleLowerThirdsDir(appDataDir)

    private val jsonFormat = Json {
        ignoreUnknownKeys = true // ignore extra fields in JSON
        encodeDefaults = true    // always write defaults when saving
    }

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
        12 to ::migrateOutputProfiles,
        13 to ::migrateDictionaryToGlobal,
    )

    /**
     * Lifts a profile's own dictionary styling back onto the document, which now owns it alone.
     *
     * The dictionary's look moved out of [OutputProfile] and off the settings dialog entirely: it
     * is edited from the gear on the Dictionary tab now, the way the STT tab has always styled
     * itself, and there is one of it per install rather than one per output. A document written
     * while profiles still carried their own would otherwise lose that styling silently on the next
     * load -- the field is gone from the type, so the decoder simply drops the key.
     *
     * The first profile that differs from the document wins. There is no merging to be done between
     * several: they are alternatives, and the document's own copy is what every output that never
     * customized it was already showing.
     */
    private fun migrateDictionaryToGlobal(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val profiles = root["projectionSettings"]?.jsonObject?.get("outputProfiles")?.jsonArray ?: return raw
        val documentCopy = root["dictionarySettings"]?.jsonObject
        val lifted = profiles
            .mapNotNull { (it as? JsonObject)?.get("dictionarySettings") as? JsonObject }
            .firstOrNull { it != documentCopy }
            ?: return raw
        return JsonObject(root + ("dictionarySettings" to lifted)).toString()
    }

    fun loadSettings(): AppSettings {
        cachedSettings?.let { return it }
        pruneBackupsOnce()
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
                    preserveUnreadableFile()
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
            backupSource?.let { backupBeforeRewrite(it, fromVersion) }
            return jsonFormat.decodeFromString<AppSettings>(raw).repaired()
        }

        if (fromVersion == CURRENT_SETTINGS_VERSION) {
            return jsonFormat.decodeFromString<AppSettings>(raw).repaired()
        }

        backupSource?.let { backupBeforeRewrite(it, fromVersion) }
        var migrated = raw
        for ((toVersion, step) in rawMigrations) {
            if (toVersion > fromVersion) migrated = step(migrated)
        }
        var settings = jsonFormat.decodeFromString<AppSettings>(migrated)
        if (fromVersion < VERSION_HIDDEN_TABS) settings = migrateHiddenTabs(settings, raw)
        if (fromVersion < VERSION_CALENDAR_BUTTON) {
            // The Schedule toolbar's Calendar button is new and starts hidden -- see
            // AppSettings.hiddenScheduleButtons. A file written before it existed has an explicit
            // list that cannot mention it, so without this every existing install would open with
            // a button nobody asked for.
            settings = settings.copy(hiddenScheduleButtons = settings.hiddenScheduleButtons + "CALENDAR")
        }
        // The primary/secondary-bible output shorthand ("primary"/"secondary" bibleMode, converted
        // to a position in the stack) used to be migrated here as a typed, per-[ScreenAssignment]
        // step gated on `fromVersion < 6`. An output no longer carries `bibleMode` at all -- that
        // field now lives only on [OutputProfile] -- so the conversion moved into
        // [migrateOutputProfiles] (raw-JSON, version 12) itself, which runs before typed decode and
        // so is the last point anything can still read the field off an assignment's own JSON.
        return settings.copy(settingsVersion = CURRENT_SETTINGS_VERSION).repaired()
    }

    /**
     * Puts the translation stack back in step with the legacy bible pair it mirrors.
     *
     * An invariant, not a migration, which is why it runs on every load and not behind a version
     * gate. `primaryBible`/`secondaryBible` are only kept so an older build can still read the file;
     * anything that sets one of them without going through [BibleSettings.withTranslations] leaves a
     * current-version document with a configured pair and an empty stack. That document is never
     * migrated — it is already at the current version — so before this it stayed broken for good.
     * Nothing shows it either: [BibleSettings.translationList] falls back to the pair, so the app
     * presents correctly right up until the first stack edit rewrites the pair from a list that
     * never held those bibles, and the operator's translations disappear.
     *
     * Safe on a stack that is empty on purpose: emptying it through `withTranslations` clears the
     * legacy pair too, so there is nothing to put back. Idempotent, by
     * [BibleSettings.migrateTranslations]'s own guard.
     */
    private fun AppSettings.repaired(): AppSettings =
        copy(
            bibleSettings = bibleSettings.migrateTranslations(),
            songSettings = songSettings.migrateSongNumberStyle().migrateElementPositions(),
        )

    /** Reads the document's schema version without decoding it; absent or unparseable means 0
     * (pre-versioning), which runs the full migration chain — the pre-versioning behaviour. */
    private fun readSettingsVersion(raw: String): Int =
        try {
            (jsonFormat.parseToJsonElement(raw).jsonObject["settingsVersion"] as? JsonPrimitive)
                ?.content?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }

    /** Snapshots [source] as `settings.json.v<version>.bak` before this build rewrites it into a
     * different schema. Never overwrites an existing snapshot: the oldest copy for a given version
     * is the one taken before any lossy rewrite, so it is the one worth keeping. */
    private fun backupBeforeRewrite(source: File, version: Int) {
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
    private fun pruneBackupsOnce() {
        if (!backupsPruned.compareAndSet(false, true)) return
        pruneBackups()
    }

    internal fun pruneBackups() {
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
    private fun preserveUnreadableFile() {
        try {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val target = File(appDataDir, "settings.json.corrupt-$stamp")
            if (!target.exists()) Files.copy(settingsFile.toPath(), target.toPath())
        } catch (_: Exception) {
            // Best effort only.
        }
    }

    /**
     * Schema version 5. Ensures new tabs (like QA) are hidden by default for existing users.
     * If the raw JSON has no "qaSettings" key, the user has never interacted with Q&A,
     * so we add "QA" to hiddenTabs if it's not already there.
     */
    private fun migrateHiddenTabs(settings: AppSettings, raw: String): AppSettings {
        var result = settings
        if ("\"qaSettings\"" !in raw && "QA" !in result.hiddenTabs) {
            result = result.copy(hiddenTabs = result.hiddenTabs + "QA")
        }
        if ("\"sttSettings\"" !in raw && "STT" !in result.hiddenTabs) {
            result = result.copy(hiddenTabs = result.hiddenTabs + "STT")
        }
        return result
    }

    /** One screen assignment with showBible/showSongs turned into modes, or null if untouched. */
    private fun assignmentWithModes(obj: JsonObject): JsonObject? {
        val showBibleFalse = (obj["showBible"] as? JsonPrimitive)?.content == "false"
        val showSongsFalse = (obj["showSongs"] as? JsonPrimitive)?.content == "false"
        if (!showBibleFalse && !showSongsFalse) return null
        return buildJsonObject {
            obj.forEach { (k, v) -> if (k != "showBible" && k != "showSongs") put(k, v) }
            if (showBibleFalse && !obj.containsKey("bibleMode")) put("bibleMode", JsonPrimitive("off"))
            if (showSongsFalse && !obj.containsKey("songMode")) put("songMode", JsonPrimitive("off"))
        }
    }

    /** One satellite connection with row/column ranges turned back into counts, or null if untouched. */
    private fun connectionWithCounts(obj: JsonObject, rangeKeys: Set<String>): JsonObject? {
        val additions = buildJsonObject {
            for (prefix in CompanionSurfacePlacementPrefixes) {
                val startRow = (obj["${prefix}StartRow"] as? JsonPrimitive)?.content?.toIntOrNull()
                val endRow = (obj["${prefix}EndRow"] as? JsonPrimitive)?.content?.toIntOrNull()
                val startColumn = (obj["${prefix}StartColumn"] as? JsonPrimitive)?.content?.toIntOrNull()
                val endColumn = (obj["${prefix}EndColumn"] as? JsonPrimitive)?.content?.toIntOrNull()
                if (startRow != null && endRow != null && !obj.containsKey("${prefix}Rows")) {
                    put("${prefix}Rows", JsonPrimitive((endRow - startRow + 1).coerceAtLeast(1)))
                }
                if (startColumn != null && endColumn != null && !obj.containsKey("${prefix}Columns")) {
                    put("${prefix}Columns", JsonPrimitive((endColumn - startColumn + 1).coerceAtLeast(1)))
                }
            }
        }
        // Stray range keys with no rows/columns to derive (shouldn't normally happen) are still
        // stripped, so they don't linger as dead unknown keys forever.
        if (additions.isEmpty() && rangeKeys.none { it in obj }) return null
        return buildJsonObject {
            obj.forEach { (k, v) -> if (k !in rangeKeys) put(k, v) }
            additions.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun parseSettingsRoot(raw: String): JsonObject? =
        try { jsonFormat.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }

    /** Schema version 1. Converts old showBible:false/showSongs:false booleans to
     * bibleMode:"off"/songMode:"off" strings. */
    private fun migrateScreenAssignmentModes(raw: String): String {
        if (!raw.contains("\"showBible\"") && !raw.contains("\"showSongs\"")) return raw
        val root = parseSettingsRoot(raw) ?: return raw
        val proj = root["projectionSettings"]?.jsonObject ?: return raw
        val assignments = proj["screenAssignments"]?.jsonArray ?: return raw
        var changed = false
        val newAssignments = buildJsonArray {
            for (element in assignments) {
                val migrated = assignmentWithModes(element.jsonObject)
                if (migrated != null) changed = true
                add(migrated ?: element)
            }
        }
        if (!changed) return raw
        val newProj = buildJsonObject {
            proj.forEach { (k, v) -> if (k == "screenAssignments") put(k, newAssignments) else put(k, v) }
        }
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Schema version 3. Renames the old single companionSatelliteConnections[] fields (rows/columns/bitmapSize) to
     * their tab-prefixed placement-specific equivalents, so existing users' configured values
     * survive the placement-per-connection rework instead of silently resetting via
     * ignoreUnknownKeys. TAB is the migration target for all of these since it was the only
     * placement that existed before. (The old single "startPage" field has no equivalent to rename
     * to — per-placement start page was tried and dropped again; see
     * [migrateCompanionSatelliteRowColumnRangeBackToCount].) */
    private fun migrateCompanionSatelliteStartPage(raw: String): String {
        if (!raw.contains("\"companionSatelliteConnections\"")) return raw
        val root = parseSettingsRoot(raw) ?: return raw
        val connections = root["companionSatelliteConnections"]?.jsonArray ?: return raw
        val renames = mapOf("rows" to "tabRows", "columns" to "tabColumns", "bitmapSize" to "tabBitmapSize")
        var changed = false
        val newConnections = buildJsonArray {
            for (element in connections) {
                val obj = element.jsonObject
                val toRename = renames.filterKeys { obj.containsKey(it) && !obj.containsKey(renames.getValue(it)) }
                if (toRename.isNotEmpty()) {
                    changed = true
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> if (k !in toRename) put(k, v) }
                        toRename.forEach { (oldKey, newKey) -> put(newKey, obj.getValue(oldKey)) }
                    })
                } else { add(element) }
            }
        }
        if (!changed) return raw
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "companionSatelliteConnections") put(k, newConnections) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Schema version 4. Converts each placement's briefly-introduced start/end row/column RANGE
     * fields back into a
     * plain rows/columns COUNT, so anyone who saved settings while that experiment was live doesn't
     * lose their configured grid size via ignoreUnknownKeys. That start/end scheme (backed by
     * LAYOUT_MANIFEST registration, letting a placement show an arbitrary sub-rectangle of a larger
     * page) worked when probed directly against the protocol, but wasn't respected reliably in
     * practice — Companion already exposes equivalent per-surface start-page/offset configuration
     * of its own (Settings → Surfaces → device), so ChurchPresenter dropped its own version rather
     * than keep two conflicting sources of truth. A startRow=0/endRow=N-1 range becomes plain
     * rows=N — identical count to what was already configured, just without the (unreliable) offset. */
    private fun migrateCompanionSatelliteRowColumnRangeBackToCount(raw: String): String {
        if (!raw.contains("\"companionSatelliteConnections\"")) return raw
        val root = parseSettingsRoot(raw) ?: return raw
        val connections = root["companionSatelliteConnections"]?.jsonArray ?: return raw
        var changed = false
        val rangeKeys = CompanionSurfacePlacementPrefixes.flatMap { prefix ->
            listOf("${prefix}StartRow", "${prefix}EndRow", "${prefix}StartColumn", "${prefix}EndColumn")
        }.toSet()
        val newConnections = buildJsonArray {
            for (element in connections) {
                val migrated = connectionWithCounts(element.jsonObject, rangeKeys)
                if (migrated != null) changed = true
                add(migrated ?: element)
            }
        }
        if (!changed) return raw
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "companionSatelliteConnections") put(k, newConnections) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Schema version 7. Moves the global `stageMonitorSettings.showChords` switch onto every
     * output, where it now lives. Only an operator who had switched it off has anything to carry —
     * the per-output field defaults to on, as the old global did. The old key is left in place so a
     * downgrade still finds its switch. */
    private fun migrateStageMonitorChords(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val showChords = root["stageMonitorSettings"]?.jsonObject
            ?.get("showChords")
            ?.let { (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() }
        val proj = if (showChords == false) root["projectionSettings"]?.jsonObject else null
        if (proj == null) return raw

        fun withChordsOff(key: String): JsonArray? {
            val outputs = proj[key]?.jsonArray ?: return null
            return buildJsonArray {
                for (element in outputs) {
                    val obj = element.jsonObject
                    if ("showChords" in obj) add(element) else add(buildJsonObject {
                        obj.forEach { (k, v) -> put(k, v) }
                        put("showChords", JsonPrimitive(false))
                    })
                }
            }
        }
        val newAssignments = withChordsOff("screenAssignments")
        val newBrowserSources = withChordsOff("browserSourceOutputs")
        if (newAssignments == null && newBrowserSources == null) return raw

        val newProj = buildJsonObject {
            proj.forEach { (k, v) ->
                when (k) {
                    "screenAssignments" -> put(k, newAssignments ?: v)
                    "browserSourceOutputs" -> put(k, newBrowserSources ?: v)
                    else -> put(k, v)
                }
            }
        }
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Schema version 7. Renames the stage monitor's fixed zone names to the layout slots that
     * replaced them, so a saved screen keeps the arrangement it had. The five positions become the
     * five slots of the CLASSIC layout in the order it draws them, which is the layout every
     * existing document opens with. */
    private fun migrateStageMonitorZoneNames(raw: String): String {
        val stageMonitor = parseSettingsRoot(raw)?.get("stageMonitorSettings")?.jsonObject ?: return raw
        val root = parseSettingsRoot(raw) ?: return raw
        val slots = mapOf(
            "TOP_LEFT" to "A", "TOP_RIGHT" to "B",
            "BOTTOM_LEFT" to "C", "BOTTOM_MIDDLE" to "D", "BOTTOM_RIGHT" to "E",
        )
        val zones = stageMonitor["contentZones"]?.jsonObject
        val styles = stageMonitor["zoneStyles"]?.jsonObject
        if (zones == null && styles == null) return raw

        val newStageMonitor = buildJsonObject {
            stageMonitor.forEach { (k, v) ->
                when (k) {
                    // Values name the zone: "BIBLE": "TOP_LEFT".
                    "contentZones" -> put(k, buildJsonObject {
                        zones?.forEach { (type, zone) ->
                            val named = (zone as? JsonPrimitive)?.content
                            put(type, JsonPrimitive(slots[named] ?: named ?: ""))
                        }
                    })
                    // Keys name the zone: "TOP_LEFT": { ...style... }.
                    "zoneStyles" -> put(k, buildJsonObject {
                        styles?.forEach { (zone, style) -> put(slots[zone] ?: zone, style) }
                    })
                    else -> put(k, v)
                }
            }
        }
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "stageMonitorSettings") put(k, newStageMonitor) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Schema version 2. Migrates old screen1-4Assignment fields to screenAssignments list. */
    /**
     * The lower-third band height moved off [ProjectionSettings] and onto the two content types that
     * actually draw a band -- [BibleSettings] and [SongSettings] -- so a church can give scripture a
     * shallow band and lyrics a deeper one.
     *
     * A migration and not just a default, because the field was *removed*: `ignoreUnknownKeys` would
     * decode an existing document without complaint and drop the operator's number on the floor, and
     * the next save would write the file back without it. Someone who had set 45 would find their
     * band at 33 with nothing anywhere to explain it.
     *
     * Copies into both, and into neither where one already has a value -- a document written by a
     * newer build, opened by an older one and rolled forward again must not have its two values
     * flattened back into the one they replaced. The old key is left where it is: unknown on read,
     * and gone the first time the file is saved.
     */
    private fun migrateLowerThirdHeight(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val height = root["projectionSettings"]?.jsonObject
            ?.get(LOWER_THIRD_HEIGHT_KEY)?.jsonPrimitive?.intOrNull ?: return raw

        fun carried(name: String): JsonObject {
            val section = root[name]?.jsonObject ?: buildJsonObject { }
            if (LOWER_THIRD_HEIGHT_KEY in section) return section
            return buildJsonObject {
                section.forEach { (k, v) -> put(k, v) }
                put(LOWER_THIRD_HEIGHT_KEY, JsonPrimitive(height))
            }
        }

        val carriers = setOf("bibleSettings", "songSettings")
        return buildJsonObject {
            root.forEach { (k, v) -> if (k !in carriers) put(k, v) }
            carriers.forEach { put(it, carried(it)) }
        }.toString()
    }

    /**
     * The song number gained a corner it can be pinned to, defaulting to bottom right -- which is
     * where `songNumberPosition` and `songNumberHorizontalAlignment` already put it out of the box.
     *
     * A migration and not just a default, because a corner overrides those two fields. A fresh
     * install sees no difference either way, but an operator who had moved the number -- above the
     * verse, or left of it -- would have found it snapped to the bottom right corner on first
     * launch, with the controls they set it with still reading what they had chosen. So an existing
     * document is pinned to [Constants.NONE] and keeps drawing the number exactly where it was; the
     * corner is offered to them in settings rather than applied to them.
     */
    /**
     * Turns each output's override from a whole settings snapshot into the difference it meant.
     *
     * An override used to be a complete copy of the settings, taken when the screen was first
     * customized. Every field the operator never touched sat in it at whatever value it had that
     * day, and beat the document for ever after -- so a setting *added* later arrived at its class
     * default on that screen, and the global one silently did nothing there. That is not a
     * hypothetical: a second language set to blue for the whole install came out white on the one
     * screen that had been customized, and nothing in the interface explained why.
     *
     * Diffing the snapshot against the document recovers what the operator actually chose: a field
     * that matches the document was never a decision, and drops out. What is left is the screen's
     * own, and everything else follows the document again -- including everything added from here
     * on. The keys named in [SONG_GLOBAL_KEYS] and [BIBLE_GLOBAL_KEYS] drop out regardless: a
     * snapshot may carry a library folder the operator has since moved, and no screen should hold
     * one at all.
     */
    private fun migrateSparseOutputOverrides(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val projection = root["projectionSettings"]?.jsonObject ?: return raw
        val assignments = projection["screenAssignments"]?.jsonArray ?: return raw

        fun globalTree(name: String): JsonObject? = root[name]?.jsonObject

        // Each override key, the document section it is a difference from, and what it never keeps.
        val categories = listOf(
            Triple("songOverride", "songSettings", SONG_GLOBAL_KEYS),
            Triple("bibleOverride", "bibleSettings", BIBLE_GLOBAL_KEYS),
            Triple("dictionaryOverride", "dictionarySettings", emptySet()),
            Triple("backgroundOverride", "backgroundSettings", emptySet()),
            Triple("stageMonitorOverride", "stageMonitorSettings", emptySet()),
        )

        fun slimmed(assignment: JsonObject): JsonObject = buildJsonObject {
            assignment.forEach { (key, value) ->
                val category = categories.firstOrNull { it.first == key }
                val snapshot = value as? JsonObject
                if (category == null || snapshot == null) {
                    put(key, value)
                    return@forEach
                }
                val global = globalTree(category.second)
                if (global == null) {
                    put(key, value)
                    return@forEach
                }
                val atomic = if (key == "bibleOverride") setOf(BIBLE_STACK_KEY) else emptySet()
                val diff = diffObjects(global, snapshot, atomic)
                put(key, JsonObject(diff.filterKeys { it !in category.third }))
            }
        }

        return buildJsonObject {
            root.forEach { (key, value) -> if (key != "projectionSettings") put(key, value) }
            put(
                "projectionSettings",
                buildJsonObject {
                    projection.forEach { (key, value) ->
                        if (key != "screenAssignments") put(key, value)
                    }
                    put("screenAssignments", JsonArray(assignments.map { slimmed(it.jsonObject) }))
                },
            )
        }.toString()
    }

    /**
     * Schema version 12. An output no longer carries its own display mode, content selection or
     * style overrides at all -- it only ever *assigns* an [OutputProfile]. This creates one profile
     * per existing output (screen, Browser Source, NDI), seeded from exactly what that output drew
     * before this version -- its own former `displayMode`/`bibleMode`/`songMode`/`show*` fields, and
     * its former sparse override (already normalized to the post-[migrateSparseOutputOverrides]
     * shape) merged onto the global document -- and points that output at its new profile. Nothing
     * any existing output shows changes across this migration; consolidating near-identical profiles
     * afterward is left to the operator, not attempted here, because collapsing two outputs into one
     * profile is only safe when every one of their fields agrees, and getting that wrong would be a
     * silent visual change on upgrade.
     *
     * A brand-new install never runs this -- it has no `settings.json` to migrate -- and instead
     * gets its one factory profile from [ProjectionSettings]'s own default.
     */
    /** Each override key and the global section it is a difference from. */
    private val outputProfileOverrideCategories = listOf(
        "songOverride" to "songSettings",
        "bibleOverride" to "bibleSettings",
        "backgroundOverride" to "backgroundSettings",
        "stageMonitorOverride" to "stageMonitorSettings",
        // No `dictionaryOverride`: a profile has no dictionary styling to carry any more, so an old
        // per-output dictionary customization has nowhere to land. The document's own copy is what
        // every output that never customized it already showed, and is now what all of them show.
    )

    /** What used to live directly on an assignment and now lives on its profile instead. */
    private val outputProfileOwnKeys = setOf(
        "displayMode", "bibleMode", "bibleTranslations", "songMode", "songTranslations",
        "showPictures", "showMedia", "showSubtitles", "showStreaming", "showAnnouncements",
        "showWebsite", "songLookAhead", "showChords", "showQA", "showSTT", "showDictionary",
        "showCanvas", "showFullscreenBackground", "showLowerThirdBackground",
        "showBibleBackground", "showSongsBackground",
    )

    /**
     * A document old enough to still carry the pre-stack "primary"/"secondary" `bibleMode`
     * shorthand is normalized here rather than relying on the old (now-removed) typed step that
     * used to run after decode: by then an assignment's `bibleMode` field is gone from the type
     * entirely, so this raw step is the last point anything can still read it. See
     * [Constants.SONG_LANG_PRIMARY]/[Constants.SONG_LANG_SECONDARY].
     */
    private fun legacyBibleModeFields(assignment: JsonObject): Map<String, JsonElement> =
        when ((assignment["bibleMode"] as? JsonPrimitive)?.contentOrNull) {
            Constants.SONG_LANG_PRIMARY -> mapOf(
                "bibleMode" to JsonPrimitive(Constants.SONG_LANG_BOTH),
                "bibleTranslations" to JsonArray(listOf(JsonPrimitive(0))),
            )
            Constants.SONG_LANG_SECONDARY -> mapOf(
                "bibleMode" to JsonPrimitive(Constants.SONG_LANG_BOTH),
                "bibleTranslations" to JsonArray(listOf(JsonPrimitive(1))),
            )
            else -> emptyMap()
        }

    /** [assignment]'s resolved styling and behavior, as a new profile named [label]. */
    private fun migratedOutputProfile(assignment: JsonObject, id: String, label: String, root: JsonObject): JsonObject {
        val normalizedBibleFields = legacyBibleModeFields(assignment)
        fun globalTree(name: String): JsonObject = root[name]?.jsonObject ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive("Migrated — $label"))
            outputProfileOwnKeys.forEach { key ->
                (normalizedBibleFields[key] ?: assignment[key])?.let { put(key, it) }
            }
            outputProfileOverrideCategories.forEach { (overrideKey, globalKey) ->
                val override = assignment[overrideKey] as? JsonObject
                val global = globalTree(globalKey)
                val resolved = if (override != null && override.isNotEmpty()) mergeObjects(global, override) else global
                put(globalKey, resolved)
            }
        }
    }

    /** [assignment] with its behavioral fields dropped and [profileId] assigned in their place. */
    private fun slimmedToProfileReference(assignment: JsonObject, profileId: String): JsonObject = buildJsonObject {
        assignment.forEach { (key, value) ->
            if (key !in outputProfileOwnKeys && outputProfileOverrideCategories.none { it.first == key }) {
                put(key, value)
            }
        }
        put("activeProfileId", JsonPrimitive(profileId))
    }

    /**
     * Schema version 12. An output no longer carries its own display mode, content selection or
     * style overrides at all -- it only ever *assigns* an [OutputProfile]. This creates one profile
     * per existing output (screen, Browser Source, NDI), seeded from exactly what that output drew
     * before this version -- its own former `displayMode`/`bibleMode`/`songMode`/`show*` fields, and
     * its former sparse override (already normalized to the post-[migrateSparseOutputOverrides]
     * shape) merged onto the global document -- and points that output at its new profile. Nothing
     * any existing output shows changes across this migration; consolidating near-identical profiles
     * afterward is left to the operator, not attempted here, because collapsing two outputs into one
     * profile is only safe when every one of their fields agrees, and getting that wrong would be a
     * silent visual change on upgrade.
     *
     * A brand-new install never runs this -- it has no `settings.json` to migrate -- and instead
     * gets its one factory profile from [ProjectionSettings]'s own default.
     */
    private fun migrateOutputProfiles(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val projection = root["projectionSettings"]?.jsonObject ?: return raw
        if ("outputProfiles" in projection) return raw

        var profileCounter = 0
        val newProfiles = mutableListOf<JsonObject>()

        fun migrateList(assignments: JsonArray, kindLabel: String, nameKey: String): JsonArray {
            val migrated = assignments.mapIndexed { index, element ->
                val assignment = element.jsonObject
                profileCounter++
                val profileId = "profile$profileCounter"
                val ownName = (assignment[nameKey] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val label = ownName ?: "$kindLabel ${index + 1}"

                newProfiles.add(migratedOutputProfile(assignment, profileId, label, root))
                slimmedToProfileReference(assignment, profileId)
            }
            return JsonArray(migrated)
        }

        val migratedScreens = migrateList(
            (projection["screenAssignments"] as? JsonArray) ?: JsonArray(emptyList()), "Screen", "screenName",
        )
        val migratedBrowser = migrateList(
            (projection["browserSourceOutputs"] as? JsonArray) ?: JsonArray(emptyList()),
            "Browser Source",
            "browserSourceName",
        )
        val migratedNdi = migrateList(
            (projection["ndiOutputs"] as? JsonArray) ?: JsonArray(emptyList()), "NDI Output", "ndiName",
        )

        return buildJsonObject {
            root.forEach { (key, value) -> if (key != "projectionSettings") put(key, value) }
            put(
                "projectionSettings",
                buildJsonObject {
                    projection.forEach { (key, value) ->
                        when (key) {
                            "screenAssignments" -> put(key, migratedScreens)
                            "browserSourceOutputs" -> put(key, migratedBrowser)
                            "ndiOutputs" -> put(key, migratedNdi)
                            else -> put(key, value)
                        }
                    }
                    if ("screenAssignments" !in projection) put("screenAssignments", migratedScreens)
                    if ("browserSourceOutputs" !in projection) put("browserSourceOutputs", migratedBrowser)
                    if ("ndiOutputs" !in projection) put("ndiOutputs", migratedNdi)
                    put("outputProfiles", JsonArray(newProfiles))
                },
            )
        }.toString()
    }

    private fun migrateSongNumberCorner(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val song = root["songSettings"]?.jsonObject ?: return raw
        val keys = listOf("songNumberCorner", "songNumberLowerThirdCorner")
        // Only where the document does not already carry one: a file written by a newer build,
        // opened by an older one and rolled forward again must keep the corner it chose.
        if (keys.all { it in song }) return raw
        val newSong = buildJsonObject {
            song.forEach { (k, v) -> put(k, v) }
            keys.forEach { if (it !in song) put(it, JsonPrimitive(Constants.NONE)) }
        }
        return buildJsonObject {
            root.forEach { (k, v) -> if (k == "songSettings") put(k, newSong) else put(k, v) }
        }.toString()
    }

    private fun migrateProjectionSettings(raw: String): String {
        val root = parseSettingsRoot(raw) ?: return raw
        val proj = root["projectionSettings"]?.jsonObject ?: return raw
        if ("screenAssignments" in proj) return raw // already new format

        val oldKeys = setOf("screen1Assignment", "screen2Assignment",
            "screen3Assignment", "screen4Assignment", "numberOfWindows")
        val assignments = buildJsonArray {
            for (key in listOf("screen1Assignment", "screen2Assignment",
                               "screen3Assignment", "screen4Assignment")) {
                val value = proj[key]
                if (value != null) add(value)
            }
        }
        val newProj = buildJsonObject {
            proj.forEach { (k, v) -> if (k !in oldKeys) put(k, v) }
            put("screenAssignments", assignments)
        }
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "projectionSettings") put(k, newProj) else put(k, v) }
        }
        return newRoot.toString()
    }

    fun saveSettings(settings: AppSettings) {
        cachedSettings = settings
        try {
            val json = jsonFormat.encodeToString(settings)
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
