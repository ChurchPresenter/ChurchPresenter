package org.churchpresenter.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Taking out of a settings document what its output profiles own, so a save writes it once.

/**
 * Captions and Q&A down to their install-wide keys ([STT_GLOBAL_KEYS], [QA_GLOBAL_KEYS]); the
 * dictionary card and video subtitles, which have none, dropped whole.
 */
internal fun MutableMap<String, JsonElement>.stripOverlayStyling() {
    keepOnly("sttSettings", STT_GLOBAL_KEYS)
    keepOnly("qaSettings", QA_GLOBAL_KEYS)
    remove("dictionarySettings")
    remove("mediaSettings")
}

/** Songs and the Bible down to their install-wide keys, each translation to its file and names. */
internal fun MutableMap<String, JsonElement>.stripBibleAndSongStyling() {
    keepOnly("songSettings", SONG_GLOBAL_KEYS)
    val bible = this["bibleSettings"] as? JsonObject ?: return
    val kept = bible.filterKeys { it in BIBLE_GLOBAL_KEYS }.toMutableMap()
    (bible[BIBLE_STACK_KEY] as? JsonArray)?.let { stack ->
        kept[BIBLE_STACK_KEY] = JsonArray(stack.map { it.keepingOnly(BIBLE_TRANSLATION_GLOBAL_KEYS) })
    }
    this["bibleSettings"] = JsonObject(kept)
}

private fun JsonElement.keepingOnly(keys: Set<String>): JsonElement =
    (this as? JsonObject)?.let { entry -> JsonObject(entry.filterKeys { it in keys }) } ?: this

private fun MutableMap<String, JsonElement>.keepOnly(key: String, keys: Set<String>) {
    val block = this[key] as? JsonObject ?: return
    this[key] = JsonObject(block.filterKeys { it in keys })
}
