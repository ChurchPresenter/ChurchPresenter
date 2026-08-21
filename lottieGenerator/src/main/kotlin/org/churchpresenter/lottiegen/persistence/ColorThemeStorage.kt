package org.churchpresenter.lottiegen.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.lottiegen.model.ColorTheme
import org.churchpresenter.lottiegen.model.defaultColorThemes
import java.io.File

object ColorThemeStorage {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun themesFile(): File {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/churchpresenter-lottiegen")
        dir.mkdirs()
        return File(dir, "color-themes.json")
    }

    fun load(): List<ColorTheme> {
        val file = themesFile()
        if (!file.exists()) {
            save(defaultColorThemes())
            return defaultColorThemes()
        }
        return try {
            val themes = json.decodeFromString<List<ColorTheme>>(file.readText())
            if (themes.isEmpty()) defaultColorThemes() else themes
        } catch (e: Exception) {
            defaultColorThemes()
        }
    }

    fun save(themes: List<ColorTheme>) {
        try {
            themesFile().writeText(json.encodeToString(themes))
        } catch (e: Exception) {
            System.err.println("Failed to save color themes: ${e.message}")
        }
    }
}
