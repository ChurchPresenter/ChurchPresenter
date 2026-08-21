package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.decodeFromString
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings.Companion.CURRENT_SETTINGS_VERSION
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.AppDataDir
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private const val VERSION_HIDDEN_TABS = 5
private const val VERSION_SCREEN_ASSIGNMENTS = 6

/** The three placement-field prefixes used throughout companionSatelliteConnections[] entries
 * (tabRows, leftSidebarRows, rightSidebarRows, etc.) — shared by the migrations below. */
private val CompanionSurfacePlacementPrefixes = listOf("tab", "leftSidebar", "rightSidebar")

class SettingsManager {
    private val appDataDir = AppDataDir.resolve()
    private val settingsFile = File(appDataDir, "settings.json")
    private val settingsTmpFile = File(appDataDir, "settings.json.tmp")
    val lottiePresetsDir: File = File(appDataDir, "lottie_presets")

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
    )

    fun loadSettings(): AppSettings {
        cachedSettings?.let { return it }
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
        if (fromVersion < VERSION_SCREEN_ASSIGNMENTS) {
            // The primary/secondary bible pair became an ordered list of any length. Typed rather
            // than raw, because the conversion is a field-by-field restructure the data class
            // already knows how to do. The old fields are left in the document on purpose so a
            // downgrade still finds a configured bible.
            //
            // Only the *output* half of that conversion belongs behind this version gate. The stack
            // itself is repaired by `repaired()` on every load, whatever the version says.
            settings = settings.copy(
                projectionSettings = settings.projectionSettings.copy(
                    screenAssignments = settings.projectionSettings.screenAssignments
                        .map(::migrateOutputTranslations),
                    // Browser sources are the same ScreenAssignment shape driven by the same UI, so
                    // they carry the same legacy mode and need the same conversion. Left out, a
                    // stream feed set to one language kept a mode the new code cannot read and
                    // silently started showing every translation stacked.
                    browserSourceOutputs = settings.projectionSettings.browserSourceOutputs
                        .map(::migrateOutputTranslations),
                ),
            )
        }
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
        copy(bibleSettings = bibleSettings.migrateTranslations())

    /**
     * An output used to name which of two bibles it showed. With a stack of any length it names
     * positions instead, so "primary" becomes position 0 and "secondary" position 1. Everything else
     * — "off", and "both" meaning all of them — is already expressed by an empty list plus the
     * unchanged on/off flag.
     */
    private fun migrateOutputTranslations(assignment: ScreenAssignment): ScreenAssignment =
        when (assignment.bibleMode) {
            Constants.SONG_LANG_PRIMARY ->
                assignment.copy(bibleMode = Constants.SONG_LANG_BOTH, bibleTranslations = listOf(0))
            Constants.SONG_LANG_SECONDARY ->
                assignment.copy(bibleMode = Constants.SONG_LANG_BOTH, bibleTranslations = listOf(1))
            else -> assignment
        }

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

    /** Schema version 2. Migrates old screen1-4Assignment fields to screenAssignments list. */
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
            Files.move(settingsTmpFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Silently handle error
        }
    }
}
