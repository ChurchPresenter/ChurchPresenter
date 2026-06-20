package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlinx.serialization.decodeFromString
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class SettingsManager {
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val settingsFile = File(appDataDir, "settings.json")
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
                val migrated = migrateProjectionSettings(migrateScreenAssignmentModes(raw))
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
        if ("DICTIONARY" !in result.hiddenTabs) {
            result = result.copy(hiddenTabs = result.hiddenTabs + "DICTIONARY")
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
            settingsFile.writeText(json)
        } catch (e: Exception) {
            // Silently handle error
        }
    }
}
