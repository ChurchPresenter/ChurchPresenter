package org.churchpresenter.app.churchpresenter.data

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

class SettingsManager {
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val settingsFile = File(appDataDir, "settings.json")

    private val jsonFormat = Json {
        ignoreUnknownKeys = true // ignore extra fields in JSON
        encodeDefaults = true    // always write defaults when saving
    }

    init {
        // Create app data directory if it doesn't exist
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
    }

    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val json = settingsFile.readText()
                try {
                    jsonFormat.decodeFromString<AppSettings>(json)
                } catch (e: Exception) {
                    println("Error parsing settings: ${e.message}")
                    AppSettings()
                }
            } else {
                AppSettings() // Return default settings
            }
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
            AppSettings() // Return default settings on error
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            val json = jsonFormat.encodeToString(settings)
            settingsFile.writeText(json)
            println("Settings saved to: ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
