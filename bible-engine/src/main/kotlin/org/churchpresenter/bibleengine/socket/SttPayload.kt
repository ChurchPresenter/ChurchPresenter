package org.churchpresenter.bibleengine.socket

import org.json.JSONObject

/**
 * One parsed STT update — everything the detection pipeline consumes from a
 * `transcription_update` / `translation_update` payload.
 *
 * Extraction lives here (not in [SttSocketClient]) so the live socket path and the replay
 * harness (`DbReplayTest`) share ONE text-shaping code path and can never drift: replay feeds
 * the engine through [windowedText] with the exact same last-2-segments+in-progress rule the
 * live extractors apply.
 */
data class SttUpdate(
    val text: String,
    val speechType: String?,
    val segmentId: String?,
    val startTime: Double?,
    val sessionId: String?,
)

/**
 * The shared text-window rule: the last two completed segments plus the in-progress text,
 * joined with single spaces. Null when nothing is present.
 */
fun windowedText(completedSegments: List<String>, inProgress: String?): String? {
    val parts = ArrayList<String>(3)
    completedSegments.takeLast(2).forEach { seg ->
        seg.trim().takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    }
    inProgress?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    return parts.joinToString(" ").trim().takeIf { it.isNotEmpty() }
}

/** Parses a `transcription_update` payload, or null when it carries no transcript text. */
fun transcriptionUpdate(payload: JSONObject): SttUpdate? =
    buildUpdate(payload, textField = "text")

/** Parses a `translation_update` payload, or null when it carries no translated text. */
fun translationUpdate(payload: JSONObject): SttUpdate? =
    buildUpdate(payload, textField = "translated_text")

/**
 * A trimmed string field, or null when absent, blank or JSON `null`.
 *
 * `JSONObject.optString` coerces a JSON `null` into the four-character string `"null"` rather than
 * returning the default, so every `optString(...).takeIf { it.isNotEmpty() }` in this file silently
 * accepted `"null"` as a real value. Observed live: a `segment_id` of null reached the detection log
 * and the operator-flag log as `"segmentId":"null"`, where it becomes a fake correlation key that
 * joins every such row to every other. The same coercion applied to `session_id` (the primary join
 * key across all three artifacts) and to the transcript text itself, where it would have fed the
 * literal word "null" into detection as if it had been spoken.
 */
private fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key, "").trim().takeIf { it.isNotEmpty() }
}

private fun buildUpdate(payload: JSONObject, textField: String): SttUpdate? {
    val completed = ArrayList<String>(2)
    val segments = payload.optJSONArray("segments")
    if (segments != null) {
        val start = maxOf(0, segments.length() - 2)
        for (i in start until segments.length()) {
            segments.optJSONObject(i)?.stringOrNull(textField)?.let { completed.add(it) }
        }
    }
    val inProgress = when (val ip = payload.opt("in_progress")) {
        is String -> ip
        is JSONObject -> ip.stringOrNull(textField)
        else -> null
    }
    val text = windowedText(completed, inProgress) ?: return null
    return SttUpdate(
        text = text,
        speechType = speechTypeOf(payload),
        segmentId = extractSegmentId(payload),
        startTime = extractStartTime(payload),
        sessionId = sessionIdOf(payload),
    )
}

// Best-effort speech_type from the payload (e.g. "Speaking"/"Quiet"/"Music"); null if absent so
// detection behaves unchanged until the STT stream provides it. Drives the music precision gate.
private fun speechTypeOf(payload: JSONObject): String? = payload.stringOrNull("speech_type")

// Stable per-service session id (e.g. the STT db base name "S01"), emitted as a
// top-level `session_id` in every payload. Ties all three artifacts (STT db, engine detection-log,
// CP live-references) with an exact join. Null until the STT app ships the field.
private fun sessionIdOf(payload: JSONObject): String? = payload.stringOrNull("session_id")

// The STT segment id that produced this update — the clock-free correlation key matching the STT
// db's `segment_id` column (TEXT = str(id)). Prefers an explicit string `segment_id`, then falls
// back to the integer `id` (the db primary key) stringified. Probe order: in-progress (newest) →
// latest completed segment → top-level. Null only if nothing is present.
private fun extractSegmentId(payload: JSONObject): String? {
    (payload.opt("in_progress") as? JSONObject)?.let { segmentIdOf(it)?.let { id -> return id } }
    val segments = payload.optJSONArray("segments")
    if (segments != null && segments.length() > 0) {
        segments.optJSONObject(segments.length() - 1)?.let { segmentIdOf(it)?.let { id -> return id } }
    }
    return segmentIdOf(payload)
}

private fun segmentIdOf(obj: JSONObject): String? {
    obj.stringOrNull("segment_id")?.let { return it }
    if (obj.has("id") && !obj.isNull("id")) return obj.optInt("id").toString()
    return null
}

// Session-relative start time of the newest segment (seconds from session start). Reads the
// canonical `start_time`, falling back to `start` (the field today's payload uses). Best-effort.
private fun extractStartTime(payload: JSONObject): Double? {
    (payload.opt("in_progress") as? JSONObject)?.let { startTimeOf(it)?.let { t -> return t } }
    val segments = payload.optJSONArray("segments")
    if (segments != null && segments.length() > 0) {
        segments.optJSONObject(segments.length() - 1)?.let { startTimeOf(it)?.let { t -> return t } }
    }
    return startTimeOf(payload)
}

private fun startTimeOf(obj: JSONObject): Double? = when {
    obj.has("start_time") -> obj.optDouble("start_time")
    obj.has("start") -> obj.optDouble("start")
    else -> null
}
