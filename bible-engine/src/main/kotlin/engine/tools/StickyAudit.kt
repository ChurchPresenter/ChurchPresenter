package engine.tools

import engine.detection.BookResolver
import engine.detection.ReferenceWatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Offline auditor for sticky-log-*.jsonl (see DetectionLogger.logStickyChange / TRAINING_PLAN.md).
 * Classifies every recorded sticky-book/chapter jump so a human doesn't have to manually
 * cross-reference timestamps against the transcript to judge whether a jump was justified — the
 * exact multi-hour manual step that diagnosed an earlier session's bugs. Uses the SAME
 * BookResolver.ALIASES / BookResolver.resolveStem the live engine uses, so this can never drift out
 * of sync with what the detector actually does as the alias table grows.
 *
 * Usage: ./gradlew stickyAudit --args="/path/to/sticky-log-SESSION.jsonl"
 */

internal data class StickyRow(
    val ts: String,
    val prevBook: Int?,
    val prevChapter: Int?,
    val newBook: Int?,
    val newChapter: Int?,
    val transcript: String,
    val translation: String,
)

internal enum class Category { CHAPTER_CLEARED, UNEXPLAINED, SHORT_ALIAS, STEM_OVEREXTENSION, CONFIDENT, OTHER }

internal data class Verdict(val row: StickyRow, val category: Category, val detail: String)

fun main(args: Array<String>) {
    val path = args.firstOrNull()
    if (path == null) {
        System.err.println("Usage: stickyAudit <path-to-sticky-log.jsonl>")
        return
    }
    val file = File(path)
    if (!file.exists()) {
        System.err.println("File not found: $path")
        return
    }

    val rows = file.readLines().filter { it.isNotBlank() }.mapNotNull { parseRow(it) }
    val verdicts = rows.map { classify(it) }
    printReport(path, verdicts)
}

private fun parseRow(line: String): StickyRow? = runCatching {
    val obj = Json.parseToJsonElement(line).jsonObject
    StickyRow(
        ts = obj["ts"]?.jsonPrimitive?.contentOrNull ?: "",
        prevBook = obj["prevBook"]?.jsonPrimitive?.intOrNull,
        prevChapter = obj["prevChapter"]?.jsonPrimitive?.intOrNull,
        newBook = obj["newBook"]?.jsonPrimitive?.intOrNull,
        newChapter = obj["newChapter"]?.jsonPrimitive?.intOrNull,
        transcript = obj["transcript"]?.jsonPrimitive?.contentOrNull ?: "",
        translation = obj["translation"]?.jsonPrimitive?.contentOrNull ?: "",
    )
}.getOrNull()

/**
 * [CHAPTER_CLEARED] is a pure structural check (no alias knowledge needed) — the exact shape of the
 * same-book-reflush bug, since fixed. For an actual book change, the remaining categories judge
 * how well the new book is textually supported.
 *
 * A first version of this classifier flagged *any* stem-fallback match as risky, which turned out to
 * be nearly all of them — `resolveStem`'s inflection tolerance is how ordinary Russian book names
 * normally resolve (Лука → "Луки", Даниил → "Даниила"), not a sign of trouble on its own. Empirically
 * re-run against an earlier session log, that version flagged 35 of 61 book changes — useless
 * noise. What actually distinguished the three real bugs found that session:
 *  - "бытие"/"song"/"при" are SHORT exact aliases (<6 chars) that double as ordinary vocabulary —
 *    [SHORT_ALIAS].
 *  - "открывает" matched only by extending 5 characters past its stem ("откр") — a large gap between
 *    the matched stem and the actual spoken word, unlike a normal grammatical ending (1-2 chars) —
 *    [STEM_OVEREXTENSION].
 * Note a real limitation: "иоанну" (dative "to John", narrating the apostle, not citing the Gospel)
 * is a *small*, grammatically ordinary extension over its stem ("иоанн" + "у"), so no string-length
 * heuristic catches it — that one needed a human to actually read the sentence. This tool narrows
 * down which rows are worth reading, it doesn't replace reading them.
 */
internal fun classify(row: StickyRow): Verdict {
    if (row.prevBook != null && row.prevBook == row.newBook &&
        row.prevChapter != null && row.newChapter == null
    ) {
        return Verdict(row, Category.CHAPTER_CLEARED, "book ${row.newBook} kept, chapter ${row.prevChapter} -> null")
    }
    val newBook = row.newBook
    if (newBook == null || newBook == row.prevBook) {
        return Verdict(row, Category.OTHER, "")
    }

    val tokens = tokenize("${row.transcript} ${row.translation}")

    if (hasMultiTokenAliasHit(tokens, newBook)) return Verdict(row, Category.CONFIDENT, "")

    // Numbered books ("во втором послании Коринфянам" → 2 Corinthians) never appear in the alias
    // table under their spoken form — the ordinal is read off the surrounding tokens by
    // ReferenceWatcher.resolveNumberedBookAt. Ask that same function, or every such jump is filed
    // as UNEXPLAINED, which is the one category that must stay trustworthy: 4 of the 5 UNEXPLAINED
    // rows across earlier sessions were correct resolutions of exactly this shape, and they
    // buried the one that was real.
    if (tokens.indices.any { ReferenceWatcher.resolveNumberedBookAt(tokens, it)?.first == newBook }) {
        return Verdict(row, Category.CONFIDENT, "")
    }

    // Exact single-token alias match. Length floor of 3 mirrors ReferenceWatcher.classify's own
    // floor for single-token aliases (it skips <=2-char aliases like "мк"/"ре" entirely as too risky
    // to ever fire from prose) — below that, a coincidental match here wouldn't explain a real jump
    // anyway, since the live engine would never have used it.
    val exactHit = tokens.firstOrNull { BookResolver.ALIASES[it] == newBook && it.length >= 3 }
    if (exactHit != null) {
        return if (exactHit.length >= 6) Verdict(row, Category.CONFIDENT, "")
        else Verdict(
            row,
            Category.SHORT_ALIAS,
            "matched a short exact alias \"$exactHit\" — confirm this wasn't ordinary vocabulary",
        )
    }

    // Reachable only via the inflection-tolerant stem fallback — measure how far the matched word
    // extends past the shortest stem that explains it.
    val stemToken = tokens.firstOrNull { BookResolver.resolveStem(it)?.bookNum == newBook }
    if (stemToken != null) {
        val extension = extensionOverBestStem(stemToken, newBook)
        return if (extension >= 3) {
            Verdict(row, Category.STEM_OVEREXTENSION,
                "\"$stemToken\" extends ${extension} chars past its matched book alias's stem — " +
                    "confirm this wasn't an unrelated word")
        } else {
            Verdict(row, Category.CONFIDENT, "")
        }
    }

    return Verdict(row, Category.UNEXPLAINED, "no alias/stem match for book $newBook anywhere in the text")
}

private fun hasMultiTokenAliasHit(tokens: List<String>, book: Int): Boolean {
    for (i in tokens.indices) {
        for (len in 3 downTo 2) {
            if (i + len > tokens.size) continue
            val phrase = tokens.subList(i, i + len).joinToString(" ")
            if (BookResolver.ALIASES[phrase] == book) return true
        }
    }
    return false
}

// Mirrors BookResolver's private stemOf()/MIN_STEM/RU_TRIM (only the algorithm, not any alias data —
// this formula is stable and effectively never changes, unlike the alias table which grows every
// session, so duplicating just this small piece carries negligible drift risk).
private const val STEM_MIN_LEN = 4
private val STEM_RU_TRIM = "йьяиаеоуюёы".toSet()

private fun approxStemOf(s: String): String {
    var x = s
    while (x.length > STEM_MIN_LEN && x.last() in STEM_RU_TRIM) x = x.dropLast(1)
    return x
}

/** How many characters [token] extends past the longest single-token alias stem for [book] that it
 * has as a prefix — 0 if none found (shouldn't happen when called after a confirmed resolveStem hit). */
private fun extensionOverBestStem(token: String, book: Int): Int {
    val bestStemLen = BookResolver.ALIASES.entries
        .filter { it.value == book && !it.key.contains(' ') && it.key.length >= STEM_MIN_LEN }
        .map { approxStemOf(it.key) }
        .filter { token.startsWith(it) }
        .maxOfOrNull { it.length } ?: return 0
    return token.length - bestStemLen
}

private fun tokenize(text: String): List<String> =
    text.lowercase()
        .replace(Regex("[^\\p{L}0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

private fun printReport(path: String, verdicts: List<Verdict>) {
    val label = File(path).name
    val byCategory = verdicts.groupBy { it.category }
    val unexplained = byCategory[Category.UNEXPLAINED].orEmpty()
    val chapterCleared = byCategory[Category.CHAPTER_CLEARED].orEmpty()
    val shortAlias = byCategory[Category.SHORT_ALIAS].orEmpty()
    val stemOverext = byCategory[Category.STEM_OVEREXTENSION].orEmpty()
    val confident = byCategory[Category.CONFIDENT].orEmpty()
    val other = byCategory[Category.OTHER].orEmpty()

    println()
    println(
        "=== sticky-audit $label  jumps=${verdicts.size}  " +
            "unexplained=${unexplained.size} chapter-cleared=${chapterCleared.size} " +
            "short-alias=${shortAlias.size} stem-overext=${stemOverext.size} " +
            "confident=${confident.size} other=${other.size} ==="
    )
    println()

    if (unexplained.isNotEmpty()) {
        println(
            "UNEXPLAINED (${unexplained.size}) — no alias/stem match found for the new book anywhere " +
                "in the text; likely a NEW, undiagnosed bug pattern. (One benign cause: the live " +
                "engine also resolves book names the loaded SPB modules register at startup, which " +
                "this tool cannot see — e.g. the Russian Synodal names book 65 «Иуда».)"
        )
        unexplained.forEach(::printRow)
        println()
    }
    if (chapterCleared.isNotEmpty()) {
        println(
            "CHAPTER-CLEARED SAME-BOOK (${chapterCleared.size}) — should be ~zero after the " +
                "same-book-reflush fix; any hit is a regression or a new variant:"
        )
        chapterCleared.forEach(::printRow)
        println()
    }
    if (shortAlias.isNotEmpty()) {
        println(
            "SHORT ALIAS (${shortAlias.size}) — matched only via a short (<6-char) exact alias, the " +
                "shape of the \"бытие\" bug — consider adding to AMBIGUOUS_BOOK_FORMS if this recurs:"
        )
        shortAlias.forEach(::printRow)
        println()
    }
    if (stemOverext.isNotEmpty()) {
        println(
            "STEM OVER-EXTENSION (${stemOverext.size}) — matched word extends well past its book " +
                "alias's stem, the shape of the \"открывает\"/\"откр\" bug — consider adding to " +
                "AMBIGUOUS_BOOK_FORMS if this recurs:"
        )
        stemOverext.forEach(::printRow)
        println()
    }
    println(
        "CONFIDENT (${confident.size}) — resolved via an explicit/long alias or a normal " +
            "grammatical ending, no review needed."
    )
    if (other.isNotEmpty()) {
        println("OTHER (${other.size}) — same book, or a book-only prime with no change of note.")
    }
    println()
}

private fun printRow(v: Verdict) {
    val r = v.row
    val trigger = r.transcript.ifBlank { r.translation }.take(100)
    println("  book ${r.newBook} <- book ${r.prevBook}  ts=${r.ts}  ${v.detail}")
    println("    transcript: \"$trigger\"")
}
