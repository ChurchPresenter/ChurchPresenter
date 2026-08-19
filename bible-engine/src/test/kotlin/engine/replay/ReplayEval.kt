package engine.replay

import engine.AppConfig
import java.io.File

/**
 * Scores a replayed session against operator ground truth — run via `./gradlew replayEval`:
 *
 *   ./gradlew replayEval --args="--db /path/service.db --lref /path/live-references-<id>.jsonl \
 *       [--outcomes /path/suggestion-outcomes-<id>.jsonl] [--out /tmp/replay.jsonl] \
 *       [--bibles 'a.spb,b.spb'] [--level balanced]"
 *
 * Matching rule (TRAINING_PLAN's evaluation window): a detection is a TP for a live reference when
 * they name the same verse and |detection ts − live ts| <= 90 s. "The same verse" is compared in
 * canonical (`BxxxCyyyVzzz`) numbering when the ground truth is canonical — see [GroundTruth] — since
 * the engine reports the matched *module's* numbering, and two modules of the same translation can
 * number Psalms differently. Live references are the operator's ground truth (what was actually
 * shown); `action:"corrected"` rows from suggestion-outcomes are counted as labeled false positives.
 *
 * Load the bibles the service actually ran with (`--bibles`), or the replay resolves explicit
 * citations against a different Psalter than the operator heard.
 *
 * Output: per-matchType table (emitted / TP / labeled-FP) + the FN list (live refs no detection
 * matched). Detections outside the sermon bracket (first..last live ref ± window) are reported
 * separately as "unbracketed" — not judged, since there is no ground truth there.
 */
object ReplayEval {

    private const val WINDOW_MS = 90_000L

    /**
     * One operator go-live. [canonical] is true when the row is in the engine's module-independent
     * canonical numbering — recognised by the `display*` fields ChurchPresenter writes alongside
     * them. Recordings made before those fields existed carry the primary Bible's own numbering
     * instead, which only agrees
     * with a replay when that replay loads the very same module (this machine has two RST files that
     * number Psalms differently), so they are matched the old way.
     */
    data class GroundTruth(
        val tsMs: Long, val book: Int, val chapter: Int, val verse: Int, val canonical: Boolean = false,
    )

    /** `BxxxCyyyVzzz` → book/chapter/verse, the numbering every module agrees on. */
    private fun parseCode(code: String?): Triple<Int, Int, Int>? {
        val m = Regex("^B(\\d{3})C(\\d{3})V(\\d{3})$").find(code ?: return null) ?: return null
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }

    /** True when [t] names the same verse [e] does, in whichever space [t] is expressed in. */
    private fun sameVerse(t: GroundTruth, e: engine.engine.ScriptureEvent): Boolean {
        if (t.canonical) {
            val c = parseCode(e.reference.canonicalCodeStart)
            if (c != null) return t.book == c.first && t.chapter == c.second && t.verse == c.third
        }
        return t.book == e.reference.bookId && t.chapter == e.reference.chapter &&
            t.verse == e.reference.verseStart
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val opts = parseArgs(args)
        val dbPath = opts["db"] ?: error("--db required")
        val lrefPath = opts["lref"] ?: error("--lref required")
        val level = opts["level"] ?: "balanced"
        val bibles = opts["bibles"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val bibleRoot = AppConfig.discoverBibleRoot() ?: error("no bible root discoverable")
        val translations = DbReplay.loadTranslations(bibleRoot, bibles)
        check(translations.isNotEmpty()) { "no bibles loadable from $bibleRoot" }
        println("bibles: ${translations.map { it.id }}, level: $level")

        val rows = DbReplay.readRows(dbPath)
        println("db rows: ${rows.size}")
        val result = DbReplay.replay(rows, translations, level)
        println("emitted events: ${result.events.size}")
        opts["out"]?.let { out ->
            File(out).writeText(result.lines.joinToString("\n") + "\n")
            println("replay written: $out")
        }

        val truth = readLiveReferences(File(lrefPath))
        check(truth.isNotEmpty()) { "no live references in $lrefPath" }
        val corrected = opts["outcomes"]?.let { readCorrectedOutcomes(File(it)) } ?: emptyList()

        val bracketStart = truth.minOf { it.tsMs } - WINDOW_MS
        val bracketEnd = truth.maxOf { it.tsMs } + WINDOW_MS

        // Per-detection judgement
        val matchTypes = result.events.map { it.matchType }.distinct().sorted()
        data class Bucket(var emitted: Int = 0, var tp: Int = 0, var labeledFp: Int = 0, var unbracketed: Int = 0)
        val buckets = LinkedHashMap<String, Bucket>()
        matchTypes.forEach { buckets[it] = Bucket() }

        result.events.forEachIndexed { i, e ->
            val ts = result.eventTsMs[i]
            val bucket = buckets.getValue(e.matchType)
            bucket.emitted++
            if (ts < bracketStart || ts > bracketEnd) {
                bucket.unbracketed++
                return@forEachIndexed
            }
            if (truth.any { t -> sameVerse(t, e) && kotlin.math.abs(t.tsMs - ts) <= WINDOW_MS }) bucket.tp++
            if (corrected.any { c -> sameVerse(c, e) && kotlin.math.abs(c.tsMs - ts) <= WINDOW_MS }) {
                bucket.labeledFp++
            }
        }

        println()
        println("%-16s %8s %6s %10s %12s".format("matchType", "emitted", "TP", "labeledFP", "unbracketed"))
        buckets.forEach { (type, b) ->
            println("%-16s %8d %6d %10d %12d".format(type, b.emitted, b.tp, b.labeledFp, b.unbracketed))
        }
        println()
        // A go-live is covered when ANY detection names that verse inside the window. Matching
        // one-truth-row-per-detection instead would report the same verse re-shown later in the
        // service as a miss: in one recorded service the operator walked Psalm 24 twice, and 3 of the 5
        // reported FNs were simply the second showing of a verse the engine had already found.
        val fns = truth.filterIndexed { i, t ->
            result.events.indices.none { j ->
                sameVerse(t, result.events[j]) && kotlin.math.abs(t.tsMs - result.eventTsMs[j]) <= WINDOW_MS
            }
        }
        val matchedTruth = truth.size - fns.size
        println("ground-truth live references: ${truth.size}, matched: $matchedTruth, FN: ${fns.size}")
        fns.forEach { println("  FN: book=${it.book} ${it.chapter}:${it.verse} at tsMs=${it.tsMs}") }
    }

    private fun readLiveReferences(file: File): List<GroundTruth> =
        file.readLines().mapNotNull { line ->
            if (line.isBlank() || line.contains("\"type\":\"session\"")) return@mapNotNull null
            val book = intField(line, "book") ?: return@mapNotNull null
            val chapter = intField(line, "chapter") ?: return@mapNotNull null
            val verse = intField(line, "verseStart") ?: return@mapNotNull null
            val ts = longField(line, "ts_ms") ?: return@mapNotNull null
            GroundTruth(ts, book, chapter, verse, canonical = line.contains("\"displayChapter\""))
        }

    private fun readCorrectedOutcomes(file: File): List<GroundTruth> =
        file.readLines().mapNotNull { line ->
            if (!line.contains("\"action\":\"corrected\"")) return@mapNotNull null
            val book = intField(line, "suggestedBook") ?: return@mapNotNull null
            val chapter = intField(line, "suggestedChapter") ?: return@mapNotNull null
            val verse = intField(line, "suggestedVerse") ?: return@mapNotNull null
            val ts = longField(line, "ts_ms") ?: return@mapNotNull null
            GroundTruth(ts, book, chapter, verse, canonical = line.contains("\"displayChapter\""))
        }

    // The logs are flat single-line JSON written by TrainingDataLogger — regex extraction keeps
    // this tool dependency-free (org.json would work too; not worth the coupling for two files).
    private fun intField(line: String, name: String): Int? =
        Regex("\"$name\":(-?\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()

    private fun longField(line: String, name: String): Long? =
        Regex("\"$name\":(-?\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()

    private fun parseArgs(args: Array<String>): Map<String, String> {
        val map = HashMap<String, String>()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--") && i + 1 < args.size) {
                map[a.removePrefix("--")] = args[i + 1]
                i += 2
            } else {
                i++
            }
        }
        return map
    }
}
