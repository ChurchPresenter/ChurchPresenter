package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** The three placement-field prefixes used throughout companionSatelliteConnections[] entries
 * (tabRows, leftSidebarRows, rightSidebarRows, etc.) — shared by the migrations below. */
private val CompanionSurfacePlacementPrefixes = listOf("tab", "leftSidebar", "rightSidebar")

class SettingsManager {
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
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

    fun loadSettings(): AppSettings {
        cachedSettings?.let { return it }
        return try {
            if (settingsFile.exists()) {
                val raw = settingsFile.readText()
                val migrated = migrateCompanionSatelliteRowColumnRangeBackToCount(
                    migrateCompanionSatelliteStartPage(migrateProjectionSettings(migrateScreenAssignmentModes(raw)))
                )
                try {
                    val settings = jsonFormat.decodeFromString<AppSettings>(migrated)
                    migrateHiddenTabs(settings, raw)
                } catch (e: Exception) {
                    AppSettings()
                }
            } else {
                AppSettings() // Return default settings
            }
        } catch (e: Exception) {
            AppSettings() // Return default settings on error
        }.also { cachedSettings = it }
    }

    /**
     * Ensures new tabs (like QA) are hidden by default for existing users.
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

    /** Converts old showBible:false/showSongs:false booleans to bibleMode:"off"/songMode:"off" strings. */
    private fun migrateScreenAssignmentModes(raw: String): String {
        if (!raw.contains("\"showBible\"") && !raw.contains("\"showSongs\"")) return raw
        val root = try { jsonFormat.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return raw }
        val proj = root["projectionSettings"]?.jsonObject ?: return raw
        val assignments = proj["screenAssignments"]?.jsonArray ?: return raw
        var changed = false
        val newAssignments = buildJsonArray {
            for (element in assignments) {
                val obj = element.jsonObject
                val showBibleFalse = (obj["showBible"] as? JsonPrimitive)?.content == "false"
                val showSongsFalse = (obj["showSongs"] as? JsonPrimitive)?.content == "false"
                if (showBibleFalse || showSongsFalse) {
                    changed = true
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> if (k != "showBible" && k != "showSongs") put(k, v) }
                        if (showBibleFalse && !obj.containsKey("bibleMode")) put("bibleMode", JsonPrimitive("off"))
                        if (showSongsFalse && !obj.containsKey("songMode")) put("songMode", JsonPrimitive("off"))
                    })
                } else { add(element) }
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

    /** Renames the old single companionSatelliteConnections[] fields (rows/columns/bitmapSize) to
     * their tab-prefixed placement-specific equivalents, so existing users' configured values
     * survive the placement-per-connection rework instead of silently resetting via
     * ignoreUnknownKeys. TAB is the migration target for all of these since it was the only
     * placement that existed before. (The old single "startPage" field has no equivalent to rename
     * to — per-placement start page was tried and dropped again; see
     * [migrateCompanionSatelliteRowColumnRangeBackToCount].) */
    private fun migrateCompanionSatelliteStartPage(raw: String): String {
        if (!raw.contains("\"companionSatelliteConnections\"")) return raw
        val root = try { jsonFormat.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return raw }
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

    /** Converts each placement's briefly-introduced start/end row/column RANGE fields back into a
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
        val root = try { jsonFormat.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return raw }
        val connections = root["companionSatelliteConnections"]?.jsonArray ?: return raw
        var changed = false
        val rangeKeys = CompanionSurfacePlacementPrefixes.flatMap { prefix ->
            listOf("${prefix}StartRow", "${prefix}EndRow", "${prefix}StartColumn", "${prefix}EndColumn")
        }.toSet()
        val newConnections = buildJsonArray {
            for (element in connections) {
                val obj = element.jsonObject
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
                if (additions.isNotEmpty()) {
                    changed = true
                    add(buildJsonObject {
                        obj.forEach { (k, v) -> if (k !in rangeKeys) put(k, v) }
                        additions.forEach { (k, v) -> put(k, v) }
                    })
                } else if (rangeKeys.any { it in obj }) {
                    // Stray range keys with no rows/columns to derive (shouldn't normally happen) —
                    // still strip them so they don't linger as dead unknown keys forever.
                    changed = true
                    add(buildJsonObject { obj.forEach { (k, v) -> if (k !in rangeKeys) put(k, v) } })
                } else { add(element) }
            }
        }
        if (!changed) return raw
        val newRoot = buildJsonObject {
            root.forEach { (k, v) -> if (k == "companionSatelliteConnections") put(k, newConnections) else put(k, v) }
        }
        return newRoot.toString()
    }

    /** Migrates old screen1-4Assignment fields to screenAssignments list. */
    private fun migrateProjectionSettings(raw: String): String {
        val root = try { jsonFormat.parseToJsonElement(raw).jsonObject } catch (_: Exception) { return raw }
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
        } catch (e: Exception) {
            // Silently handle error
        }
    }
}
