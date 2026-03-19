package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
        return try {
            if (settingsFile.exists()) {
                val raw = settingsFile.readText()
                val migrated = migrateProjectionSettings(raw)
                try {
                    jsonFormat.decodeFromString<AppSettings>(migrated)
                } catch (e: Exception) {
                    AppSettings()
                }
            } else {
                AppSettings() // Return default settings
            }
        } catch (e: Exception) {
            AppSettings() // Return default settings on error
        }
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
        try {
            val json = jsonFormat.encodeToString(settings)
            settingsFile.writeText(json)
        } catch (e: Exception) {
            // Silently handle error
        }
    }
}
