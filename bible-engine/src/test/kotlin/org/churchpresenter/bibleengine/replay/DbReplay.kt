package org.churchpresenter.bibleengine.replay

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.SpbLoader
import org.churchpresenter.bibleengine.detection.BookResolver
import org.churchpresenter.bibleengine.engine.DetectionEngine
import org.churchpresenter.bibleengine.engine.DetectionLogger
import org.churchpresenter.bibleengine.engine.ScriptureEvent
import org.churchpresenter.bibleengine.socket.windowedText
import java.sql.DriverManager
import kotlin.math.roundToInt

/**
 * Replays a recorded STT service database through the real detection pipeline, producing one
 * privacy-safe JSONL line per emitted event (refs only, never transcript text — safe to commit
 * as a golden file).
 *
 * Determinism: the engine is constructed with a clock that returns the current row's `ts_ms`,
 * so every time-dependent gate (sticky TTL, dedup TTL, continuation timeout, re-emit cooldown)
 * is a pure function of the database. Two replays of the same db yield byte-identical output.
 *
 * Text shaping mirrors the live socket path exactly: both tracks are windowed with
 * [org.churchpresenter.bibleengine.socket.windowedText] (last two finalized rows + in-progress), the same function
 * `SttSocketClient` uses via SttPayload.kt.
 *
 * NOTE on fidelity: the db stores finalized rows (plus optionally in-progress snapshots via
 * `is_final = 0`); the live session also saw transient partials that were never persisted, so a
 * replay is not expected to reproduce the live detection-log exactly. Golden comparisons are
 * always replay-vs-replay.
 */
object DbReplay {

    data class Row(
        val id: Long,
        val tsMs: Long,
        val text: String?,
        val translated: String?,
        val speechType: String?,
        val segmentId: String?,
        val sessionId: String?,
        val startTime: Double?,
        val isFinal: Boolean,
        val denied: Boolean,
        /** Arrival time of the row's translation when the db records it separately
         *  (`translation_ts_ms`, nullable, newer STT servers) — live, the translation is a
         *  later event than the transcript, and replay honors that when the data exists. */
        val translationTsMs: Long? = null,
    )

    data class ReplayResult(
        val lines: List<String>,
        val events: List<ScriptureEvent>,
        /** ts_ms of the row that produced each event (parallel to [events]). */
        val eventTsMs: List<Long>,
    )

    /** Loads translations + registers manifests the same way EngineServer.start does. */
    fun loadTranslations(bibleRoot: String, bibleFiles: List<String>): List<EngineTranslation> {
        Config.bibleRoot = bibleRoot
        BookResolver.register(SpbLoader.scanAllBookManifests())
        return if (bibleFiles.isEmpty()) SpbLoader.loadDefaults() else SpbLoader.loadSelected(bibleFiles)
    }

    // Reading a session database whose schema changed over time: the nesting is one column-presence
    // check inside the row loop, and flattening it would mean querying the schema twice.
    @Suppress("NestedBlockDepth")
    fun readRows(dbPath: String, includeDenied: Boolean = false): List<Row> {
        val rows = ArrayList<Row>(1024)
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            // Optional column on newer STT servers — select it only when the schema has it, so
            // older dbs keep loading (and replaying byte-identically).
            val hasTranslationTs = conn.createStatement().use { st ->
                val info = st.executeQuery("PRAGMA table_info(transcriptions)")
                var found = false
                while (info.next()) if (info.getString("name") == "translation_ts_ms") found = true
                found
            }
            conn.createStatement().use { st ->
                val translationTsCol = if (hasTranslationTs) ", translation_ts_ms" else ""
                val rs = st.executeQuery(
                    "SELECT id, ts_ms, text, translated_text, speech_type, segment_id, session_id, " +
                        "start_time, is_final, denied$translationTsCol FROM transcriptions ORDER BY ts_ms, id"
                )
                var lastTs = 0L
                while (rs.next()) {
                    val denied = rs.getInt("denied") == 1
                    if (denied && !includeDenied) continue
                    var ts = rs.getLong("ts_ms")
                    // Old rows may predate the ts_ms column — keep time monotonic so TTL gates
                    // still behave sensibly.
                    if (rs.wasNull() || ts <= 0L) ts = lastTs + 1_000
                    lastTs = ts
                    rows.add(
                        Row(
                            id = rs.getLong("id"),
                            tsMs = ts,
                            text = rs.getString("text")?.trim()?.takeIf { it.isNotEmpty() },
                            translated = rs.getString("translated_text")?.trim()?.takeIf { it.isNotEmpty() },
                            speechType = rs.getString("speech_type")?.trim()?.takeIf { it.isNotEmpty() },
                            segmentId = rs.getString("segment_id")?.trim()?.takeIf { it.isNotEmpty() }
                                ?: rs.getLong("id").toString(),
                            sessionId = rs.getString("session_id")?.trim()?.takeIf { it.isNotEmpty() },
                            startTime = rs.getDouble("start_time").takeIf { !rs.wasNull() },
                            isFinal = rs.getInt("is_final") != 0,
                            denied = denied,
                            translationTsMs = if (hasTranslationTs) {
                                rs.getLong("translation_ts_ms").takeIf { !rs.wasNull() && it > 0L }
                            } else null,
                        )
                    )
                }
            }
        }
        return rows
    }

    // The replay loop is a state machine over one ordered feed -- transcript and translation events
    // interleaved -- and every branch is a case of that order. Splitting it would spread one
    // sequence across several functions without removing a single decision.
    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    fun replay(rows: List<Row>, translations: List<EngineTranslation>, level: String): ReplayResult {
        Config.applyLevel(level)
        val previousLogPath = DetectionLogger.path
        DetectionLogger.path = null // logging disabled during replay
        try {
            var clockNow = rows.firstOrNull()?.tsMs ?: 0L
            val engine = DetectionEngine(translations, clock = { clockNow })

            val txWindow = ArrayDeque<String>()
            val trWindow = ArrayDeque<String>()
            val lines = ArrayList<String>()
            val events = ArrayList<ScriptureEvent>()
            val eventTs = ArrayList<Long>()

            fun push(window: ArrayDeque<String>, text: String) {
                window.addLast(text)
                while (window.size > 2) window.removeFirst()
            }

            // Feed events in true arrival order: the transcript at the row's ts_ms, the
            // translation at its own translation_ts_ms when the db records one (live, it IS a
            // later event). Without the column both land at the row ts with transcript first —
            // exactly the old per-row order, so pre-column dbs replay byte-identically.
            data class FeedEvent(val row: Row, val isTranslation: Boolean, val tsMs: Long)
            val feed = ArrayList<FeedEvent>(rows.size * 2)
            for (row in rows) {
                if (row.text != null) feed.add(FeedEvent(row, false, row.tsMs))
                if (row.translated != null) {
                    feed.add(FeedEvent(row, true, row.translationTsMs ?: row.tsMs))
                }
            }
            feed.sortWith(compareBy({ it.tsMs }, { it.row.id }, { it.isTranslation }))

            for (ev in feed) {
                clockNow = ev.tsMs
                val row = ev.row
                val emitted: List<ScriptureEvent>
                if (!ev.isTranslation) {
                    val text = row.text ?: continue
                    val windowed = if (row.isFinal) {
                        push(txWindow, text)
                        windowedText(txWindow.toList(), null)
                    } else {
                        windowedText(txWindow.toList(), text)
                    } ?: continue
                    emitted = engine.processTranscription(
                        "live", windowed, row.speechType, row.segmentId, row.startTime, row.sessionId
                    )
                } else {
                    val text = row.translated ?: continue
                    val windowed = if (row.isFinal) {
                        push(trWindow, text)
                        windowedText(trWindow.toList(), null)
                    } else {
                        windowedText(trWindow.toList(), text)
                    } ?: continue
                    emitted = engine.processTranslation(
                        "live", windowed, row.speechType, row.segmentId, row.startTime, row.sessionId
                    )
                }
                for (e in emitted) {
                    lines.add(lineFor(row, e, ev.tsMs))
                    events.add(e)
                    eventTs.add(ev.tsMs)
                }
            }
            return ReplayResult(lines, events, eventTs)
        } finally {
            DetectionLogger.path = previousLogPath
        }
    }

    /** One privacy-safe JSONL line per emitted event: refs + scores only, no transcript text. */
    private fun lineFor(row: Row, e: ScriptureEvent, tsMs: Long = row.tsMs): String {
        val ref = "${e.reference.bookId}:${e.reference.chapter}:${e.reference.verseStart}" +
            (e.reference.verseEnd?.let { "-$it" } ?: "")
        val conf = (e.confidence * 1000).roundToInt() / 1000.0
        return buildString {
            append("{\"row\":").append(row.id)
            append(",\"tsMs\":").append(tsMs)
            append(",\"segmentId\":").append(jsonStr(e.segmentId))
            append(",\"type\":").append(jsonStr(e.type))
            append(",\"matchType\":").append(jsonStr(e.matchType))
            append(",\"ref\":").append(jsonStr(ref))
            // ref above is the matched MODULE's numbering — replaying the same service against a
            // differently-numbered module of the same translation shifts it (the two RST files here
            // disagree on the whole Psalter). The canonical code is what stays comparable, so a
            // golden diff caused by swapping bibles is visibly distinct from a real behaviour change.
            append(",\"canon\":").append(jsonStr(e.reference.canonicalCodeStart))
            append(",\"conf\":").append(conf)
            append(",\"tier\":").append(e.tier ?: "null")
            append(",\"bible\":").append(jsonStr(e.translation))
            append(",\"tracks\":[").append(e.tracks.joinToString(",") { jsonStr(it) }).append("]")
            append("}")
        }
    }

    private fun jsonStr(s: String?): String =
        if (s == null) "null" else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** Snapshot of every mutable Config field the replay/tests touch — restore to avoid test bleed. */
    fun snapshotConfig(): Map<String, Any> = mapOf(
        "level" to Config.level,
        "bibleRoot" to Config.bibleRoot,
        "reverseMinScoreRatio" to Config.reverseMinScoreRatio,
        "reverseMinAgreement" to Config.reverseMinAgreement,
        "trackCoverageMin" to Config.trackCoverageMin,
        "dedupTtlMs" to Config.dedupTtlMs,
        "minConfidenceEmit" to Config.minConfidenceEmit,
        "stickyTtlMs" to Config.stickyTtlMs,
        "reverseEnabled" to Config.reverseEnabled,
        "logCandidates" to Config.logCandidates,
        "versionDetectionEnabled" to Config.versionDetectionEnabled,
        "versionMinVerses" to Config.versionMinVerses,
        "versionMinEvidence" to Config.versionMinEvidence,
        "versionMinMargin" to Config.versionMinMargin,
        "versionDecay" to Config.versionDecay,
        "versionMissPenalty" to Config.versionMissPenalty,
        "versionMaxVerseDelta" to Config.versionMaxVerseDelta,
        "versionMinVerseCoverage" to Config.versionMinVerseCoverage,
        "versionCandidateMinJaccard" to Config.versionCandidateMinJaccard,
        "versionDuplicateJaccard" to Config.versionDuplicateJaccard,
        "versionMaxCorpusBibles" to Config.versionMaxCorpusBibles,
        "versionResetGapMs" to Config.versionResetGapMs,
        "versionSwitchMinMargin" to Config.versionSwitchMinMargin,
    )

    fun restoreConfig(snapshot: Map<String, Any>) {
        Config.level = snapshot["level"] as String
        Config.bibleRoot = snapshot["bibleRoot"] as String
        Config.reverseMinScoreRatio = snapshot["reverseMinScoreRatio"] as Double
        Config.reverseMinAgreement = snapshot["reverseMinAgreement"] as Double
        Config.trackCoverageMin = snapshot["trackCoverageMin"] as Double
        Config.dedupTtlMs = snapshot["dedupTtlMs"] as Long
        Config.minConfidenceEmit = snapshot["minConfidenceEmit"] as Double
        Config.stickyTtlMs = snapshot["stickyTtlMs"] as Long
        Config.reverseEnabled = snapshot["reverseEnabled"] as Boolean
        Config.logCandidates = snapshot["logCandidates"] as Boolean
        Config.versionDetectionEnabled = snapshot["versionDetectionEnabled"] as Boolean
        Config.versionMinVerses = snapshot["versionMinVerses"] as Int
        Config.versionMinEvidence = snapshot["versionMinEvidence"] as Double
        Config.versionMinMargin = snapshot["versionMinMargin"] as Double
        Config.versionDecay = snapshot["versionDecay"] as Double
        Config.versionMissPenalty = snapshot["versionMissPenalty"] as Double
        Config.versionMaxVerseDelta = snapshot["versionMaxVerseDelta"] as Double
        Config.versionMinVerseCoverage = snapshot["versionMinVerseCoverage"] as Double
        Config.versionCandidateMinJaccard = snapshot["versionCandidateMinJaccard"] as Double
        Config.versionDuplicateJaccard = snapshot["versionDuplicateJaccard"] as Double
        Config.versionMaxCorpusBibles = snapshot["versionMaxCorpusBibles"] as Int
        Config.versionResetGapMs = snapshot["versionResetGapMs"] as Long
        Config.versionSwitchMinMargin = snapshot["versionSwitchMinMargin"] as Double
    }
}
