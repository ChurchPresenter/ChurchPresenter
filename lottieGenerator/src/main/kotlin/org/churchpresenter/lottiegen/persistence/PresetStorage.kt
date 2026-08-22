package org.churchpresenter.lottiegen.persistence

import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.lottiegen.model.Preset
import java.io.File

object PresetStorage {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun presetsFile(): File {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/churchpresenter-lottiegen")
        dir.mkdirs()
        return File(dir, "presets.json")
    }

    fun load(): List<Preset> {
        val file = presetsFile()
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<Preset>>(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(presets: List<Preset>) {
        try {
            presetsFile().writeText(json.encodeToString(presets))
        } catch (e: IOException) {
            System.err.println("Failed to save presets: ${e.message}")
        }
    }
}
