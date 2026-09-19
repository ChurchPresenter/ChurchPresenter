package org.churchpresenter.calendar

import kotlinx.serialization.json.Json
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.normalizedStamp
import org.churchpresenter.calendar.model.storedInstant
import org.churchpresenter.calendar.model.withPathsRelativeTo
import org.churchpresenter.calendar.model.withPathsResolvedFrom
import org.churchpresenter.core.models.io.writeTextAtomically
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * `presets.json` — the items saved with **Save preset** from the app's tabs.
 *
 * Beside `calendar.json` in the same folder, and its own file rather than a section of it: the
 * tabs write here without opening the calendar, and the calendar reads here without owning it.
 * Written through [writeTextAtomically] like the calendar; unlike the calendar it keeps no
 * backups — a preset is re-saved from its tab in two clicks, and a parse failure opens as empty.
 *
 * Shared between two machines the same way the calendar is: every preset carries the instant it
 * was saved, a removal leaves a tombstone, and [PresetDocument.mergedWith] decides each one on its
 * own. [now] is where the stamps come from; a test pins it.
 *
 * [onSaved] is called after every write, so a watcher can tell this process's own writes from
 * another machine's — see [CalendarFileWatcher.savedPresetsHere].
 */
class PresetStore(
    private val folder: File,
    private val now: () -> Instant = { Instant.now() },
    private val onSaved: () -> Unit = {},
) {

    internal val file: File = File(folder, PRESET_FILE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Reads the file, with every stamp brought to the instant form so they compare — see [normalizedStamp]. */
    fun load(): PresetDocument {
        if (!file.isFile) return PresetDocument()
        val document = runCatching { json.decodeFromString(PresetDocument.serializer(), file.readText()) }
            .getOrDefault(PresetDocument())
        return document
            .copy(presets = document.presets.map { it.copy(savedAt = normalizedStamp(it.savedAt)) })
            .withPathsResolvedFrom(folder)
    }

    fun save(document: PresetDocument) {
        folder.mkdirs()
        val stored = document.withPathsRelativeTo(folder)
        file.writeTextAtomically(json.encodeToString(PresetDocument.serializer(), stored))
        onSaved()
    }

    /** Saves [item] as a preset called [name], replacing one of the same name. Returns what was saved. */
    fun add(name: String, item: ScheduleItem): ItemPreset {
        val preset = ItemPreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            item = item,
            savedAt = storedInstant(now()),
        )
        save(load().withPreset(preset))
        return preset
    }

    fun remove(id: String) {
        save(load().withoutPreset(id, now()))
    }

    /**
     * Folds [known] -- what this process last held -- into what is on disk now, and writes the
     * result back only when the merge holds something the file did not. The same rule as
     * `CalendarState.reloadMerging`: an identical document is not worth a write, and two machines
     * writing the same merge at each other is a loop. Returns the merged document.
     */
    fun reloadMerging(known: PresetDocument): PresetDocument {
        val onDisk = load()
        if (onDisk == known) return onDisk
        val merged = known.mergedWith(onDisk, now())
        if (merged != onDisk) save(merged)
        return merged
    }
}

internal const val PRESET_FILE = "presets.json"
