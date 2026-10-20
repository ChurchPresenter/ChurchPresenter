package org.churchpresenter.settings

import kotlinx.serialization.json.JsonElement

/**
 * The [settingPaths] a profile holding [this] has under `songSettings` -- the same paths and values,
 * without encoding the rest of a profile or keeping the answer in [RecentSettingPaths]. For a page
 * that probes a dozen throwaway copies of one section: each would otherwise flatten a whole profile
 * and push a profile the page actually reads out of that cache.
 */
fun SongSettings.profilePaths(): Map<String, JsonElement> =
    sectionPaths("songSettings", linkJson.encodeToJsonElement(SongSettings.serializer(), this))

/** [SongSettings.profilePaths] for `bibleSettings`. */
fun BibleSettings.profilePaths(): Map<String, JsonElement> =
    sectionPaths("bibleSettings", linkJson.encodeToJsonElement(BibleSettings.serializer(), this))

private fun sectionPaths(key: String, value: JsonElement): Map<String, JsonElement> =
    LinkedHashMap<String, JsonElement>().also { flattenTopLevel(key, value, it) }
