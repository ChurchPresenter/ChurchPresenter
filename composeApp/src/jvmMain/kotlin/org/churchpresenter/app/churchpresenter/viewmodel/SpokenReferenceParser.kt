package org.churchpresenter.app.churchpresenter.viewmodel

/**
 * Detects Scripture references inside free speech (a live transcript) and returns them in reading
 * order. This is intentionally a pure, stateless object with no Compose / ViewModel dependencies so
 * it can be unit-tested directly against real transcription samples.
 *
 * The book name is returned as a raw phrase ([SpokenRef.bookPhrase]); resolving it to an actual book
 * index is the ViewModel's job (it needs the loaded Bible and its localized names).
 *
 * Detection is **keyword-anchored**: a reference is only emitted around an explicit chapter word
 * (ru "глава", en "chapter") or verse word (ru "стих", en "verse"). This keeps precision high — a
 * bare number in ordinary speech never triggers a detection. Explicit word *sets* (rather than open
 * stems) are used so common look-alikes such as ru "главное"/"главный" (main) or "стихия" (element)
 * do not false-trigger.
 */
object SpokenReferenceParser {

    /** A raw Scripture reference detected in speech, before book resolution. */
    data class SpokenRef(
        val bookPhrase: String?,   // raw book words incl. any leading "1"/"2"/"3"; null if none stated
        val chapter: Int?,
        val verseStart: Int?,
        val verseEnd: Int?,        // contiguous range end (e.g. "15 по 16"); null for single or list
        val raw: String,
    )

    /** Per-language vocabulary. Book names are matched elsewhere, against the loaded Bible. */
    private class LangPack(
        val chapterWords: Set<String>,
        val verseWords: Set<String>,
        val rangeWords: Set<String>,   // connect two numbers into a contiguous range ("по", "to")
        val listWords: Set<String>,    // separate non-contiguous numbers ("и", "and", ",")
        /** Spelled-out number stems → value, longest-first so "двадцат"(20) wins over "два"(2). */
        val numberStems: List<Pair<String, Int>>,
    )

    private val RU = LangPack(
        chapterWords = setOf("глава", "главе", "главу", "главы", "глав", "главам", "главах"),
        verseWords = setOf("стих", "стиха", "стихе", "стихи", "стихов", "стихам", "стихах", "стиху"),
        rangeWords = setOf("по", "с", "до"),
        listWords = setOf("и", "потом", "затем"),
        numberStems = ruNumberStems(),
    )

    private val EN = LangPack(
        chapterWords = setOf("chapter", "chapters", "chap"),
        verseWords = setOf("verse", "verses", "vs"),
        rangeWords = setOf("to", "through", "thru", "till", "until"),
        listWords = setOf("and"),
        numberStems = enNumberStems(),
    )

    /** Picks the vocabulary for [languageCode] (e.g. "ru", "en"); falls back to English. */
    private fun packFor(languageCode: String): LangPack =
        if (languageCode.lowercase().startsWith("ru")) RU else EN

    /**
     * Chooses the vocabulary from the **text itself** so detection works regardless of the OS/UI
     * locale: any meaningful amount of Cyrillic means Russian speech. [languageCode] is only a
     * fallback for non-Cyrillic text. (Russian services were being missed when the OS locale wasn't
     * "ru", because the English keyword set never matches "глава"/"стих".)
     */
    private fun pickPack(text: String, languageCode: String): LangPack {
        val cyrillic = text.count { it in 'Ѐ'..'ӿ' }
        return if (cyrillic >= 2) RU else packFor(languageCode)
    }

    // ── Token model ───────────────────────────────────────────────────────────
    private sealed interface Tok
    // end set only for "36-37" tokens; spelled = came from a number word (not digits), so adjacent
    // spelled tens+unit can be composed ("двадцать третий" → 23).
    private data class Num(val start: Int, val end: Int?, val spelled: Boolean = false) : Tok
    private data class Word(val text: String) : Tok               // lowercased content word
    private object ChapterKw : Tok
    private object VerseKw : Tok
    private object RangeConn : Tok
    private object ListConn : Tok

    private val DIGIT_RANGE = Regex("^(\\d{1,3})[-–—](\\d{1,3})$")
    private val DIGIT_ORDINAL = Regex("^(\\d{1,3})(?:[-–—]?[а-яёa-z]{1,2})?$") // "21", "3-я", "19го"

    private fun tokenize(text: String, pack: LangPack): List<Tok> {
        // Split on whitespace; commas act as list separators; other punctuation is stripped from
        // word edges but hyphens are kept so "36-37" / "3-я" survive as one token.
        val out = ArrayList<Tok>()
        val rawTokens = text.split(Regex("\\s+"))
        for (rawAny in rawTokens) {
            var raw = rawAny
            // A trailing comma means a list separator follows this token.
            val trailingComma = raw.endsWith(",")
            raw = raw.trim().trim('.', ',', ';', ':', '!', '?', '«', '»', '"', '(', ')').lowercase()
            if (raw.isNotEmpty()) {
                val rangeMatch = DIGIT_RANGE.matchEntire(raw)
                when {
                    rangeMatch != null -> out += Num(
                        rangeMatch.groupValues[1].toInt(), rangeMatch.groupValues[2].toInt()
                    )
                    raw in pack.chapterWords -> out += ChapterKw
                    raw in pack.verseWords -> out += VerseKw
                    raw in pack.rangeWords -> out += RangeConn
                    raw in pack.listWords -> out += ListConn
                    else -> {
                        val digits = DIGIT_ORDINAL.matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
                        when {
                            digits != null -> out += Num(digits, null, spelled = false)
                            else -> {
                                val spelled = spelledNumber(raw, pack)
                                out += if (spelled != null) Num(spelled, null, spelled = true) else Word(raw)
                            }
                        }
                    }
                }
            }
            if (trailingComma) out += ListConn
        }
        return composeSpelledNumbers(out)
    }

    /** Parses a spelled-out number word ("пятый", "twenty") to its value, or null. */
    private fun spelledNumber(token: String, pack: LangPack): Int? {
        for ((stem, value) in pack.numberStems) if (token.startsWith(stem)) return value
        return null
    }

    private val TENS = setOf(20, 30, 40, 50, 60, 70, 80, 90)

    /** Merges adjacent spelled tens + unit into one number ("двадцать"+"третий" → 23). */
    private fun composeSpelledNumbers(toks: List<Tok>): List<Tok> {
        val out = ArrayList<Tok>(toks.size)
        var i = 0
        while (i < toks.size) {
            val a = toks[i]
            val b = toks.getOrNull(i + 1)
            if (a is Num && a.spelled && a.end == null && a.start in TENS &&
                b is Num && b.spelled && b.end == null && b.start in 1..9
            ) {
                out += Num(a.start + b.start, null, spelled = true)
                i += 2
            } else {
                out += a
                i++
            }
        }
        return out
    }

    // ── Public entry point ─────────────────────────────────────────────────────

    /** Detects all references in [text] using the vocabulary for [languageCode]. */
    fun parse(text: String, languageCode: String): List<SpokenRef> {
        if (text.isBlank()) return emptyList()
        val pack = pickPack(text, languageCode)
        val toks = tokenize(text, pack)
        if (toks.isEmpty()) return emptyList()

        val refs = ArrayList<SpokenRef>()
        val consumed = BooleanArray(toks.size)

        var i = 0
        while (i < toks.size) {
            when (toks[i]) {
                is ChapterKw -> {
                    val chap = nearestNumber(toks, i, -1).takeIf { it.first != null }
                        ?: nearestNumber(toks, i, +1)
                    val chapter = chap.first
                    val chapterNumIdx = chap.second
                    val book = bookPhraseBefore(toks, i)
                    // Look ahead a short window for the matching verse keyword.
                    var verseStart: Int? = null
                    var verseEnd: Int? = null
                    val vIdx = indexOfWithin(toks, i + 1, VerseKw, window = 7)
                    if (vIdx >= 0) {
                        // Don't let the chapter number ("16 verses 30") be mistaken for the verse.
                        val ve = verseExpr(toks, vIdx, blockedLeftIndex = chapterNumIdx)
                        verseStart = ve.first; verseEnd = ve.second
                        consumed[vIdx] = true
                    }
                    // Only emit when a real number was found — a bare chapter word in ordinary
                    // speech ("очень интересная глава") is not a reference.
                    if (chapter != null || verseStart != null) {
                        refs += SpokenRef(book, chapter, verseStart, verseEnd, text)
                    }
                    consumed[i] = true
                    i = if (vIdx >= 0) vIdx + 1 else i + 1
                }
                is VerseKw -> {
                    if (!consumed[i]) {
                        val ve = verseExpr(toks, i)
                        // A bare "Иоанна 3 ... 5 стихи": try to recover book+chapter stated without a
                        // chapter keyword earlier on the line.
                        val (book, chapter) = bookChapterBeforeVerse(toks, i)
                        if (ve.first != null) {
                            refs += SpokenRef(book, chapter, ve.first, ve.second, text)
                        }
                    }
                    i++
                }
                else -> i++
            }
        }
        return refs
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun indexOfWithin(toks: List<Tok>, from: Int, target: Tok, window: Int): Int {
        var j = from
        val limit = minOf(toks.size, from + window)
        while (j < limit) {
            if (toks[j] === target) return j
            // Stop if another chapter keyword starts a new reference.
            if (j != from && toks[j] === ChapterKw) return -1
            j++
        }
        return -1
    }

    /**
     * Nearest single number from [anchor] walking [dir] (±1), skipping connectors. Returns
     * (value-or-null, index-or-(-1)) so callers can know which token supplied the number.
     */
    private fun nearestNumber(toks: List<Tok>, anchor: Int, dir: Int): Pair<Int?, Int> {
        var j = anchor + dir
        var hops = 0
        while (j in toks.indices && hops < 2) {
            val t = toks[j]
            if (t is Num) return t.start to j
            if (t === RangeConn || t === ListConn) { j += dir; hops++; continue }
            break
        }
        return null to -1
    }

    /**
     * Parses the verse number expression around a verse keyword. Russian places the number before
     * the keyword ("5 стих", "15 по 16 стих", "36-37 стихи"); English places it after ("verse 14",
     * "verses 30 to 31"). Returns (start, endOrNull); a non-contiguous list keeps the first only.
     */
    private fun verseExpr(toks: List<Tok>, anchor: Int, blockedLeftIndex: Int = -1): Pair<Int?, Int?> {
        // Prefer whichever side has a number directly adjacent to the keyword. The chapter number
        // (e.g. "16 verses 30") is excluded so it isn't read as the verse.
        val beforeIsNum = anchor - 1 >= 0 && toks[anchor - 1] is Num && (anchor - 1) != blockedLeftIndex
        val afterIsNum = anchor + 1 < toks.size && toks[anchor + 1] is Num
        if (beforeIsNum || (!afterIsNum)) {
            val left = collectNumberRun(toks, anchor - 1, step = -1, blockedIndex = blockedLeftIndex)
            if (left.first != null) return left
        }
        return collectNumberRun(toks, anchor + 1, step = +1, blockedIndex = -1)
    }

    /** Walks [step] (±1) from [from], collecting a contiguous run of numbers/connectors. */
    private fun collectNumberRun(toks: List<Tok>, from: Int, step: Int, blockedIndex: Int = -1): Pair<Int?, Int?> {
        val run = ArrayList<Tok>()
        var j = from
        var hops = 0
        while (j in toks.indices && hops < 6) {
            if (j == blockedIndex) break   // stop at the chapter number; it isn't part of the verse
            val t = toks[j]
            if (t is Num || t === RangeConn || t === ListConn) { run += t; j += step; hops++ }
            else break
        }
        // Reading order is left-to-right regardless of walk direction.
        val ordered = if (step < 0) run.asReversed() else run
        val nums = ArrayList<Int>()
        var sawRange = false
        for (t in ordered) {
            when {
                t is Num -> { nums += t.start; if (t.end != null) { nums += t.end; sawRange = true } }
                t === RangeConn -> sawRange = true
                else -> { /* list separator → numbers stay, but not a contiguous range */ }
            }
        }
        if (nums.isEmpty()) return null to null
        val end = if (sawRange && nums.size >= 2) nums.max() else null
        return nums.first() to end
    }

    /**
     * Collects the up-to-4 word/number tokens before a chapter anchor's number as the raw book
     * phrase (filler words are tolerated — the resolver picks the best-matching word). Includes a
     * leading book number ("1"/"2"/"3") when present so "1 Коринфянам" is distinguishable.
     */
    private fun bookPhraseBefore(toks: List<Tok>, chapterAnchor: Int): String? {
        // Skip the chapter number itself.
        var j = chapterAnchor - 1
        if (j >= 0 && (toks[j] is RangeConn || toks[j] is ListConn)) j--
        if (j >= 0 && toks[j] is Num) j--   // the chapter number
        val words = ArrayList<String>()
        var scanned = 0
        while (j >= 0 && words.size < 4 && scanned < 8) {
            val t = toks[j]
            when {
                t is Word -> words += t.text
                t is Num -> if (t.start in 1..3) words += t.start.toString() else break
                t === RangeConn || t === ListConn -> { /* skip commas/connectors between words */ }
                else -> break   // a chapter/verse keyword is a hard boundary
            }
            j--; scanned++
        }
        if (words.isEmpty()) return null
        return words.asReversed().joinToString(" ")
    }

    /**
     * For a verse-only reference, try to recover a book + bare chapter stated earlier on the line
     * without a chapter keyword, e.g. "Иоанна 3 ... 5 стихи". Returns (bookPhrase?, chapter?).
     */
    private fun bookChapterBeforeVerse(toks: List<Tok>, verseAnchor: Int): Pair<String?, Int?> {
        // Walk left past the verse number expression, then look for "<word(s)> <number>".
        var j = verseAnchor - 1
        // skip verse number expression
        while (j >= 0 && (toks[j] is Num || toks[j] is RangeConn || toks[j] is ListConn)) j--
        // now scan left a little for a number that could be a chapter preceded by words
        var hops = 0
        while (j >= 0 && hops < 6) {
            val t = toks[j]
            if (t is Num && t.start in 1..150) {
                // words before this number form the book phrase
                var k = j - 1
                val words = ArrayList<String>()
                var scanned = 0
                while (k >= 0 && words.size < 4 && scanned < 8) {
                    val w = toks[k]
                    when {
                        w is Word -> words += w.text
                        w is Num -> if (w.start in 1..3) words += w.start.toString() else break
                        w === RangeConn || w === ListConn -> { /* skip */ }
                        else -> break
                    }
                    k--; scanned++
                }
                if (words.isNotEmpty()) return words.asReversed().joinToString(" ") to t.start
                return null to t.start
            }
            if (t is ChapterKw || t is VerseKw) break
            j--; hops++
        }
        return null to null
    }

    // ── Number stem tables ───────────────────────────────────────────────────
    // Ordered longest-first within build helpers so the greedy startsWith match is correct.

    private fun ruNumberStems(): List<Pair<String, Int>> = listOf(
        // teens (must precede units so "одиннадцат" beats "одн", "двенадцат" beats "дв" etc.)
        "одиннадцат" to 11, "двенадцат" to 12, "тринадцат" to 13, "четырнадцат" to 14,
        "пятнадцат" to 15, "шестнадцат" to 16, "семнадцат" to 17, "восемнадцат" to 18,
        "девятнадцат" to 19,
        // tens
        "двадцат" to 20, "тридцат" to 30, "сороков" to 40, "сорок" to 40, "пятьдесят" to 50,
        "пятидесят" to 50, "шестьдесят" to 60, "шестидесят" to 60, "семьдесят" to 70,
        "восемьдесят" to 80, "девяност" to 90,
        // units — cardinals & ordinals share a stem. Short/ambiguous stems ("одн"→однако,
        // "ст"→стоит) are deliberately omitted to avoid mis-reading ordinary words as numbers.
        "перв" to 1, "втор" to 2, "две" to 2, "два" to 2, "трет" to 3, "три" to 3,
        "четверт" to 4, "четыр" to 4, "пят" to 5, "шест" to 6, "седьм" to 7, "семь" to 7,
        "восьм" to 8, "восем" to 8, "девят" to 9, "десят" to 10,
    ).sortedByDescending { it.first.length }

    private fun enNumberStems(): List<Pair<String, Int>> = listOf(
        "eleven" to 11, "twelve" to 12, "twelf" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "twentie" to 20, "thirty" to 30, "thirtie" to 30, "forty" to 40,
        "fortie" to 40, "fifty" to 50, "fiftie" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100,
        "first" to 1, "one" to 1, "second" to 2, "two" to 2, "third" to 3, "three" to 3,
        "fourth" to 4, "four" to 4, "fifth" to 5, "five" to 5, "sixth" to 6, "six" to 6,
        "seventh" to 7, "seven" to 7, "eighth" to 8, "eight" to 8, "ninth" to 9, "nine" to 9,
        "tenth" to 10, "ten" to 10,
    ).sortedByDescending { it.first.length }
}
