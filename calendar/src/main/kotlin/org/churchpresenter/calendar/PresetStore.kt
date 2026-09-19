package org.churchpresenter.calendar

import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/**
 * `presets.json` — the items saved with **Save preset** from the app's tabs.
 *
 * Beside `calendar.json` in the same folder, and its own file rather than a section of it: the
 * tabs write here without opening the calendar, and the calendar reads here without owning it.
 * Written through [writeTextAtomically] like the calendar; unlike the calendar it keeps no
 * backups — a preset is re-saved from its tab in two clicks, and a parse failure opens as empty.
 */
class PresetStore(private val folder: File) {

    internal val file: File = File(folder, PRESET_FILE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(): PresetDocument {
        if (!file.isFile) return PresetDocument()
        return runCatching { json.decodeFromString(PresetDocument.serializer(), file.readText()) }
            .getOrDefault(PresetDocument())
    }

    fun save(document: PresetDocument) {
        folder.mkdirs()
        file.writeTextAtomically(json.encodeToString(PresetDocument.serializer(), document))
    }

    /** Saves [item] as a preset called [name], replacing one of the same name. Returns what was saved. */
    fun add(name: String, item: ScheduleItem): ItemPreset {
        val preset = ItemPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            item = item,
            savedAt = LocalDateTime.now().withNano(0).toString(),
        )
        save(load().withPreset(preset))
        return preset
    }

    fun remove(id: String) {
        save(load().withoutPreset(id))
    }
}

private const val PRESET_FILE = "presets.json"
