package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// The one JSON configuration settings are read and written with, and the parse every raw migration
// starts from.

internal val settingsJson = Json {
    ignoreUnknownKeys = true // ignore extra fields in JSON
    encodeDefaults = true    // always write defaults when saving
}

internal fun parseSettingsRoot(raw: String): JsonObject? =
    try { settingsJson.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }
