package org.churchpresenter.ui

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.json.Json

/** Stores recently used colors across all color picker instances, persisted to disk. */
object RecentColors {
    private const val MAX = 12
    private val file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_colors.json")
    val colors = mutableStateListOf<String>()

    init { load() }

    fun add(hex: String) {
        val upper = hex.uppercase()
        colors.remove(upper)
        colors.add(0, upper)
        while (colors.size > MAX) colors.removeLast()
        save()
    }

    internal fun load() {
        try {
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                colors.clear()
                colors.addAll(list.take(MAX))
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            file.writeText(json.encodeToString(colors.toList()))
        } catch (_: Exception) {}
    }
}
