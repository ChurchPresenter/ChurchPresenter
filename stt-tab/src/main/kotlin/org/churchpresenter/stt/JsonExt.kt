package org.churchpresenter.stt

import org.json.JSONArray
import org.json.JSONObject

/**
 * String accessors that treat a JSON `null` as absent.
 *
 * `JSONObject.optString` does **not** return the supplied default for a JSON `null` — it returns the
 * four-character string `"null"`. So the natural-looking
 * `optString(key, "").takeIf { it.isNotEmpty() }` quietly accepts `"null"` as a real value, and
 * `optString(key, "").ifBlank { … }` never reaches its fallback.
 *
 * This is not hypothetical. The STT server emits `"segment_id": null`, which reached the Bible
 * engine's detection log and ChurchPresenter's operator-flag log as `"segmentId":"null"` — a
 * correlation key that then joins every such row to every other. The same coercion on a caption's
 * `text` field would put the literal word "null" on screen in front of a congregation.
 *
 * Use these anywhere a JSON string field is optional.
 */
fun JSONObject.stringOr(key: String, default: String = ""): String =
    if (!has(key) || isNull(key)) default else optString(key, default)

/** Null-safe [stringOr] for array elements; a JSON `null` entry yields [default]. */
fun JSONArray.stringOr(index: Int, default: String = ""): String =
    if (isNull(index)) default else optString(index, default)

/** The value, or null when the key is absent, JSON `null`, or blank. */
fun JSONObject.stringOrNull(key: String): String? =
    stringOr(key).trim().takeIf { it.isNotEmpty() }
