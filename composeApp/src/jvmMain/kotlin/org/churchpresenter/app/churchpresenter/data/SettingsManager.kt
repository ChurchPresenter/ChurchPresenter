package org.churchpresenter.app.churchpresenter.data

import java.io.File

class SettingsManager {
    private val userHome = System.getProperty("user.home")
    private val appDataDir = File(userHome, ".churchpresenter")
    private val settingsFile = File(appDataDir, "settings.json")

    init {
        // Create app data directory if it doesn't exist
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
    }

    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                // For now, return default settings - we'll implement JSON parsing later
                AppSettings()
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
            // For now, just create the file - we'll implement JSON serialization later
            settingsFile.writeText("Settings saved: ${settings}")
            println("Settings saved to: ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
