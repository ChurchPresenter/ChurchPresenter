package org.churchpresenter.bibleengine.detection

/**
 * Converts spoken/written numbers to ints, for chapters and verses (range 1..199 is plenty —
 * Psalms top out at 150). Handles three forms seen in real STT output:
 *
 *  - plain digits:            "28"            -> 28
 *  - digit + ordinal suffix:  "3-я" "21-й" "19-го" -> 3 / 21 / 19
 *  - Russian number words:    "третья" "пятый" "семнадцатый" "двадцать первый" -> 3 / 5 / 17 / 21
 *
 * Word matching is by **stem prefix** (longest-first) so the many gender/case endings of Russian
 * ordinals/cardinals (третий/третья/третьего/третьем…) all collapse to one value without listing
 * every form. English spelled-out numbers ("twenty-eight") are covered too for the translation track.
 */
object NumberWords {

    /** A single token parsed to its value, or null. Digits, digit-ordinals, or one number word. */
    fun parseToken(token: String): Int? {
        val t = token.trim().lowercase()
        if (t.isEmpty()) return null
        if (t in NOT_NUMBERS) return null
        // digits, optionally with an ordinal suffix like "3-я", "21-й", "19-го", "2nd"
        DIGIT_ORD.find(t)?.let { return it.groupValues[1].toIntOrNull() }
        // a single number word: match a stem, but only when the remaining grammatical ending is
        // plausible — this rejects look-alikes such as "столько"/"сторона"/"дважды" -> not numbers.
        for ((stem, value) in STEMS) {
            if (!t.startsWith(stem)) continue
            val ending = t.substring(stem.length)
            if (ending.isEmpty() || ending[0] in VALID_ENDING_START) return value
        }
        return null
    }

    /**
     * Length of the number stem [parseToken] matched in [token], or 0 when it matched no word stem
     * (no match at all, or a pure-digit match, where "how long was the stem" has no meaning).
     *
     * Exists so a caller can weigh this lexicon against another one instead of treating a stem hit
     * as final — `ReferenceWatcher.isNumberRatherThanBook` compares it against the book stem, since
     * a book name beginning with a number stem ("Второзакония" over "втор") is otherwise swallowed.
     */
    fun matchedStemLength(token: String): Int {
        val t = token.trim().lowercase()
        if (t.isEmpty() || t in NOT_NUMBERS) return 0
        if (DIGIT_ORD.find(t) != null) return 0
        for ((stem, _) in STEMS) {
            if (!t.startsWith(stem)) continue
            val ending = t.substring(stem.length)
            if (ending.isEmpty() || ending[0] in VALID_ENDING_START) return stem.length
        }
        return 0
    }

    // Russian ordinal/cardinal endings start with a vowel, soft sign, or й; Latin endings with a
    // consonant cluster (th/st/nd/rd) handled by DIGIT_ORD only. Used to gate stem matches.
    private val VALID_ENDING_START = "йяеёьоаиуыю".toSet()

    // Genuine look-alikes that share a number stem but are NOT numerals. The forms of "семья"
    // (family) collide with the stem "семь" (7) — e.g. "глава семьи" (head of household) would
    // otherwise read as chapter 7. The VALID_ENDING gate can't catch these (their endings are
    // grammatically valid), so reject them explicitly. "семью" is left out: it is also the
    // instrumental of семь (7), so it stays ambiguous-but-numeric.
    private val NOT_NUMBERS = setOf(
        "семья", "семьи", "семье", "семьёй", "семьей", "семьею", "семей", "семьям", "семьями", "семьях",
    )

    /**
     * Parses a run of number tokens starting at [start], returning (value, tokensConsumed) or null.
     * Combines compounds by summation: "двадцать"(20) + "первый"(1) = 21, "сто"(100) + "пятый"(5) =
     * 105. Only consecutive **word** numbers are combined; a digit token is always taken alone so
     * unrelated digit sequences ("37 38") are never silently merged.
     */
    fun parseSequence(tokens: List<String>, start: Int): Pair<Int, Int>? {
        if (start !in tokens.indices) return null
        val first = parseToken(tokens[start]) ?: return null
        // A digit token (or digit-ordinal) stands alone.
        if (tokens[start].any { it.isDigit() }) return first to 1

        var sum = first
        var consumed = 1
        var prev = first
        var i = start + 1
        while (i < tokens.size) {
            if (tokens[i].any { it.isDigit() }) break
            val v = parseToken(tokens[i]) ?: break
            // Only combine when each part is strictly smaller than the previous (100 > 20 > 1);
            // this rejects "третий четвёртый" (two separate verses) while accepting "сто пятый".
            if (v >= prev) break
            sum += v
            prev = v
            consumed++
            i++
        }
        return sum to consumed
    }

    private val DIGIT_ORD = Regex("^(\\d{1,3})(?:-?(?:я|й|е|го|му|м|ой|ом|ая|ое|ый|ий|ст|нд|рд|th|nd|st|rd))?$")

    // Stem -> value, longest stem first so "пятнадцат"/"пятьдесят" win over "пят" (5).
    private val STEMS: List<Pair<String, Int>> = buildList {
        // teens (single tokens) — must precede their unit stems
        add("одиннадцат" to 11); add("двенадцат" to 12); add("тринадцат" to 13)
        add("четырнадцат" to 14); add("пятнадцат" to 15); add("шестнадцат" to 16)
        add("семнадцат" to 17); add("восемнадцат" to 18); add("девятнадцат" to 19)
        // tens
        add("двадцат" to 20); add("тридцат" to 30)
        add("сороков" to 40); add("сорок" to 40); add("сорока" to 40)
        add("пятьдесят" to 50); add("пятидесят" to 50); add("пятьюдесят" to 50)
        add("шестьдесят" to 60); add("шестидесят" to 60)
        add("семьдесят" to 70); add("семидесят" to 70)
        add("восемьдесят" to 80); add("восьмидесят" to 80)
        add("девяност" to 90)
        // hundred
        add("сотый" to 100); add("сотом" to 100); add("ста" to 100); add("сто" to 100)
        // 10 (after teens so "десят" doesn't shadow "девятнадцать")
        add("десят" to 10)
        // units / ordinals 1..9 — stems that uniquely identify the value
        add("одиннадц" to 11)
        add("перв" to 1); add("одна" to 1); add("одно" to 1); add("один" to 1); add("одн" to 1)
        add("втор" to 2); add("двух" to 2); add("две" to 2); add("два" to 2)
        add("трет" to 3); add("трёх" to 3); add("трех" to 3); add("три" to 3)
        add("четвёрт" to 4); add("четверт" to 4); add("четыр" to 4)
        add("пят" to 5)
        add("шест" to 6)
        add("седьм" to 7); add("семь" to 7); add("сем" to 7)
        add("восьм" to 8); add("восем" to 8); add("восьем" to 8)
        add("девят" to 9); add("девя" to 9)
        // English spelled-out (translation track), longest first
        add("eleven" to 11); add("twelve" to 12); add("thirteen" to 13); add("fourteen" to 14)
        add("fifteen" to 15); add("sixteen" to 16); add("seventeen" to 17); add("eighteen" to 18)
        add("nineteen" to 19); add("twenty" to 20); add("thirty" to 30); add("forty" to 40)
        add("fifty" to 50); add("sixty" to 60); add("seventy" to 70); add("eighty" to 80)
        add("ninety" to 90); add("hundred" to 100); add("hundredth" to 100)
        add("first" to 1); add("second" to 2); add("third" to 3); add("fourth" to 4); add("fifth" to 5)
        add("sixth" to 6); add("seventh" to 7); add("eighth" to 8); add("ninth" to 9); add("tenth" to 10)
        add("eleventh" to 11)
        add("one" to 1); add("two" to 2); add("three" to 3); add("four" to 4); add("five" to 5)
        add("six" to 6); add("seven" to 7); add("eight" to 8); add("nine" to 9); add("ten" to 10)
    }.sortedByDescending { it.first.length }
}
