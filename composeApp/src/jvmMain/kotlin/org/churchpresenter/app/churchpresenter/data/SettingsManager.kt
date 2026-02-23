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
                val json = settingsFile.readText()
                try {
                    jsonFormat.decodeFromString<AppSettings>(json)
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

    fun saveSettings(settings: AppSettings) {
        try {
            val json = jsonFormat.encodeToString(settings)
            settingsFile.writeText(json)
        } catch (e: Exception) {
            // Silently handle error
        }
    }
}
