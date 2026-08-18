package engine.detection

import engine.Config

/**
 * Stateful explicit-reference detector. Replaced the older single-string ExplicitParser for the live
 * feed: it scans an utterance into typed atoms (book / chapter-keyword / verse-keyword / number /
 * range / colon) and interprets them into references, while carrying a **sticky** book+chapter
 * across utterances so a later bare "N стих" (verse-by-verse reading) still resolves.
 *
 * Evidence tiers (consumed by the caller to set confidence / gate auto-follow):
 *   1 FULL    — book + chapter + verse all present in this utterance
 *   2 STICKY  — verse resolved against the carried context, no book in this utterance
 *
 * A book (and/or chapter) named with no verse yet — either together in one utterance ("1 Corinthians
 * 11", then a tangent before reading) or split across utterances (a book named now, a bare chapter
 * named later via the sticky) — primes the sticky context (see [emit]) but never emits a [Ref]: there
 * is no evidence of *which* verse to show yet, and guessing verse 1 would put a wrong reference on
 * screen until the real one is read, possibly much later. This is what lets book, chapter, and verse
 * each resolve independently as they're spoken, rather than requiring one fully-formed phrase.
 *
 * Pure w.r.t. the Bible data — it only resolves book *numbers* via [BookResolver]; the caller looks
 * up verse text. [process] mutates only the sticky fields on the passed sticky holder.
 */
object ReferenceWatcher {

    /** Mutable sticky context — backed by fields on UtteranceState (kept out of this module). */
    interface Sticky {
        var watchBook: Int?
        var watchChapter: Int?
        var watchExpiresAt: Long
    }

    data class Ref(
        val bookNum: Int,
        val chapter: Int,
        val verseStart: Int?,
        val verseEnd: Int?,
        val tier: Int,          // 1 FULL, 2 STICKY
    )

    // Non-Russian chapter/verse keyword stems use plain prefix matching (low FP risk, no data to
    // refine). The Russian глав-/стих- stems are handled separately below with inflection-aware
    // endings so look-alikes (главное, стихотворение) don't fire as keywords.
    private val CHAP_KW = listOf(
        "kapitel", "kapit", "chapter", "chapitre", "capitul", "capitol", "розділ", "rozdzia",
    )
    private val VERSE_KW = listOf(
        "вірш", "versicul", "versiculo", "verset", "verse", "vers", "wiersz",
    )
    // Valid grammatical endings after the Russian stems "глав"/"стих". The whole suffix must match
    // (not just its first char) — that is what separates глава/главы/глав from главное/главный and
    // стих/стиха/стихи/стихом from стихотворение.
    private val CHAP_RU_ENDINGS = setOf("", "а", "ы", "е", "у", "ой", "ою", "ам", "ах", "ами")
    private val VERSE_RU_ENDINGS = setOf("", "а", "е", "у", "и", "ов", "ом", "ах", "ами")
    private val RANGE_WORDS = setOf("по", "до", "через", "bis", "to", "through", "hasta", "até", "ate")
    private val LIST_WORDS = setOf("и", "или", "потом", "затем", "then", "and")
    private val FROM_WORDS = setOf("с", "со", "from", "desde")

    // Gate thresholds for prose-vs-citation disambiguation (see classify). SHORT_ALIAS_MAX_LEN:
    // single-token exact aliases at or under this length ("job", "при") are common words in
    // prose. STEM_MAX_EXTENSION_UNCORROBORATED: a token this many chars longer than its matched
    // stem is treated as vocabulary unless corroborated (StickyAudit's risky band).
    private const val SHORT_ALIAS_MAX_LEN = 4
    private const val STEM_MAX_EXTENSION_UNCORROBORATED = 3
    // Bare cardinal "one" (count) — distinct from the ordinal (первый/first) and the digit "1". As
    // a chapter/verse number a real reference uses the ordinal or the digit, so "один стих" / "one
    // verse" means "one verse" (a quantity), never "verse one". Treated as filler so it can't bind
    // to a sticky context. Both RU and EN forms are listed because the translation track is fed
    // through the same watcher (DetectionEngine combines transcript + translation).
    private val BARE_ONE = setOf("один", "одна", "одно", "одни", "одну", "one")

    // ── Numbered-book (ordinal) disambiguation ──────────────────────────────────
    // Two related problems share one mechanism:
    //  - "Иоанна" is the Gospel of John (43); "1-е/Первое Послание Иоанна" is the *epistle* 1 John
    //    (62). The plain alias table resolves the spoken name to the gospel, and an intervening
    //    «Послание» breaks the "1 иоанна" greedy multi-token alias — so the epistle was being
    //    dropped to the gospel. A marker word alone ("Послание Иоанна", no ordinal) already
    //    conventionally means the 1st (1 John) — there's no unnumbered alternative to guard against
    //    once a marker is present.
    //  - Every OTHER numbered book family (Царств, Паралипоменон, Коринфянам, Фессалоникийцам,
    //    Тимофею) only has digit-adjacent aliases registered in BookResolver ("1 царств", "2
    //    коринфянам"…) — a spelled Russian ordinal ("Первая книга царств") doesn't resolve at all.
    //    Unlike John/Peter these have no unnumbered meaning, so a marker/filler word alone must NOT
    //    resolve (that stays the existing, accepted "bare ambiguous numbered book" gap, e.g. bare
    //    "Коринфянам" with no ordinal) — only an explicit ordinal (digit or word) may fire.
    // When the marker/filler word («послание»/«письмо»/«книга») or an explicit ordinal precedes one
    // of these forms, resolve to the numbered book, with the ordinal selecting which.
    private val EPISTLE_MARKER_STEMS = listOf("послани", "письм", "книг") // послание/письмо/книга…
    private val EPISTLE_CONNECTORS = setOf("к", "ко")
    // How far past a book name to look for a trailing "в первом послании", and the words allowed to
    // sit in between (verbs of speaking are what actually occur: "Иоанн ГОВОРИТ в первом послании").
    private const val AHEAD_WINDOW = 4
    private val AHEAD_FILLERS = setOf(
        "в", "во", "говорит", "пишет", "сказал", "says", "writes", "wrote", "in", "the", "his",
    )
    // Inflected spoken forms of John / Peter that, in an epistle context, mean the epistle.
    private val JOHN_FORMS = setOf("иоанна", "иоанн", "иоанну", "иоанне")
    private val PETER_FORMS = setOf("петра", "петру", "петре", "петр", "пётр")
    // base = canonical id of the 1st book in the family, count = how many numbered variants exist.
    // markerAloneDefaultsToFirst: true only for John/Peter (see comment above); the bare stems below
    // are taken from the digit-stripped forms already registered in BookResolver.kt, so they're
    // forms already vetted from real transcripts — further inflections can be added the same way as
    // future training data surfaces them.
    private data class NumberedBookSpec(val base: Int, val count: Int, val markerAloneDefaultsToFirst: Boolean)
    private val NUMBERED_BOOK_FORMS: Map<String, NumberedBookSpec> = buildMap {
        for (f in JOHN_FORMS) put(f, NumberedBookSpec(62, 3, markerAloneDefaultsToFirst = true))
        for (f in PETER_FORMS) put(f, NumberedBookSpec(60, 2, markerAloneDefaultsToFirst = true))
        put("царств", NumberedBookSpec(9, 4, markerAloneDefaultsToFirst = false))
        put("царство", NumberedBookSpec(9, 4, markerAloneDefaultsToFirst = false))
        put("паралипоменон", NumberedBookSpec(13, 2, markerAloneDefaultsToFirst = false))
        put("коринфянам", NumberedBookSpec(46, 2, markerAloneDefaultsToFirst = false))
        put("фессалоникийцам", NumberedBookSpec(52, 2, markerAloneDefaultsToFirst = false))
        put("солунян", NumberedBookSpec(52, 2, markerAloneDefaultsToFirst = false))
        put("тимофею", NumberedBookSpec(54, 2, markerAloneDefaultsToFirst = false))
    }

    // ── Ambiguous common-word RU book aliases ───────────────────────────────────
    // Bare forms that are also ordinary vocabulary or narration naming a person, not citing a book:
    // dative/locative "Иоанну"/"Иоанне" narrate the apostle by name (a real citation is genitive/
    // nominative "Иоанна"/"Иоанн" — already unconditional aliases, untouched here); "бытие"/"быт" is
    // ordinary vocabulary ("being/existence") at least as often as it names Genesis. These must NOT
    // resolve to Atom.Book on the bare word alone; they need a chapter-number digit within 2 tokens
    // either side, or an explicit book/epistle/gospel marker noun in the preceding 2 tokens (bare
    // prepositions like «в»/«от» deliberately do NOT count — too common in ordinary prose; the real
    // "бытие" false positive was itself "...в то бытие"). Add further forms here the same way as
    // future training data surfaces them (mirrors NUMBERED_BOOK_FORMS above).
    // internal (not private): src/test's fuzz test iterates this table directly so any future
    // addition is automatically covered without touching the test.
    internal val AMBIGUOUS_BOOK_FORMS: Map<String, Int> = mapOf(
        "иоанну" to 43, "иоанне" to 43,
        "бытие" to 1, "быт" to 1,
        // A prophet's name is also his book's name, and a preacher expounding the book narrates
        // the man constantly ("Иеремия говорит…"). Each bare mention re-pointed the sticky, which
        // is how that sermon — preached from Lamentations 3 — produced three tier-1
        // auto-go-live detections in Jeremiah (3:25, 22:22, 18:18) and one CHAPTER-CLEARED row.
        //
        // Nominative only, exactly as "иоанну"/"иоанне" above are listed and "иоанна"/"иоанн" are
        // not: the oblique cases are what a citation uses ("в 22 главе Иеремии", "книга пророка
        // Иеремии"), so gating them would cost real references, while the nominative is the form
        // that heads a sentence about the man. The one nominative citation shape — a bare name
        // followed by its numbers, "Иеремия 33:3" — keeps the digit in reach and passes the gate.
        "иеремия" to 24,
        // "judas" is the GERMAN name for Jude, but the alias table is language-agnostic at lookup,
        // so it fires on the English track too — where "Judas" is the betrayer, a proper noun in
        // essentially every Passion sermon, and at 5 chars it clears SHORT_ALIAS_MAX_LEN and
        // resolved unconditionally. Exactly the Russian «Иуда» bug (since fixed) on the EN
        // side, which no test covered because that session's MT garbled the word. A real German
        // citation keeps its number in reach and still passes the gate.
        "judas" to 65,
    )

    // Some ambiguous words are ambiguous in EVERY inflected form — "бытие"/"бытия"/"бытием"/"бытии"
    // all mean "being/existence" as ordinary vocabulary exactly as often as they cite Genesis, no
    // matter the case ending — unlike "иоанну"/"иоанне" above, where only the dative/locative case
    // is ambiguous (genitive "иоанна"/nominative "иоанн" are already unconditional exact aliases
    // handled earlier in classify()'s greedy branch, and both happen to share the stem "иоанн" —
    // folding them in here would wrongly demand corroboration for those already-correct forms).
    // Gate the inflection-tolerant resolveStem() path (classify) by stem instead of exact token so
    // every case ending is covered without hardcoding each one — real trace: sticky-log
    // a recorded moment, genitive "бытия" (a machine-translation tail of "...level of his
    // being") slipped through the exact-token gate and cleared a sticky chapter mid-sermon.
    internal val AMBIGUOUS_BOOK_STEMS: Map<String, Int> = mapOf(
        BookResolver.stemOf("бытие") to 1,
        // "откр" is an abbreviation alias for Откровение, and it is also the prefix of a whole family
        // of ordinary verbs (открыть/открой/открыл/открывает — "to open"). The stem
        // over-extension gate cannot separate them: the stem is only 4 chars, so the common forms sit
        // 1-2 chars past it, inside the allowance for a normal grammatical ending. Real trace:
        // "...чтобы сердце открой." flipped the sticky book to Revelation mid-sermon
        // (sticky-log-S12). Costs no real citation — a spelled-out
        // "Откровении" resolves through the longer "откровен" stem (resolveStem takes the longest
        // match), and the abbreviation itself is only ever spoken next to a chapter number.
        BookResolver.stemOf("откр") to 66,
    )

    private sealed interface Atom {
        data class Book(val num: Int) : Atom
        object ChapKw : Atom
        object VerseKw : Atom
        data class Num(val value: Int) : Atom
        object Range : Atom
        object Colon : Atom
        object ListSep : Atom
        object From : Atom
        object Filler : Atom
    }

    /**
     * Parse [text]; emit references found and update the carried sticky context.
     *
     * [isMusic] flags a segment the STT engine labelled as music. Sung lyrics quote scripture as
     * lyrics, not as references being looked up — so when [isMusic] (and [Config.suppressDuringMusic])
     * we skip detection entirely and leave the sticky context untouched, so a song can't seed or
     * hijack it. Every false positive in the real corpus came from sung/recited non-reference text.
     */
    fun process(
        text: String,
        sticky: Sticky,
        now: Long = System.currentTimeMillis(),
        isMusic: Boolean = false,
    ): List<Ref> {
        if (isMusic && Config.suppressDuringMusic) return emptyList()
        if (sticky.watchExpiresAt != 0L && now > sticky.watchExpiresAt) {
            sticky.watchBook = null
            sticky.watchChapter = null
        }
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val atoms = classify(tokens)
        return interpret(atoms, sticky, now)
    }

    /**
     * Dual-track entry point for the live pipeline: [transcript] (original language) and
     * [translation] (its live machine translation) describe the same utterance. Normally they are
     * concatenated so a citation spoken in either language is caught — but when the translation
     * *mistranslates* a book name into a different book than the transcript names (seen in
     * production: Russian "Первая книга царств" — 1 Samuel — machine-translated as "1 Kings"), blind
     * concatenation lets the translation's book win, since [interpret] treats the last book atom
     * seen as authoritative and translation tokens are scanned after transcript tokens. That
     * silently corrupts the sticky book for every later utterance that doesn't restate it.
     *
     * When the two tracks name different books, the translation is dropped entirely for this call
     * (not just its book atom — an MT that garbled the book name can't be trusted for numbers
     * either) and only the transcript is processed. When they agree, or only one names a book, the
     * tracks are concatenated as before — this is what lets a translation-only citation (transcript
     * names no book this utterance) still get picked up.
     */
    fun process(
        transcript: String,
        translation: String,
        sticky: Sticky,
        now: Long = System.currentTimeMillis(),
        isMusic: Boolean = false,
    ): List<Ref> {
        val transcriptBook = lastBookMentioned(transcript)
        val translationBook = lastBookMentioned(translation)
        val disagree = transcriptBook != null && translationBook != null && transcriptBook != translationBook
        val combined = if (disagree) transcript.trim() else "$transcript $translation".trim()
        return process(combined, sticky, now, isMusic)
    }

    /** The last book [text] names, or null if it names none. Read-only — does not touch sticky. */
    private fun lastBookMentioned(text: String): Int? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null
        return classify(tokens).filterIsInstance<Atom.Book>().lastOrNull()?.num
    }

    // ── tokenization ──────────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> {
        var s = text.lowercase().replace('–', '-').replace('—', '-')
            // ё→е is standard Russian orthography variance (verse/alias text may carry ё, STT
            // writes е) — folded unconditionally, unlike the STT-quirk э→е below.
            .replace('ё', 'е')
        // Gated STT spelling normalization: fold Cyrillic э→е so "эфесянам"→"ефесянам" resolves.
        if (Config.normalizeStt) s = s.replace('э', 'е')
        // strip digit-ordinal suffixes ("3-я" -> "3", "21-й" -> "21", "19-го" -> "19", "2nd" -> "2").
        // Use a Unicode-safe lookahead, not \b — Java's \b is ASCII-only and won't fire after Cyrillic.
        s = Regex("(\\d{1,3})-(?:я|й|е|го|му|м|ой|ом|ая|ое|ый|ий|ст|нд|рд|th|nd|st|rd)(?![\\p{L}])")
            .replace(s) { it.groupValues[1] }
        // make ":" and "-" (between alphanumerics) standalone separator tokens. A dot BETWEEN
        // digits ("Исайя 26.3", "Псалом 118.105") is the same citation notation as a colon —
        // normalize it to one BEFORE the general punctuation fold below turns it into a space
        // (which split chapter and verse into two bare numbers the prose guard then discarded).
        s = Regex("(?<=\\d)\\.(?=\\d)").replace(s, " : ")
        s = s.replace(":", " : ")
        s = Regex("(?<=[\\p{L}0-9])-(?=[\\p{L}0-9])").replace(s, " - ")
        // drop other punctuation (commas, dots, parens, quotes …) to whitespace
        s = s.replace(Regex("[^\\p{L}0-9:\\- ]"), " ")
        return s.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    // ── classification (greedy multi-token book lookup) ─────────────────────────

    private fun classify(tokens: List<String>): List<Atom> {
        val atoms = ArrayList<Atom>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            // Numbered-book disambiguation runs first: "1 Послание Иоанна" → 1 John (62), not the
            // Gospel (43); "Первая книга царств" → 1 Samuel (9), not left unresolved. The look-back
            // tokens (ordinal / «послание»/«книга» / «к») were already classified as Num/Filler, so
            // drop those atoms and emit the resolved book in their place.
            val numberedBook = resolveNumberedBookAt(tokens, i)
            if (numberedBook != null) {
                val (bookNum, back) = numberedBook
                repeat(back) { if (atoms.isNotEmpty()) atoms.removeAt(atoms.lastIndex) }
                atoms.add(Atom.Book(bookNum))
                i++
                continue
            }
            // The same citation with the ordinal AFTER the book: "Иоанн говорит в первом послании,
            // в первой главе шестом стихе" (S13 row 928). Only the look-back form was
            // handled, so this resolved to the Gospel and shipped as an explicit tier-1 — auto-go-live
            // with the wrong book, while the reverse lookup found the real 1 John 1:6 seconds later.
            val numberedBookAhead = resolveNumberedBookAhead(tokens, i)
            if (numberedBookAhead != null) {
                atoms.add(Atom.Book(numberedBookAhead))
                i++
                continue
            }

            // Greedy book match: try 3-, then 2-, then 1-token joins against the alias table.
            var matched = false
            // Set when the exact-alias branch below refuses this token for want of corroboration, so
            // the inflection-tolerant fallback can't quietly re-admit the very same word.
            var shortAliasRefused = false
            val maxJoin = minOf(3, tokens.size - i)
            for (len in maxJoin downTo 1) {
                val phrase = tokens.subList(i, i + len).joinToString(" ")
                val bookNum = BookResolver.ALIASES[phrase]
                // Skip ultra-short single-token aliases (≤2 chars: "so"→Zeph, "re"/"ap"→Rev, "ge",
                // "ex"…). They are typed-input abbreviations and fire constantly on spoken/translated
                // prose (the translation track flows through here too), hijacking the sticky book.
                // Multi-word aliases always carry a space (length ≥ 3) and are kept.
                if (bookNum != null && (len > 1 || phrase.length >= 3)) {
                    // Ambiguous common-word aliases (bare "иоанну"/"бытие"…) AND short real-word
                    // aliases ("job", "song", "при", "откр" — ≤ SHORT_ALIAS_MAX_LEN chars) need
                    // corroborating context — a chapter/verse digit nearby or an explicit citation
                    // marker — or they're just ordinary vocabulary/narration, not a book being
                    // named. A genuine spoken citation via a short alias essentially always has
                    // the number within reach ("Job chapter 3"), so recall survives the gate.
                    // A version name is not a citation: "New King James version" names the module the
                    // speaker is reading FROM. Real trace: it flipped the sticky book to James in the
                    // middle of Psalm 14 (S09).
                    if (len == 1 && isVersionNamePart(tokens, i)) break
                    if (len == 1 &&
                        (AMBIGUOUS_BOOK_FORMS[phrase] != null || isShortAlias(phrase, bookNum)) &&
                        !hasAmbiguousBookCorroboration(tokens, i)
                    ) {
                        shortAliasRefused = true
                        break
                    }
                    atoms.add(Atom.Book(bookNum))
                    i += len
                    matched = true
                    break
                }
                // Same join, but matching each word by stem — catches a multi-word name whose
                // halves are inflected differently than the alias table spells them ("Плача
                // Иеремия" for "плач иеремии"). Tried inside the same longest-first loop so a
                // 2-word stem hit still beats the 1-word exact alias for its second word, which
                // is the whole point: otherwise "иеремия" wins on its own and names Jeremiah.
                if (len > 1) {
                    val phraseBook = BookResolver.resolveStemPhrase(tokens.subList(i, i + len))
                    if (phraseBook != null) {
                        atoms.add(Atom.Book(phraseBook))
                        i += len
                        matched = true
                        break
                    }
                }
            }
            if (matched) continue

            val tok = tokens[i]
            // Inflection-tolerant single-token book match (Матфея, Даниила, Римлянам…), but not for
            // tokens that are numbers/keywords/separators.
            if (tok != ":" && tok != "-" && !isChapKw(tok) && !isVerseKw(tok) &&
                tok !in RANGE_WORDS && tok !in LIST_WORDS && !isNumberRatherThanBook(tok)
            ) {
                BookResolver.resolveStem(tok)?.let { match ->
                    // Over-extension gate: a token much longer than the alias stem it matched is
                    // usually ordinary vocabulary sharing a prefix ("открылся"→"откр",
                    // "повторить"→a Deuteronomy stem), not an inflected book name — real
                    // grammatical endings extend a stem by 1-2 chars (Матфея, Даниила). Beyond
                    // that, require the same corroboration ambiguous aliases need. The threshold
                    // matches StickyAudit's empirically-validated risky band (extension >= 3).
                    //
                    // Two ways a SHORT name used to slip past the corroboration gate, because a name
                    // of ≤ SHORT_ALIAS_MAX_LEN chars survives stemOf untouched and so matches itself
                    // here at extension 0:
                    //  - [shortAliasRefused]: the exact branch above just refused this very token for
                    //    want of corroboration, and this branch would re-admit it ("плач" → Lamentations
                    //    off ordinary grief vocabulary, sticky-log-S10.
                    //  - [BookResolver.isRegisteredOnlyStem]: the exact branch never sees names an SPB
                    //    module registered at startup (it reads the static table), so those reach only
                    //    this branch — ungated, however short or ordinary the word. The Russian Synodal
                    //    module names book 65 "Иуда" and book 28 "Осия", both everyday words: a sermon
                    //    illustration about Judas flipped the sticky book to Jude
                    //    (sticky-log-S12,, and "осия" held Hosea as the wrong
                    //    sticky book for ~40 minutes through a Psalm 14 passage (S09).
                    // Inflected forms are untouched — "Луки"/"Марка" resolve through the vetted static
                    // stems and stay unconditional.
                    val overExtended = tok.length - match.stem.length >= STEM_MAX_EXTENSION_UNCORROBORATED
                    val unvettedShortName = match.stem.length <= SHORT_ALIAS_MAX_LEN &&
                        BookResolver.isRegisteredOnlyStem(match.stem)
                    val needsCorroboration = overExtended || shortAliasRefused || unvettedShortName ||
                        AMBIGUOUS_BOOK_FORMS[tok] != null || AMBIGUOUS_BOOK_STEMS[match.stem] != null
                    val gated = needsCorroboration && !hasAmbiguousBookCorroboration(tokens, i)
                    if (!gated) { atoms.add(Atom.Book(match.bookNum)); i++; matched = true }
                }
            }
            if (matched) continue

            atoms.add(
                when {
                    tok == ":" -> Atom.Colon
                    tok == "-" -> Atom.Range
                    isChapKw(tok) -> Atom.ChapKw
                    isVerseKw(tok) -> Atom.VerseKw
                    tok in RANGE_WORDS -> Atom.Range
                    tok in LIST_WORDS -> Atom.ListSep
                    tok in FROM_WORDS -> Atom.From
                    tok in BARE_ONE -> Atom.Filler
                    else -> NumberWords.parseToken(tok)?.let { Atom.Num(it) } ?: Atom.Filler
                }
            )
            i++
        }
        return atoms
    }

    /**
     * True when [t] should be read as a number rather than offered to the book-stem resolver.
     *
     * `NumberWords.parseToken` matches a number stem plus any plausible grammatical ending, so a
     * book name that merely *begins* with one is swallowed whole: "Второзакония" — the standard
     * Russian title of Deuteronomy — is stem "втор" (2) followed by "озакония", whose leading
     * vowel is a valid ending, so it parsed as the number 2 and never reached [BookResolver].
     * Exact aliases escaped this because the greedy alias branch runs first; only the inflected
     * forms fell through, which is why "Второзаконие" worked and "Второзакония" did not.
     *
     * Resolve it the way any lexicon collision should be: longest match wins. "второзакония"
     * matches book stem "второзакон" (10) against number stem "втор" (4), so it is a book;
     * "второй" matches "втор" both ways (4 vs 4) and stays the number it is. Same shape as the
     * `NOT_NUMBERS` "семья"/"семь" fix, but as a rule instead of a list, so the next such book
     * name needs no new entry.
     */
    private fun isNumberRatherThanBook(t: String): Boolean {
        if (NumberWords.parseToken(t) == null) return false
        val bookStem = BookResolver.resolveStem(t)?.stem ?: return true
        return bookStem.length <= NumberWords.matchedStemLength(t)
    }

    private fun isChapKw(t: String): Boolean =
        (t.startsWith("глав") && t.substring(4) in CHAP_RU_ENDINGS) || CHAP_KW.any { t.startsWith(it) }
    private fun isVerseKw(t: String): Boolean =
        (t.startsWith("стих") && t.substring(4) in VERSE_RU_ENDINGS) || VERSE_KW.any { t.startsWith(it) }

    private fun isEpistleMarker(t: String): Boolean = EPISTLE_MARKER_STEMS.any { t.startsWith(it) }

    /**
     * If [tokens]`[i]` is a numbered-book name (John/Peter, or a Царств/Паралипоменон/Коринфянам/
     * Фессалоникийцам/Тимофею family member) preceded by a marker word («послание»/«письмо»/«книга»)
     * and/or an explicit ordinal (digit or word), returns the resolved canonical book number plus how
     * many preceding tokens the pattern consumed; else null.
     *
     *   "1 Послание Иоанна"       → (62, 2)  // 1 John, consuming «послание» + «1»
     *   "Послание к Петру"        → (60, 2)  // 1 Peter, consuming «к» + «послание»
     *   "Первое Иоанна"           → (62, 1)  // 1 John, consuming the word ordinal
     *   "Первая книга царств"     → (9, 2)   // 1 Samuel, consuming «книга» + the word ordinal
     *   "Третья царств"           → (11, 1)  // 1 Kings, consuming the word ordinal alone
     *
     * A bare "Иоанна"/"Петра" with no marker and no ordinal returns null (left to the alias table /
     * gospel reading). A bare "Коринфянам"/"Царств" with a marker but no ordinal ALSO returns null —
     * unlike John/Peter there's no unnumbered meaning to fall back to, but there's also no convention
     * that a bare mention means the 1st, so guessing would be wrong more often than right; this stays
     * the accepted "bare ambiguous numbered book" gap, not a case this function resolves.
     */
    // internal (not private): StickyAudit resolves the same way the live engine does, so a numbered
    // book ("во втором послании Коринфянам") isn't reported as an unexplained sticky jump.
    internal fun resolveNumberedBookAt(tokens: List<String>, i: Int): Pair<Int, Int>? {
        val spec = NUMBERED_BOOK_FORMS[tokens[i]] ?: return null
        var j = i - 1
        var back = 0
        // optional «к»/«ко» connector directly before the book ("послание к иоанну")
        if (j >= 0 && tokens[j] in EPISTLE_CONNECTORS) { j--; back++ }
        // optional marker/filler word («послание»/«письмо»/«книга»)
        var marker = false
        if (j >= 0 && isEpistleMarker(tokens[j])) { marker = true; j--; back++ }
        // optional ordinal (digit or word) selecting which numbered variant
        var ord: Int? = null
        if (j >= 0) {
            val n = NumberWords.parseToken(tokens[j])
            if (n != null && n in 1..spec.count) { ord = n; back++ }
        }
        // Require an explicit ordinal, unless the family conventionally defaults a bare marker to
        // the 1st (John/Peter only).
        if (ord == null && !(marker && spec.markerAloneDefaultsToFirst)) return null
        return (spec.base + (ord ?: 1) - 1) to back
    }

    /**
     * True when the token at [at] is a number that could plausibly be a chapter or verse, as opposed
     * to an ordinary count.
     *
     * A citation says the number in digits ("Psalm 14", "1 Коринфянам") or spells it right next to a
     * chapter/verse keyword ("Десятая глава", "chapter three"). A count phrase spells it and attaches
     * it to a noun — "Two songs", "Job's first trial", "Два пения" — which used to satisfy this gate
     * exactly like a real citation and was the longest-standing false-positive shape in the gap table.
     */
    private fun isCitationNumber(tokens: List<String>, at: Int): Boolean {
        val tok = tokens.getOrNull(at) ?: return false
        if (NumberWords.parseToken(tok) == null) return false
        if (tok.any { it.isDigit() }) return true
        // Spelled out: only a chapter/verse keyword on either side makes it a citation number.
        for (d in 1..2) {
            val after = tokens.getOrNull(at + d)
            val before = tokens.getOrNull(at - d)
            if (after != null && (isChapKw(after) || isVerseKw(after))) return true
            if (before != null && (isChapKw(before) || isVerseKw(before))) return true
        }
        return false
    }

    /**
     * True when this alias is short enough to double as ordinary vocabulary. English escapes a plain
     * length test through inflection the Russian stem index never sees: "song" and "job" are gated at
     * ≤ [SHORT_ALIAS_MAX_LEN], while "songs" and "job's" — the forms that actually occur in speech
     * ("Two songs", "Job's first trial") — are a character longer and sailed straight past. A plural
     * or possessive inherits the gate only when stripping it yields a short alias for the SAME book,
     * so "james" (→"jame", not an alias) and "acts" (→"act", not an alias) are untouched.
     */
    private fun isShortAlias(phrase: String, bookNum: Int): Boolean {
        if (phrase.length <= SHORT_ALIAS_MAX_LEN) return true
        val singular = phrase.removeSuffix("'s").removeSuffix("s")
        return singular != phrase && singular.length <= SHORT_ALIAS_MAX_LEN &&
            BookResolver.ALIASES[singular] == bookNum
    }

    /**
     * True when `tokens[i]` is part of a Bible *version* name rather than a citation — the one that
     * occurs in practice is "(New) King James (Version)", which contains a book name.
     */
    private fun isVersionNamePart(tokens: List<String>, i: Int): Boolean =
        i > 0 && tokens[i] == "james" && tokens[i - 1] == "king"

    /**
     * The mirror of [resolveNumberedBookAt] for speech that names the book and *then* qualifies it —
     * "Иоанн говорит в первом послании", "Пётр пишет во втором послании". Scans a short window after
     * `tokens[i]` for an epistle marker, taking any ordinal found on the way; ordinary prepositions
     * and the «к»/«ко» connector may sit between, anything else closes the window so a marker
     * belonging to a later, unrelated clause can't be claimed.
     *
     * Requires an explicit ordinal for families with no unnumbered meaning, exactly as the look-back
     * form does — a bare "Иоанн говорит в послании" still means 1 John only because John/Peter
     * conventionally default to the first.
     */
    private fun resolveNumberedBookAhead(tokens: List<String>, i: Int): Int? {
        val spec = NUMBERED_BOOK_FORMS[tokens[i]] ?: return null
        var ord: Int? = null
        var marker = false
        for (j in i + 1..minOf(i + AHEAD_WINDOW, tokens.lastIndex)) {
            val tok = tokens[j]
            if (isEpistleMarker(tok)) { marker = true; break }
            val n = NumberWords.parseToken(tok)
            if (n != null && n in 1..spec.count) { ord = n; continue }
            if (tok in EPISTLE_CONNECTORS || tok in AHEAD_FILLERS) continue
            return null
        }
        if (!marker) return null
        if (ord == null && !spec.markerAloneDefaultsToFirst) return null
        return spec.base + (ord ?: 1) - 1
    }

    /**
     * True when `tokens[i]` (one of [AMBIGUOUS_BOOK_FORMS]) has corroborating context: a
     * chapter/verse digit within 2 tokens either side, or an explicit book/epistle/gospel marker
     * noun within the preceding 2 tokens.
     */
    private fun hasAmbiguousBookCorroboration(tokens: List<String>, i: Int): Boolean {
        for (d in 1..2) {
            if (isCitationNumber(tokens, i + d)) return true
            // A number introduced by "с"/"from" opens a VERSE span inside the book already being
            // read ("Если мы прочитаем с 22 стиха…") — the same binding interpret()'s fromMark
            // enforces. It therefore cannot double as the chapter of a book named after it, so it
            // must not corroborate one: in that sermon that clause let the following
            // "Иеремия описывает" claim the sticky off Lamentations, and the drift produced the
            // wrong-book Jeremiah 18:18 two utterances later. A number reached by an ordinary
            // preposition ("в 3 главе Бытие") is untouched.
            if (isCitationNumber(tokens, i - d) && tokens.getOrNull(i - d - 1) !in FROM_WORDS) return true
        }
        for (d in 1..2) {
            val back = tokens.getOrNull(i - d) ?: continue
            if (isEpistleMarker(back) || back.startsWith("евангели")) return true
        }
        return false
    }

    // ── interpretation ──────────────────────────────────────────────────────────

    private fun interpret(atoms: List<Atom>, sticky: Sticky, now: Long): List<Ref> {
        val out = ArrayList<Ref>()

        var curBook: Int? = null
        var chapter: Int? = null
        var verseStart: Int? = null
        var verseEnd: Int? = null
        var keywordSeen = false
        var colonSeen = false
        var rangeArmed = false
        // How many numbers were already buffered when "с"/"from" arrived, or null if it hasn't.
        // Everything after it opens a VERSE span, so the bare "book N = chapter" convention may only
        // claim a number that PREDATES it: "Псалом 23. С первого стиха." still means 23:1, while the
        // machine translation "From the first verse." (no chapter number at all, the book named in an
        // earlier sentence of the same accumulated text) must not turn the verse ordinal into a
        // chapter — that primed the sticky to Psalm 1 mid-Psalm-23 and emitted 19:1:1 as a tier-2
        // continuation, which auto-goes-live.
        var fromMark: Int? = null
        // Keyword-first binding: a chapter/verse keyword arriving BEFORE its number
        // ("глава 26 стих 3", "Job chapter 3 verse 2") marks the NEXT number as bound. Without
        // this, keywords only consumed numbers that preceded them and keyword-first citations
        // parsed inverted (chapter/verse swapped) in every language.
        var pendingChapKw = false
        var pendingVerseKw = false
        // numbers buffered since the last keyword/colon, each tagged whether a range preceded it
        val recent = ArrayList<Pair<Int, Boolean>>()

        fun assignChapterFromRecent() {
            if (chapter == null && recent.isNotEmpty()) chapter = recent.first().first
            recent.clear()
        }
        fun assignVersesFromRecent() {
            for ((v, ranged) in recent) {
                if (verseStart == null) verseStart = v
                else if (ranged && verseEnd == null) verseEnd = v
            }
            recent.clear()
        }
        fun flush() {
            // leftover buffered numbers with no trailing keyword
            if (recent.isNotEmpty()) {
                // The bare "book N" convention only applies while nothing has claimed a verse yet.
                // Once verseStart is bound, a leftover number is the rest of THAT reference — the end
                // of a range, or another verse in a list — never a chapter. Real trace: "стихи 3-го
                // стиха по 6-й" (S13 seg 797, announcing Psalm 24:3-6 against a live
                // sticky) bound verseStart=3 through the verse keyword, then this fallback promoted
                // the range end 6 to chapter, emitting against a chapter the speaker never named and
                // dropping the operator's verses 4-6 on the floor.
                if (chapter == null && verseStart == null) {
                    chapter = recent.first().first
                    if (recent.size >= 2) {
                        verseStart = recent[1].first
                        if (recent.size >= 3 && recent[2].second) verseEnd = recent[2].first
                    }
                } else {
                    assignVersesFromRecent()
                }
                recent.clear()
            }
            emit(curBook, chapter, verseStart, verseEnd, keywordSeen, sticky, now, out)
            chapter = null; verseStart = null; verseEnd = null; keywordSeen = false; colonSeen = false; rangeArmed = false
            fromMark = null
            pendingChapKw = false; pendingVerseKw = false
        }

        for (a in atoms) {
            when (a) {
                is Atom.Book -> {
                    // Normally a book starts a fresh reference (flush whatever preceded it). When
                    // inferBookAtEnd is on and no book has been named yet in this segment but
                    // chapter/verse numbers already have, the book was spoken *after* its numbers
                    // ("14 стих 3 главы … Матфея") — attach it to them instead of flushing.
                    val attachToPending = Config.inferBookAtEnd && curBook == null &&
                        (chapter != null || verseStart != null || recent.isNotEmpty())
                    if (attachToPending) curBook = a.num
                    else { flush(); curBook = a.num }
                }
                is Atom.ChapKw -> {
                    if (recent.isNotEmpty()) assignChapterFromRecent() else pendingChapKw = true
                    keywordSeen = true
                }
                is Atom.VerseKw -> {
                    when {
                        // "Book N, verse M" with no chapter keyword for N ("Psalm 10, verse 13") —
                        // same bare "book number = chapter" convention flush()'s own leftover
                        // fallback already applies. Without this, N gets swept into verseStart below
                        // and flush()'s leftover fallback wrongly promotes the real verse number M
                        // into chapter (parsed as ch=?, v=10 instead of ch=10, v=13).
                        // Only the FIRST buffered number is the chapter. Russian says the verse number
                        // before its keyword ("Псалом 23, 1 стих"), so anything after the chapter is
                        // the verse this keyword names — discarding it, as an unconditional
                        // assignChapterFromRecent() does, silently drops a verse the speaker said and
                        // leaves the reference chapter-only.
                        chapter == null && curBook != null && recent.isNotEmpty() &&
                            (fromMark == null || fromMark!! > 0) -> {
                            chapter = recent.first().first
                            val rest = recent.drop(1)
                            recent.clear()
                            recent.addAll(rest)
                            if (recent.isNotEmpty()) assignVersesFromRecent() else pendingVerseKw = true
                        }
                        recent.isNotEmpty() -> assignVersesFromRecent()
                        else -> pendingVerseKw = true
                    }
                    keywordSeen = true
                }
                is Atom.Colon -> { assignChapterFromRecent(); colonSeen = true }
                is Atom.Range -> { rangeArmed = true }
                is Atom.From -> { if (fromMark == null) fromMark = recent.size }
                is Atom.ListSep -> { /* keep recent; lists don't form ranges */ }
                is Atom.Num -> when {
                    pendingChapKw -> { chapter = a.value; pendingChapKw = false; rangeArmed = false }
                    pendingVerseKw && verseStart == null -> { verseStart = a.value; pendingVerseKw = false; rangeArmed = false }
                    else -> { recent.add(a.value to rangeArmed); rangeArmed = false }
                }
                is Atom.Filler -> {
                    // A colon already bound these numbers to the reference ("Исайя 26:3 написано
                    // «…»", "Isaiah 26:3 says …") — trailing prose must not wipe the buffered
                    // verse. Without a colon, numbers followed by prose are likely counts
                    // ("Марк 5 человек") — drop as before. Deliberately colon-only, NOT
                    // keywordSeen: "Матфея 3 глава … 5 причин" must not turn 5 into a verse.
                    if (colonSeen && recent.isNotEmpty()) assignVersesFromRecent() else recent.clear()
                    // A pending keyword must not bind across prose either ("глава, друзья, 26").
                    pendingChapKw = false
                    pendingVerseKw = false
                    rangeArmed = false
                }
            }
        }
        flush()
        return out
    }

    private fun emit(
        curBook: Int?,
        chapter: Int?,
        verseStart: Int?,
        verseEnd: Int?,
        keywordSeen: Boolean,
        sticky: Sticky,
        now: Long,
        out: MutableList<Ref>,
    ) {
        if (curBook != null) {
            // A book was named — make it sticky even if no chapter followed yet, so a chapter/verse
            // in a *later* utterance ("…к римлянам." → "Десятая глава…") still attaches to it. A
            // genuinely NEW book (different from what was already sticky) always resets the carried
            // chapter so a stale one can't bind to it — but the SAME book merely mentioned again
            // within this call (trailing after its own chapter number in RU's "N глава [Книги]" word
            // order, or re-named on the other bilingual track after the combined string already
            // resolved a chapter) must not wipe a chapter this call already established: an absent
            // chapter on THIS mention just means "nothing new was said," not "the chapter is unknown
            // again." Snapshot both values before mutating — sticky.watchChapter is read and written
            // in this same branch.
            val sameBook = curBook == sticky.watchBook
            val resolvedChapter = chapter ?: sticky.watchChapter.takeIf { sameBook }
            sticky.watchBook = curBook
            sticky.watchChapter = resolvedChapter
            sticky.watchExpiresAt = now + Config.stickyTtlMs
            val ch = resolvedChapter ?: return
            // No verse named yet (book + chapter only) — the sticky context above is primed for a
            // later bare "N стих", but there is no evidence of which verse to show, so emit nothing
            // rather than guessing verse 1.
            if (verseStart == null) return
            out.add(Ref(curBook, ch, verseStart, normEnd(verseStart, verseEnd), 1))
            return
        }
        // No book in this utterance → sticky resolution, but only when a keyword anchored it
        // (a bare number with no глава/стих is too risky to bind to stale context).
        if (!keywordSeen) return
        val book = sticky.watchBook ?: return
        val ch = chapter ?: sticky.watchChapter ?: return
        if (chapter == null && verseStart == null) return // nothing new
        sticky.watchChapter = ch
        sticky.watchExpiresAt = now + Config.stickyTtlMs
        // A new bare chapter arrived via the sticky book, but no verse yet — same situation as the
        // book+chapter branch above: prime silently and wait for a real verse rather than guessing 1.
        if (verseStart == null) return
        out.add(Ref(book, ch, verseStart, normEnd(verseStart, verseEnd), 2))
    }

    private fun normEnd(start: Int?, end: Int?): Int? =
        if (start != null && end != null && end > start) end else null
}
