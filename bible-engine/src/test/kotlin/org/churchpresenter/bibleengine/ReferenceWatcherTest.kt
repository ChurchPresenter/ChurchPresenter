package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.detection.BookResolver
import org.churchpresenter.bibleengine.detection.ReferenceWatcher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceWatcherTest {

    private class TestSticky : ReferenceWatcher.Sticky {
        override var watchBook: Int? = null
        override var watchChapter: Int? = null
        override var watchExpiresAt: Long = 0L
    }

    /**
     * A sticky pre-seeded to a live context.
     *
     * Writing the fields by hand at each call site is how a test ends up silently seeding only the
     * book and then asserting about the chapter.
     */
    private fun sticky(book: Int? = null, chapter: Int? = null, expiresAt: Long = 0L) =
        TestSticky().also { it.watchBook = book; it.watchChapter = chapter; it.watchExpiresAt = expiresAt }

    /** Feeds utterances in order through one sticky context; returns all refs emitted. */
    private fun run(vararg utterances: String, now: Long = 1_000L): List<ReferenceWatcher.Ref> =
        feed(TestSticky(), *utterances, now = now)

    /** Feeds utterances through an existing [s], returning everything emitted across them. */
    private fun feed(s: TestSticky, vararg utterances: String, now: Long = 1_000L): List<ReferenceWatcher.Ref> {
        val all = mutableListOf<ReferenceWatcher.Ref>()
        for (u in utterances) all += ReferenceWatcher.process(u, s, now)
        return all
    }

    /**
     * Bilingual sibling of [run] — each turn is (transcript, translation), the pair the live
     * pipeline hands to `process`. Without this every two-track test hand-rolls its own sticky.
     */
    private fun runBi(vararg turns: Pair<String, String>, now: Long = 1_000L): List<ReferenceWatcher.Ref> =
        feedBi(TestSticky(), *turns, now = now)

    private fun feedBi(
        s: TestSticky,
        vararg turns: Pair<String, String>,
        now: Long = 1_000L,
    ): List<ReferenceWatcher.Ref> {
        val all = mutableListOf<ReferenceWatcher.Ref>()
        for ((ru, en) in turns) all += ReferenceWatcher.process(ru, en, s, now)
        return all
    }

    private fun ReferenceWatcher.Ref.triple() = Triple(bookNum, chapter, verseStart)

    // ── Synthetic test input ────────────────────────────────────────────────────
    //
    // Test inputs are the SHORTEST phrasing that still reaches the gate under test, never a verbatim
    // sermon sentence: a one-off utterance pins behaviour to that utterance instead of to the
    // pattern, and it will never recur. Each de-verbatimized test carries a `GATE:` tag naming the
    // mechanism and a `TRACE:` tag with the session id — the recorded text lives in SESSIONS.md.
    //
    // Filler must stay free of digits, epistle markers and book aliases, or it would supply
    // corroboration by accident and the negative tests would pass for the wrong reason. That
    // invariant is asserted by `neutral filler never supplies corroboration` below, so building
    // input with [bare]/[cited] enforces it structurally instead of by eyeball.

    private val NEUTRAL_RU = listOf(
        "сегодня", "хорошо", "конечно", "братья", "сестры", "давайте", "подумаем", "вместе",
        "немного", "время", "место", "слово", "жизнь", "сердце", "истина", "путь", "человек",
        "земля", "небо", "вода",
    )

    private val NEUTRAL_EN = listOf(
        "today", "well", "surely", "together", "little", "time", "place", "word",
        "life", "heart", "truth", "way", "person", "earth", "sky", "water",
    )

    /** [word] surrounded by neutral filler and nothing else — no digit, no marker, no keyword. */
    private fun bare(word: String, before: Int = 1, after: Int = 1): String =
        (NEUTRAL_RU.take(before) + word + NEUTRAL_RU.takeLast(after)).joinToString(" ") + "."

    private fun bareEn(word: String, before: Int = 1, after: Int = 1): String =
        (NEUTRAL_EN.take(before) + word + NEUTRAL_EN.takeLast(after)).joinToString(" ") + "."

    /** [word] in a minimal explicit citation — the shortest form that corroborates a book name. */
    private fun cited(word: String, chapter: Int = 3, verse: Int = 5): String =
        "$word $chapter глава $verse стих."

    private fun assertResolves(text: String, book: Int, chapter: Int, verse: Int, tier: Int? = null) {
        val refs = run(text)
        assertTrue(
            refs.any { it.triple() == Triple(book, chapter, verse) },
            "\"$text\" should resolve to $book $chapter:$verse, got $refs"
        )
        if (tier != null) {
            assertEquals(tier, refs.first { it.triple() == Triple(book, chapter, verse) }.tier)
        }
    }

    /** The dominant shape of every precision gate: the sticky must not move. */
    private fun assertStickyHolds(book: Int, chapter: Int?, vararg utterances: String) {
        val s = sticky(book, chapter)
        val refs = feed(s, *utterances)
        assertEquals(book, s.watchBook, "sticky book moved on ${utterances.toList()} (emitted $refs)")
        assertEquals(chapter, s.watchChapter, "sticky chapter moved on ${utterances.toList()} (emitted $refs)")
    }

    @Test fun `neutral filler never supplies corroboration`() {
        // Guards [bare]/[cited]: if a filler word ever gains a digit, a marker stem or a book alias,
        // every negative test built on it would start passing for the wrong reason.
        for (w in NEUTRAL_RU + NEUTRAL_EN) {
            assertTrue(w.none { it.isDigit() }, "filler \"$w\" contains a digit")
            assertNull(BookResolver.ALIASES[w], "filler \"$w\" is a book alias")
            assertNull(BookResolver.resolveStem(w), "filler \"$w\" stems to a book")
        }
        assertTrue(run(bare("сегодня")).isEmpty(), "filler-only text must never emit")
    }

    // ── Single-utterance explicit references (from real transcripts) ────────────

    @Test fun `Matthew 28 18 with keywords`() {
        val r = run("Евангелие от Матфея, 28 глава, 18 стиха.").single()
        assertEquals(Triple(40, 28, 18), r.triple())
        assertEquals(1, r.tier)
    }

    @Test fun `inflected book without от`() {
        val r = run("Евангелие Матфея, 28 глава, 19 стих").single()
        assertEquals(Triple(40, 28, 19), r.triple())
    }

    @Test fun `digit ordinal book chapter verse`() {
        // "1 Петра 3-я глава 21-й стих" — exercises digit-ordinal stripping (3-я→3, 21-й→21)
        val r = run("1 Петра 3-я глава 21-й стих.")
        assertTrue(r.any { it.triple() == Triple(60, 3, 21) }, "expected 1 Peter 3:21, got $r")
    }

    @Test fun `range with по`() {
        val r = run("Евангелие от Марка, 16 глава, 15 по 16 стих.").single()
        assertEquals(Triple(41, 16, 15), r.triple())
        assertEquals(16, r.verseEnd)
    }

    @Test fun `hyphen range`() {
        val r = run("Деяния 2 глава 37-38 стихи").single()
        assertEquals(Triple(44, 2, 37), r.triple())
        assertEquals(38, r.verseEnd)
    }

    @Test fun `с N по M range`() {
        val r = run("Деяние 16 глава с 30 по 31 стих").single()
        assertEquals(Triple(44, 16, 30), r.triple())
        assertEquals(31, r.verseEnd)
    }

    // ── Word-number ordinals ────────────────────────────────────────────────────

    @Test fun `chapter and verse as ordinal words`() {
        // "Десятая глава, девятый-десятой стихи" → 10:9-10 (sticky book from previous line)
        val r = run("послание к римлянам.", "Десятая глава, девятый-десятой стихи.")
        assertTrue(r.any { it.bookNum == 45 && it.chapter == 10 && it.verseStart == 9 && it.verseEnd == 10 },
            "expected Romans 10:9-10, got $r")
    }

    // ── Sticky verse-by-verse reading (the Daniel 6 chain) ──────────────────────

    @Test fun `verse by verse reading keeps book and chapter`() {
        val refs = run(
            "Место, которое записано в книге пророка Даниила.",
            "Мы с вами прочитаем всю 6 главу.",
            "6 стих.",
            "8 стих.",
            "Семнадцатый стих и принесен был камень.",
            "23 стих.",
        )
        val verses = refs.filter { it.bookNum == 27 && it.chapter == 6 }.mapNotNull { it.verseStart }
        assertTrue(verses.containsAll(listOf(6, 8, 17, 23)), "expected Daniel 6:6/8/17/23, got $refs")
    }

    // ── Split across utterances ─────────────────────────────────────────────────

    @Test fun `book then chapter verse next utterance`() {
        val r = run("...Павла к римлянам, 6 глава, несколько стихов.", "Четвертый стих.")
        assertTrue(r.any { it.bookNum == 45 && it.chapter == 6 && it.verseStart == 4 },
            "expected Romans 6:4, got $r")
    }

    @Test fun `book plus chapter with no verse yet does not emit, but still primes the sticky`() {
        // The speaker announces the book+chapter, then goes on a tangent before reading the verse —
        // nothing should appear on screen (no fabricated "verse 1"), but the sticky context must
        // still be primed so the eventual bare verse resolves against it.
        val sticky = TestSticky()
        val announced = ReferenceWatcher.process("1 Коринфянам, 11 глава.", sticky, now = 1_000L)
        assertTrue(announced.isEmpty(), "book+chapter with no verse must not emit, got $announced")
        assertEquals(46, sticky.watchBook)
        assertEquals(11, sticky.watchChapter)

        val afterTangent = ReferenceWatcher.process("23 стих.", sticky, now = 1_000L)
        assertTrue(afterTangent.any { it.triple() == Triple(46, 11, 23) },
            "expected 1 Corinthians 11:23 once the verse is read, got $afterTangent")
    }

    // ── Precision (must NOT emit) ───────────────────────────────────────────────

    @Test fun `bare number in prose does not emit`() {
        assertTrue(run("У меня было три причины так поступить.").isEmpty())
        assertTrue(run("Прошло двадцать лет с того дня.").isEmpty())
    }

    @Test fun `book plus number plus noun does not emit`() {
        // "Марк 5 человек" — a name + count, not Mark 5
        assertTrue(run("Марк 5 человек пришли сегодня.").isEmpty())
    }

    @Test fun `stale sticky expires`() {
        val sticky = TestSticky()
        ReferenceWatcher.process("книга пророка Даниила, 6 глава.", sticky, now = 1_000L)
        // Much later, a bare verse with no book should NOT bind to the expired Daniel context.
        val late = ReferenceWatcher.process("18 стих.", sticky, now = 1_000L + 10 * 60_000L)
        assertTrue(late.isEmpty(), "stale context should have expired, got $late")
    }

    // ── Precision negatives: стих*/глав* look-alikes ────────────────────────────
    // Look-alike words and bare verse/chapter words with no real number must NOT emit,
    // both standalone and when a live sticky context is present (so they can't bind to it).

    /** Feeds a live reference first, then [line]; returns only the refs [line] itself emits. */
    private fun withSticky(line: String): List<ReferenceWatcher.Ref> {
        val sticky = TestSticky()
        ReferenceWatcher.process("Послание к римлянам, 6 глава, 4 стих.", sticky, 1_000L)
        return ReferenceWatcher.process(line, sticky, 1_000L)
    }

    private fun assertNoEmit(line: String) {
        assertTrue(run(line).isEmpty(), "standalone should not emit: \"$line\" → ${run(line)}")
        assertTrue(withSticky(line).isEmpty(), "should not bind to sticky: \"$line\" → ${withSticky(line)}")
    }

    @Test fun `стихотворение look-alike does not emit`() {
        assertNoEmit("звучали стихотворения.")
        assertNoEmit("молитвы, стихотворение, слово.")
        assertNoEmit("за все стихотворения.")
    }

    @Test fun `главное look-alike does not emit`() {
        assertNoEmit("это самое главное.")
    }

    @Test fun `глава семьи with no real number does not emit`() {
        // GATE: NumberWords.NOT_NUMBERS — "семьи" (family) collides with the stem "семь" (7).
        //
        // Asserting only "nothing was emitted" does NOT guard this: with the entry removed, "глава
        // семьи" reads as chapter 7, which has no verse and so emits nothing either — the test
        // stayed green with NOT_NUMBERS emptied. The sticky is where the difference shows.
        assertNoEmit("глава семьи жертвовать собой.")
        assertNoEmit("глава семьи")
        val sticky = sticky(book = 45)
        feed(sticky, "глава семьи жертвовать собой.")
        assertNull(sticky.watchChapter, "\"семьи\" must not be read as the number 7")
    }

    @Test fun `bare plural стихи with no number does not emit`() {
        assertNoEmit("читать стихи, рисовать.")
    }

    @Test fun `bare один стих and этот стих do not emit`() {
        assertNoEmit("хочу прочитать один стих.")
        assertNoEmit("этот стих был чужой.")
    }

    // ── Epistle (ordinal) disambiguation — 1/2/3 John, 1/2 Peter (an earlier study §2) ──────────

    @Test fun `Послание Иоанна resolves to the epistle not the gospel`() {
        // "1 Послание Иоанна, 4 глава, 3 стиха" → 1 John 4:3 (62), NOT John 4:3 (43).
        val r = run("1 Послание Иоанна, 4 глава, 3 стиха.").single()
        assertEquals(Triple(62, 4, 3), r.triple())
    }

    @Test fun `bare Послание Иоанна defaults to 1 John`() {
        val r = run("Послание Иоанна, 4 глава, 15 стих.").single()
        assertEquals(Triple(62, 4, 15), r.triple())
    }

    @Test fun `2-е Послание Иоанна selects the second epistle`() {
        val r = run("2 Послание Иоанна, 1 глава, 6 стих.").single()
        assertEquals(Triple(63, 1, 6), r.triple())
    }

    @Test fun `digit-adjacent 1 Иоанна locks canonical id 62`() {
        // "1-е Иоанна 4:15" — digit adjacency already joins via the alias; lock the canonical id so
        // the CP-side positional map (62 → 1 John index 61) has a trustworthy ground truth.
        val r = run("1-е Иоанна 4:15.").single()
        assertEquals(62, r.bookNum)
    }

    @Test fun `Первое Иоанна word-ordinal resolves to the epistle`() {
        val r = run("Первое Иоанна, 4 глава, 15 стих.").single()
        assertEquals(Triple(62, 4, 15), r.triple())
    }

    @Test fun `Послание Петра resolves to 1 Peter`() {
        val r = run("Послание Петра, 1 глава, 3 стих.").single()
        assertEquals(Triple(60, 1, 3), r.triple())
    }

    @Test fun `Второе Петра word-ordinal selects 2 Peter`() {
        val r = run("Второе Петра, 1 глава, 3 стих.").single()
        assertEquals(Triple(61, 1, 3), r.triple())
    }

    @Test fun `gospel of John is preserved without an epistle marker`() {
        // "Евангелие от Иоанна, 3 глава, 16 стих" must stay the Gospel (43), not become an epistle.
        val r = run("Евангелие от Иоанна, 3 глава, 16 стих.").single()
        assertEquals(Triple(43, 3, 16), r.triple())
    }

    @Test fun `bare Иоанна with no marker stays the gospel`() {
        val r = run("Иоанна, 3 глава, 16 стих.").single()
        assertEquals(Triple(43, 3, 16), r.triple())
    }

    @Test fun `engine canonical names confirm the 1 John mapping (study §1 ground truth)`() {
        // Closes the CP-side loop: the engine emits canonical id 62 and 62 is "1 John" in canonical
        // order, so CP's positional map (index = id - 1 = 61) lands on 1 John.
        assertEquals("1 John", BookResolver.canonicalName(62))
        assertEquals("James", BookResolver.canonicalName(59))
        assertEquals("2 Corinthians", BookResolver.canonicalName(47))
    }

    // ── Numbered-book (ordinal) disambiguation beyond John/Peter ────────────────

    @Test fun `Первая книга царств resolves to 1 Samuel, not left unresolved`() {
        // The real trigger from an earlier session log: spelled ordinal + "книга" + stem, with
        // no digit anywhere — the abbreviated "1-я царств" alias never fires here.
        val r = run("Первая книга царств, 15 глава, с 22 по 30 стих.").single()
        assertEquals(Triple(9, 15, 22), r.triple())
        assertEquals(30, r.verseEnd)
    }

    @Test fun `Первое Коринфянам resolves to 1 Corinthians with no книга filler`() {
        val r = run("Первое Коринфянам, 11 глава, 23 стих.").single()
        assertEquals(Triple(46, 11, 23), r.triple())
    }

    @Test fun `Третья царств resolves to 1 Kings (Synodal 3rd Kingdoms) and primes the sticky`() {
        val sticky = TestSticky()
        val refs = ReferenceWatcher.process("Третья царств, 4 глава.", sticky, now = 1_000L)
        assertTrue(refs.isEmpty(), "book+chapter with no verse must not emit, got $refs")
        assertEquals(11, sticky.watchBook)
        assertEquals(4, sticky.watchChapter)
    }

    @Test fun `book, chapter, and verse each resolve independently across separate utterances`() {
        // Real speech rarely announces a full reference as one clean phrase — book, chapter, and
        // verse are usually spoken as separate utterances, often with a tangent in between. Neither
        // of the first two utterances should show anything on screen (no book/chapter alone, and no
        // fabricated verse 1 for the chapter-only continuation) — only the third, once a real verse
        // is finally spoken, should resolve.
        val sticky = TestSticky()
        val bookOnly = ReferenceWatcher.process("Первая книга царств.", sticky, now = 1_000L)
        assertTrue(bookOnly.isEmpty(), "book alone must not emit, got $bookOnly")
        assertEquals(9, sticky.watchBook)

        val chapterOnly = ReferenceWatcher.process("15 глава.", sticky, now = 1_000L)
        assertTrue(chapterOnly.isEmpty(), "chapter-only continuation must not emit a fabricated verse, got $chapterOnly")
        assertEquals(15, sticky.watchChapter)

        val verseOnly = ReferenceWatcher.process("22 стих.", sticky, now = 1_000L)
        assertTrue(verseOnly.any { it.triple() == Triple(9, 15, 22) },
            "expected 1 Samuel 15:22 once the verse is finally spoken, got $verseOnly")
    }

    @Test fun `bare numbered book with a marker but no ordinal stays unresolved`() {
        // Unlike John/Peter, «книга царств» / bare «Коринфянам» has no default "means the 1st"
        // convention — resolving here would guess wrong as often as right, so it must stay silent
        // (the existing, accepted "bare ambiguous numbered book" gap). Checking the sticky directly
        // (not just refs.isEmpty()) matters: book+chapter-with-no-verse never emits a Ref regardless
        // of which book resolved (see the "primes the sticky" tests above), so an empty refs list
        // alone wouldn't catch a regression where "царств"/"коринфянам" wrongly resolved anyway.
        val sticky1 = TestSticky()
        val refs1 = ReferenceWatcher.process("Книга царств, 15 глава.", sticky1, now = 1_000L)
        assertTrue(refs1.isEmpty())
        assertEquals(null, sticky1.watchBook, "bare «книга царств» must stay unresolved, not default to 1 Samuel")

        val sticky2 = TestSticky()
        val refs2 = ReferenceWatcher.process("Коринфянам, 11 глава.", sticky2, now = 1_000L)
        assertTrue(refs2.isEmpty())
        assertEquals(null, sticky2.watchBook, "bare «Коринфянам» must stay unresolved, not default to 1 Corinthians")
    }

    // ── Same-book re-mention must not clobber a just-set sticky chapter (an earlier study) ────

    @Test fun `trailing book mention after its own chapter number does not wipe the chapter just set`() {
        // GATE: emit — a same-book re-mention carrying no chapter of its own must not undo the
        //       chapter the same call just set (ReferenceWatcher.emit, `resolvedChapter`).
        // TRACE: S07 — RU's "N главе <Книга-genitive>" word order names the chapter
        //        BEFORE the book, so the book atom's end-of-segment flush nulled chapter 21.
        val sticky = sticky(book = 66, chapter = 20)
        val refs = feed(sticky, "в 21 главе Откровения.")
        assertTrue(refs.isEmpty(), "book+chapter with no verse must not emit, got $refs")
        assertEquals(66, sticky.watchBook)
        assertEquals(21, sticky.watchChapter, "trailing same-book re-mention must not wipe the chapter just set")
    }

    @Test fun `bilingual track's own book re-mention does not wipe a chapter set earlier in the same call`() {
        // GATE: emit — as above, but reached through the EN track's keyword-before-number order,
        //       whose "in" fillers wipe the buffered numbers before the segment-end flush.
        // TRACE: S07 — prevChapter=12 → newChapter=null. Kept bilingual: the gate has
        //        to hold on the transcript+translation concatenation, which no single-track test
        //        exercises.
        val sticky = TestSticky()
        val refs = feedBi(
            sticky,
            "в книге Бытия, в 12 главе, в 5 стихе." to
                "in the book of Genesis, in chapter 12, in verse 5.",
        )
        assertTrue(refs.any { it.triple() == Triple(1, 12, 5) }, "expected Genesis 1 12:5 to have been emitted, got $refs")
        assertEquals(1, sticky.watchBook)
        assertEquals(12, sticky.watchChapter, "EN track's own re-mention of the same book must not wipe chapter 12")
    }

    @Test fun `genuinely different book still resets a stale carried chapter`() {
        // Guards the original behavior the buggy branch was defending: a real book change must still
        // drop a stale chapter rather than let it bind to the new book.
        val sticky = TestSticky()
        sticky.watchBook = 27
        sticky.watchChapter = 6
        val refs = ReferenceWatcher.process("Послание к римлянам.", sticky, now = 1_000L)
        assertTrue(refs.isEmpty())
        assertEquals(45, sticky.watchBook, "book must switch to Romans")
        assertEquals(null, sticky.watchChapter, "a genuinely new book must still drop the stale chapter")
    }

    // ── Ambiguous common-word RU aliases need corroborating context (an earlier study) ─────────

    @Test fun `bare dative Иоанну narrating the apostle does not hijack the sticky book`() {
        // GATE: classify/hasAmbiguousBookCorroboration — "иоанну" is the dative of the man's name;
        //       only a nearby digit or citation marker makes it the Gospel (AMBIGUOUS_BOOK_FORMS).
        // TRACE: S07 — two false flips, 66→43 and 25→43, narrating the apostle.
        //        Both collapse to the same shape, so one bare-word case covers them.
        assertStickyHolds(66, 21, bare("иоанну"))
        assertStickyHolds(25, 3, bare("иоанну", before = 2, after = 2))
    }

    @Test fun `бытие as ordinary vocabulary does not hijack the sticky book`() {
        // GATE: same — "бытие" means "being/existence" at least as often as it names Genesis.
        // TRACE: S07 — 43→1 false flip off a machine-translation tail.
        assertStickyHolds(43, 3, bare("бытие"))
    }

    @Test fun `digit-adjacent бытие still resolves as Genesis`() {
        val r = run("Бытие, 1 глава, 1 стих.").single()
        assertEquals(Triple(1, 1, 1), r.triple())
    }

    @Test fun `digit-adjacent dative Иоанну still resolves when corroborated`() {
        val r = run("Иоанну, 3 глава, 16 стих.").single()
        assertEquals(Triple(43, 3, 16), r.triple())
    }

    @Test fun `marker-adjacent быт abbreviation still resolves as Genesis`() {
        val sticky = TestSticky()
        val refs = ReferenceWatcher.process("Книга Быт, 3 глава.", sticky, now = 1_000L)
        assertTrue(refs.isEmpty())
        assertEquals(1, sticky.watchBook, "«книга» marker should corroborate the abbreviation «Быт»")
    }

    // ── Mechanism-level generalization (an earlier study §3) — test the underlying rule across
    // many books/words instead of only the specific transcripts that first exposed it, so the next
    // word/book that falls into the same trap is caught before a live session hits it. ──────────

    @Test fun `same-book reflush preserves chapter across a range of books (general invariant)`() {
        // Not just Revelation/Genesis (the two real traces) — the same "book named again, trailing
        // its own chapter number, with no chapter of its own" shape must hold for any book.
        val cases = listOf(
            Triple(66, "Откровения", 21),
            Triple(1, "Бытия", 12),
            Triple(45, "Римлянам", 8),
            Triple(40, "Матфея", 5),
        )
        for ((book, genitive, chapter) in cases) {
            val sticky = TestSticky()
            sticky.watchBook = book
            sticky.watchChapter = 1 // a different "old" chapter, distinct from the one being set
            val refs = ReferenceWatcher.process("Мы читаем об этом в $chapter главе $genitive.", sticky, now = 1_000L)
            assertTrue(refs.isEmpty(), "book+chapter with no verse must not emit for book $book, got $refs")
            assertEquals(book, sticky.watchBook, "book should stay $book")
            assertEquals(chapter, sticky.watchChapter,
                "chapter $chapter must survive the trailing same-book re-mention for book $book")
        }
    }

    @Test fun `every recorded same-book chapter-clear shape stays fixed`() {
        // GATE: emit — the same reflush as the invariant above, in the three SHAPES that invariant
        //       does not generate. Kept as a table for that reason, not because they are recorded.
        // TRACE: S07 — its four CHAPTER-CLEARED rows collapse to three shapes; the
        //        fourth repeated the first. That log is a frozen pre-fix artifact, so re-auditing it
        //        can never show the fix working — these assertions are what prove it.
        val cases = listOf(
            // book named twice in one breath, then its own chapter and verse
            Triple(42, 9, "Евангелие от Луки, откройте Евангелие от Луки на 9 главе. 9 глава, 51 стих."),
            // oblique-case re-mention carrying a bare ordinal and no chapter keyword
            Triple(20, 3, "и в притчах мы читаем 3-й."),
            // re-mention with no number at all
            Triple(1, 12, "и вот в книге Бытие."),
        )
        for ((book, chapter, text) in cases) {
            val sticky = TestSticky()
            sticky.watchBook = book
            sticky.watchChapter = chapter
            ReferenceWatcher.process(text, sticky, now = 1_000L)
            assertEquals(book, sticky.watchBook, "book $book must survive: \"$text\"")
            assertEquals(chapter, sticky.watchChapter, "chapter $chapter must survive: \"$text\"")
        }
    }

    @Test fun `ambiguous alias table never hijacks the sticky book without corroborating context (fuzz)`() {
        // Iterates ReferenceWatcher.AMBIGUOUS_BOOK_FORMS itself rather than hardcoding "иоанну"/
        // "бытие" — any future word added to that table is automatically covered by this test with
        // no extra work. Fixed seed for reproducibility; fillers deliberately contain no digits, no
        // epistle markers, and no book names/aliases, so corroboration can never trigger by accident.
        val fillers = NEUTRAL_RU
        val rnd = Random(9_140_705)
        for ((word, book) in ReferenceWatcher.AMBIGUOUS_BOOK_FORMS) {
            repeat(50) {
                val before = List(rnd.nextInt(4)) { fillers[rnd.nextInt(fillers.size)] }
                val after = List(rnd.nextInt(4)) { fillers[rnd.nextInt(fillers.size)] }
                val sentence = (before + word + after).joinToString(" ") + "."
                val sticky = TestSticky()
                sticky.watchBook = 45 // Romans — distinct from every AMBIGUOUS_BOOK_FORMS target book
                sticky.watchChapter = 3
                ReferenceWatcher.process(sentence, sticky, now = 1_000L)
                assertEquals(45, sticky.watchBook,
                    "bare \"$word\" with no corroboration must not hijack the sticky to book $book: \"$sentence\"")
            }
        }
    }

    @Test fun `ambiguous alias table resolves once corroborated by a nearby digit (fuzz)`() {
        // Proves the corroboration gate isn't just permanently closed — same table, same fillers,
        // this time with an adjacent chapter number.
        val fillers = NEUTRAL_RU.take(6)
        val rnd = Random(9_140_705)
        for ((word, book) in ReferenceWatcher.AMBIGUOUS_BOOK_FORMS) {
            repeat(20) {
                val before = List(rnd.nextInt(3)) { fillers[rnd.nextInt(fillers.size)] }
                val chapterNum = rnd.nextInt(1, 20)
                val sentence = (before + word + chapterNum.toString() + "глава").joinToString(" ") + "."
                val sticky = TestSticky()
                val refs = ReferenceWatcher.process(sentence, sticky, now = 1_000L)
                assertTrue(refs.isEmpty(), "book+chapter alone must not emit, got $refs for \"$sentence\"")
                assertEquals(book, sticky.watchBook, "digit-adjacent \"$word\" should resolve to book $book: \"$sentence\"")
            }
        }
    }

    // ── Inflected ambiguous words (an earlier session, AMBIGUOUS_BOOK_STEMS) ────

    @Test fun `genitive бытия as ordinary vocabulary does not clear sticky chapter mid-passage (real session trace)`() {
        // GATE: AMBIGUOUS_BOOK_STEMS — "бытия" is the genitive of "бытие" (being/existence) and
        //       reaches the stem path, which the exact-token gate does not cover.
        // TRACE: S09 — the MT of "...level of his being" resolved as Genesis and
        //        nulled the sticky chapter mid-Psalm-14.
        //
        // The ambiguous word must stay >2 tokens from any digit — inside that window the
        // corroboration gate admits it and the test would pass for the wrong reason. [bare] has no
        // digits at all. A trailing citation was tried and removed: "… Псалом 14." re-establishes
        // chapter 14 on its own and masks the very clearing this guards, so the test stayed green
        // with AMBIGUOUS_BOOK_STEMS emptied. Found by mutation, not by reading.
        assertStickyHolds(19, 14, bare("бытия"))
        assertStickyHolds(19, 14, bare("бытия", before = 2, after = 2))
    }

    @Test fun `digit-adjacent genitive бытия still resolves as Genesis`() {
        val r = run("Бытия, 1 глава, 1 стих.").single()
        assertEquals(Triple(1, 1, 1), r.triple())
    }

    // ── English short aliases on the translation track ────────────────
    //
    // The ≤4-char gate is keyed on the exact token, so an English plural or possessive walks straight
    // past it — "song" is gated, "songs" is not. And a Bible *version* name carries a book name
    // inside it. Both shapes are from real translation-track traces across the archive.

    @Test fun `plural singing vocabulary does not become Song of Solomon (real session traces)`() {
        // GATE: isShortAlias — a plural inherits the gate its singular has ("song" ≤4 chars).
        // TRACE: S08, S09, S12 — three services, one
        //        shape; the plural is the whole trigger, so one filler frame per form is enough.
        for (text in listOf(
            "two songs.",
            bareEn("songs"),
            bareEn("songs", before = 2, after = 2),
        )) {
            val sticky = TestSticky()
            ReferenceWatcher.process(text, sticky, now = 1_000L)
            assertNull(sticky.watchBook, "ordinary singing vocabulary must not prime a book: \"$text\"")
        }
    }

    @Test fun `a possessive of a short alias is gated like the singular`() {
        // GATE: isShortAlias — a possessive inherits the gate its singular has ("job" ≤4 chars).
        // TRACE: S08 — "Job's first trial…" primed Job off ordinary narration.
        val sticky = TestSticky()
        feed(sticky, bareEn("job's"))
        assertNull(sticky.watchBook)
    }

    @Test fun `a bible version name is not a citation (real session trace)`() {
        // GATE: isVersionNamePart — a book name inside a VERSION name names the module being read
        //       from, not a citation.
        // TRACE: S09 — mid-Psalm-14 the sticky book flipped to James for the rest of
        //        the passage. The real citation must still win in the same utterance, so the Psalm
        //        stays in the input.
        val sticky = TestSticky()
        feed(sticky, "Psalm 14, New King James version.")
        assertEquals(19, sticky.watchBook, "the citation is Psalm 14; \"King James\" names a version")
        // (The chapter does not survive here: prose after a bare number drops it by design — see the
        // colon-only exception in interpret's Filler branch. The book is what mattered for this bug.)
    }

    @Test fun `genuine English citations still resolve`() {
        // The recall guard for the three gates above — the marker words ("Gospel of", "book of")
        // are load-bearing here, unlike in the negatives, so they stay.
        assertEquals(42, run("the Gospel of Luke, chapter 21, verse 28.").single().bookNum)
        assertEquals(Triple(42, 11, 24), run("Luke, chapter 11, verses 24 through 26.").single().triple())
        val psalms = TestSticky()
        feed(psalms, "in the book of Psalms.")
        assertEquals(19, psalms.watchBook, "an explicit \"book of Psalms\" marker is a real citation")
        assertEquals(Triple(59, 1, 5), run("James, chapter 1, verse 5.").single().triple())
    }

    // ── SPB-registered short aliases that are their own stem ─────
    //
    // BookResolver.register() folds every loaded SPB module's book names into the alias table at
    // startup, so what the engine resolves live is wider than the static table these tests otherwise
    // see. The Russian Synodal module names book 65 "Иуда" and book 28 "Осия" — both ordinary words
    // (Judas the man; a "stubbornness"-adjacent noun) and both ≤4 chars, i.e. exactly the shape the
    // corroboration gate exists for. [withRegisteredBookNames] reproduces that startup state.

    private fun withRegisteredBookNames(vararg names: Pair<Int, String>, body: () -> Unit) {
        BookResolver.register(names.toList())
        try {
            body()
        } finally {
            BookResolver.register(emptyList())
        }
    }

    @Test fun `Иуда naming the betrayer does not hijack the sticky book (real session trace)`() {
        // GATE: classify — an SPB-registered ≤4-char name reaches only the stem fallback, which the
        //       exact branch's short-alias refusal must not re-admit (`unvettedShortName`).
        // TRACE: S12 — a sermon illustration about Judas Iscariot flipped the sticky
        //        from Song of Solomon to Jude, nothing emitted, no citation in either track.
        //        Kept bilingual: the gate has to hold on the concatenated tracks — and that is what
        //        caught the EN half. "judas" is the GERMAN alias for Jude, 5 chars, so it cleared
        //        the short-alias gate and primed Jude off the English word for the betrayer. The
        //        original recorded MT garbled that word, so no test had ever exercised it.
        withRegisteredBookNames(65 to "Иуда") {
            val sticky = sticky(book = 22, chapter = 2)
            feedBi(sticky, bare("иуда") to bareEn("judas"))
            assertEquals(22, sticky.watchBook, "narrating Judas must not make Jude the sticky book")
            assertEquals(2, sticky.watchChapter)
        }
    }

    @Test fun `осия as ordinary vocabulary does not hijack the sticky book (real session trace)`() {
        // The same mechanism, found first: sticky-log-S09 flipped to Hosea and held it
        // as the wrong sticky book for ~40 minutes, taking a cluster of Psalm 14 references down with
        // it (TRAINING_PLAN's "short-alias corroboration too loose" gap).
        withRegisteredBookNames(28 to "Осия") {
            val sticky = sticky(book = 19, chapter = 14)
            feed(sticky, bare("осия"))
            assertEquals(19, sticky.watchBook)
            assertEquals(14, sticky.watchChapter)
        }
    }

    @Test fun `a from-the-first-verse opener is a verse, not a chapter (real session trace)`() {
        // GATE: interpret `fromMark` — a "с"/"from" ordinal opens a VERSE span, so it must not be
        //       taken as the chapter when the book was named earlier in the same accumulated text.
        // TRACE: S13 seg 635 — the MT "From the first verse" carries no chapter at
        //        all; the branch took the verse ordinal as chapter and emitted 19:1:1 tier-2
        //        (auto-go-live) mid-Psalm-23.
        //
        // The EN track's number-BEFORE-book order ("The 23rd Psalm") is load-bearing and was found
        // by mutation, not by reading: with the plainer "Psalm 23." the gate is never reached and
        // this test passes with `fromMark` deleted. Two short sentences per track is the minimum
        // that still reproduces — the book must be named earlier in the SAME accumulated text.
        val sticky = TestSticky()
        val refs = feedBi(
            sticky,
            "Псалом 23. С первого стиха." to "The 23rd Psalm. From the first verse.",
        )
        assertEquals(23, sticky.watchChapter, "\"from the first verse\" names a verse, not chapter 1")
        assertTrue(refs.any { it.triple() == Triple(19, 23, 1) }, "expected Psalm 23:1, got $refs")
        assertTrue(refs.none { it.chapter == 1 }, "nothing in Psalm 1 may be cited: $refs")
    }

    @Test fun `с первого стиха after a chapter announcement still reads as verse 1`() {
        val r = run("Псалом 23. С первого стиха.")
        assertTrue(r.any { it.triple() == Triple(19, 23, 1) }, "expected Psalm 23:1, got $r")
    }

    @Test fun `an epistle named before its ordinal resolves to the epistle, not the gospel`() {
        // Real trace (S13.db row 928): "Иоанн говорит в первом послании, в первой главе
        // шестом стихе" — the book is named FIRST and the ordinal+marker follow it. resolveNumberedBookAt
        // only looks backwards ("первое послание Иоанна"), so this resolved to the Gospel and shipped as
        // an explicit tier-1, i.e. straight to the screen. The reverse lookup found the real verse
        // (1 John 1:6) from the quoted text moments later, which is how the mistake is visible at all.
        val r = run("Иоанн говорит в первом послании, в первой главе шестом стихе,")
        assertTrue(
            r.any { it.triple() == Triple(62, 1, 6) },
            "expected 1 John 1:6 (book 62), got $r",
        )
        assertTrue(r.none { it.bookNum == 43 }, "the Gospel of John must not be emitted here: $r")
    }

    @Test fun `a gospel citation with no epistle marker still resolves to the gospel`() {
        // The guard for the fix above: an ordinary Gospel citation must be untouched.
        assertEquals(Triple(43, 3, 16), run("Евангелие от Иоанна, 3 глава, 16 стих.").single().triple())
        assertEquals(Triple(43, 1, 6), run("Иоанна, 1 глава, 6 стих.").single().triple())
    }

    // ── Verse-range announcement against a live sticky (an earlier session) ────────

    @Test fun `a bare verse range binds to the sticky chapter, not as a chapter itself (real session trace)`() {
        // Real trace (S13.db, seg 797, ts_ms=1784761797829): re-reading Psalm 24 twenty
        // minutes after the first pass, the speaker announced the span without restating the chapter:
        // "стихи 3-го стиха по 6-й" = verses 3 through 6. The leading "3" was bound as a CHAPTER,
        // emitting 19:3:3 / 19:3:6 against a sticky that already knew chapter 24 — so the operator's
        // go-lives on verses 4, 5 and 6 had nothing to match. Verse keywords surround the number
        // ("стихи … стиха … по …"), so there is no ambiguity about what it is.
        val sticky = TestSticky()
        sticky.watchBook = 19
        sticky.watchChapter = 24
        val refs = ReferenceWatcher.process(
            "стихи 3-го стиха по 6-й.", sticky, now = 1_000L,
        )
        assertEquals(24, sticky.watchChapter, "the announcement names verses, so the chapter must survive")
        assertTrue(
            refs.any { it.bookNum == 19 && it.chapter == 24 && it.verseStart == 3 },
            "expected Psalm 24:3 (range to 6), got $refs",
        )
    }

    @Test fun `once a verse is bound, a trailing number never becomes a chapter`() {
        // Mechanism-level: the "bare book N = chapter" fallback must stay switched off for the rest of
        // a reference once a verse keyword has claimed a number, whatever phrasing follows. Each of
        // these announces verses of the sticky chapter, in either track's grammar.
        val phrasings = listOf(
            "стихи 3-го стиха по 6-й",
            "стих 3 по 6",
            "с 3 стиха по 6 стих",
            "verses 3 through 6",
            "verse 3 to 6",
        )
        for (text in phrasings) {
            val sticky = TestSticky()
            sticky.watchBook = 19
            sticky.watchChapter = 24
            val refs = ReferenceWatcher.process("$text.", sticky, now = 1_000L)
            assertEquals(24, sticky.watchChapter, "chapter must survive \"$text\"")
            assertTrue(
                refs.any { it.bookNum == 19 && it.chapter == 24 && it.verseStart == 3 },
                "expected Psalm 24:3 from \"$text\", got $refs",
            )
        }
    }

    @Test fun `a verse number spoken before its keyword is not discarded`() {
        // Russian puts the number first ("Псалом 23, 1 стих"), so at the verse keyword the buffer
        // holds BOTH the chapter and the verse. Taking the first as chapter and dropping the rest
        // silently lost the verse and left a chapter-only reference that never emits.
        assertEquals(Triple(19, 23, 1), run("Псалом 23, 1 стих.").single().triple())
        assertEquals(Triple(43, 3, 16), run("Иоанна 3, 16 стих.").single().triple())
        // The English order (number after the keyword) must keep working — it binds via the pending
        // keyword instead, and is the case the earlier fix was written for.
        assertEquals(Triple(19, 10, 13), run("Psalm 10, verse 13.").single().triple())
    }

    @Test fun `a bare book and number still reads as a chapter`() {
        // The other side of the same gate: with no verse bound, "book N" must still mean chapter N —
        // both when the verse follows in the same utterance and when it arrives in a later one.
        assertEquals(Triple(19, 23, 1), run("Псалом 23, 1 стих.").single().triple())
        assertTrue(
            run("Псалом 23.", "5 стих.").any { it.triple() == Triple(19, 23, 5) },
            "the bare book-number must still prime the sticky chapter for a later verse",
        )
    }

    @Test fun `открой in ordinary prose does not hijack the sticky book (real session trace)`() {
        // Real trace (sticky-log-S12.jsonl): a passage
        // about opening a door flipped the sticky from Luke 11 to Revelation. The stem
        // over-extension gate does not reach it — "откр" (the Revelation abbreviation alias) is only
        // 4 chars, and Russian verb forms sit 1-2 chars past it: "открой" is +2, under the >=3
        // threshold, so it resolved unconditionally. ("открываем"/"открывает", which stickyAudit
        // named because it reports the first matching token, are +5 and correctly gated already.)
        val sticky = TestSticky()
        sticky.watchBook = 42
        sticky.watchChapter = 11
        ReferenceWatcher.process(
            "Мы впускаем дверь и открываем дверь для Духа Святого тогда, когда Бог говорит нам. " +
                "Вот это хорошая дверь. Тогда как раз, когда грешник нечестивый, вот он должен " +
                "открыть свое сердце, когда мы говорим, чтобы сердце открой.",
            sticky, now = 1_000L,
        )
        assertEquals(42, sticky.watchBook, "opening a door must not make Revelation the sticky book")
        assertEquals(11, sticky.watchChapter)
    }

    @Test fun `spelled-out Откровение still resolves - it matches a longer stem than the abbreviation`() {
        // The gate keys on the short "откр" stem only. Real citations spell the book out and resolve
        // through "откровен", so they stay unconditional.
        val sticky = TestSticky()
        ReferenceWatcher.process("Читаем в Откровении.", sticky, now = 1_000L)
        assertEquals(66, sticky.watchBook)
    }

    @Test fun `the Откр abbreviation still resolves when a chapter is adjacent`() {
        val r = run("Откр 3 глава 20 стих.").single()
        assertEquals(Triple(66, 3, 20), r.triple())
    }

    @Test fun `a cited Иуда with a chapter still resolves as Jude`() {
        withRegisteredBookNames(65 to "Иуда") {
            val r = run("Иуда, 1 глава, 3 стих.").single()
            assertEquals(Triple(65, 1, 3), r.triple())
        }
    }

    @Test fun `a cited послание Иуды still resolves as Jude`() {
        // The marker noun is the corroboration the gate asks for, so the citation survives it.
        val r = run("Послание Иуды, 1 глава, 9 стих.").single()
        assertEquals(Triple(65, 1, 9), r.triple())
    }

    @Test fun `an inflected book name is still unconditional - only the bare short alias is gated`() {
        // The gate must not spread to ordinary inflected citations, which extend past their stem and
        // carry no ambiguity: "Луки"/"Марка" name a Gospel and nothing else.
        withRegisteredBookNames(65 to "Иуда", 28 to "Осия") {
            for ((text, book) in listOf("От Луки читаем." to 42, "Марка читаем сегодня." to 41)) {
                val sticky = TestSticky()
                ReferenceWatcher.process(text, sticky, now = 1_000L)
                assertEquals(book, sticky.watchBook, "inflected name must still prime the sticky: \"$text\"")
            }
        }
    }

    @Test fun `every registered short book name needs corroboration, in both branches`() {
        // Mechanism-level: whatever a module happens to name a book, a bare ≤4-char name that is
        // ordinary vocabulary must never prime the sticky on its own, and the same name with a
        // chapter number must always resolve. Iterating the table means a module registering a new
        // short name is covered without writing another test.
        val shortNames = listOf(65 to "Иуда", 28 to "Осия", 32 to "Иона", 30 to "Амос", 25 to "Плач")
        withRegisteredBookNames(*shortNames.toTypedArray()) {
            for ((book, name) in shortNames) {
                val bare = TestSticky()
                ReferenceWatcher.process("И тогда $name сказал это слово.", bare, now = 1_000L)
                assertNull(bare.watchBook, "bare \"$name\" must not prime the sticky with no citation context")

                val cited = TestSticky()
                val refs = ReferenceWatcher.process("$name, 1 глава, 2 стих.", cited, now = 1_000L)
                assertEquals(book, refs.single().bookNum, "\"$name\" with a chapter must still resolve")
            }
        }
    }

    // ── Bare chapter number before a verse keyword (an earlier session) ────────

    @Test fun `Psalm 10 verse 13 resolves chapter 10 verse 13, not transposed (real session trace)`() {
        // Real trace: DB row ts_ms=1783897645684 (session S09), the corrected/final
        // STT text. No chapter keyword for "10" — it must still bind as chapter by the universal
        // bare "book N" convention, not get swept into verseStart by the trailing "verse" keyword.
        val r = run("Psalm 10, verse 13.").single()
        assertEquals(Triple(19, 10, 13), r.triple())
    }

    @Test fun `Psalm 10 verse 30 (STT mis-hearing variant) still resolves chapter 10`() {
        // Real trace: DB row ts_ms=1783897642945, a transient partial where STT misheard "13" as
        // "30" one second before self-correcting. The engine previously parsed this as chapter=30,
        // verse=10 (transposed) — an "explicit" match, which auto-goes-live with no staging net.
        val r = run("Psalm 10, verse 30.").single()
        assertEquals(Triple(19, 10, 30), r.triple())
    }

    @Test fun `bare chapter number before a verse keyword resolves as chapter, not verse (general invariant)`() {
        // Not just Psalm 10 (the real trace) — the same "book, bare number, verse keyword with no
        // chapter keyword" shape must hold across books and both languages.
        val cases = listOf(
            Triple(19, "Псалом 10 стих 13.", Triple(19, 10, 13)),
            Triple(40, "Matthew 5 verse 3.", Triple(40, 5, 3)),
            Triple(45, "Римлянам 8 стих 28.", Triple(45, 8, 28)),
            Triple(43, "John 3 verse 16.", Triple(43, 3, 16)),
        )
        for ((_, text, expected) in cases) {
            val r = run(text).single()
            assertEquals(expected, r.triple(), "expected $expected for \"$text\", got ${r.triple()}")
        }
    }

    // Real non-reference sentences confirmed benign in past sessions. Append newly-confirmed-benign
    // trigger text here as future sessions surface it (in addition to, not instead of, a dedicated
    // named test for any actual fix) — this list is meant to keep growing every session instead of
    // coverage living only in one-off named tests. See TRAINING_PLAN.md Test Strategy.
    // Each line carries ONE trigger word in the inflection that actually misfired, in a neutral
    // frame — the frames are deliberately different from the dedicated named tests above, so a
    // corpus entry is an independent sample rather than a second run of the same assertion.
    private val NEGATIVE_CORPUS = listOf(
        "вступаем в то бытие.",                    // AMBIGUOUS_BOOK_FORMS: быти* = being/existence
        "эпизод был предоставлен Иоанну.",          // AMBIGUOUS_BOOK_FORMS: dative of the man's name
        "молодежь споет о Христе.",                 // no alias at all — plain prose must stay silent
        "как можно курить в автобусе?",             // ditto, unrelated vocabulary
        "поем ее в нашей Второй Церкви.",           // "Второй" is an ordinal, not a numbered book
        // Stem over-extension + short real-word aliases in prose (TRAINING_PLAN Known Gaps),
        // closed by classify's over-extension / short-alias gates.
        "он открылся народу.",                      // откр + 3 chars
        "нужно повторить отрывок.",                 // a Deuteronomy stem over-extended
        "we will sing a song this morning.",         // EN short alias
        "he did a good job today.",                  // EN short alias
        "при этом мы видим деталь.",                 // RU short alias
        "на уровне своего бытия.",                   // AMBIGUOUS_BOOK_STEMS: genitive of бытие
        "когда Иуда предал его.",                    // registered short name, narrated as a person
        "when Judas betrayed him.",                  // the EN half of the same — "judas" is a German alias
    )

    @Test fun `growing negative corpus never emits or hijacks an unset sticky book`() {
        for (line in NEGATIVE_CORPUS) {
            val sticky = TestSticky()
            val refs = ReferenceWatcher.process(line, sticky, now = 1_000L)
            assertTrue(refs.isEmpty(), "negative-corpus line should not emit: \"$line\" -> $refs")
            assertEquals(null, sticky.watchBook, "negative-corpus line should not seed an unset sticky book: \"$line\"")
        }
    }

    // ── Bilingual transcript/translation disagreement (mistranslated book) ─────

    @Test fun `transcript book wins over mistranslated translation book (1 Samuel not 1 Kings)`() {
        val sticky = TestSticky()
        val refs = ReferenceWatcher.process(
            "1-я царств, 15 глава, с 22 по 30 стих.",
            "1 Kings, chapter 15, from 22 to 30.",
            sticky, now = 1_000L,
        )
        assertTrue(
            refs.any { it.bookNum == 9 && it.chapter == 15 && it.verseStart == 22 && it.verseEnd == 30 },
            "expected 1 Samuel 15:22-30 from the transcript, got $refs",
        )
        assertEquals(9, sticky.watchBook, "sticky must carry the transcript's book, not the mistranslated 1 Kings")
        assertEquals(15, sticky.watchChapter)

        // A later utterance where the translation is empty/lagging must still continue against the
        // uncorrupted sticky.
        val late = ReferenceWatcher.process("31 стих.", "", sticky, now = 1_000L)
        assertTrue(late.any { it.bookNum == 9 && it.chapter == 15 && it.verseStart == 31 },
            "expected sticky continuation into 1 Samuel 15:31, got $late")
    }

    @Test fun `transcript book wins over mistranslated translation book (Lamentations not Jeremiah)`() {
        val sticky = TestSticky()
        val refs = ReferenceWatcher.process(
            "Книга Плач Иеремии, 3 глава, 21 стих.",
            "The book Lamentations of Jeremiah, 3 chapters, 21 verses.",
            sticky, now = 1_000L,
        )
        assertTrue(refs.any { it.bookNum == 25 && it.chapter == 3 },
            "expected Lamentations 3 from the transcript, got $refs")
        assertEquals(25, sticky.watchBook, "sticky must not be corrupted to Jeremiah (24)")
    }

    @Test fun `translation-only book citation is still picked up when transcript has none`() {
        // Book+chapter only (no verse spoken) never emits a Ref (see the tangent test above) — the
        // signal to check for a translation-only citation is that it still primes the sticky.
        val sticky = TestSticky()
        val refs = ReferenceWatcher.process(
            "и мы читаем",
            "1 Corinthians 11.",
            sticky, now = 1_000L,
        )
        assertTrue(refs.isEmpty(), "book+chapter with no verse must not emit, got $refs")
        assertEquals(46, sticky.watchBook, "translation-only book citation must still prime the sticky")
        assertEquals(11, sticky.watchChapter)

        // A later bare verse (transcript only, translation empty/lagging) resolves against it.
        val late = ReferenceWatcher.process("23 стих.", "", sticky, now = 1_000L)
        assertTrue(late.any { it.bookNum == 46 && it.chapter == 11 && it.verseStart == 23 },
            "expected 1 Corinthians 11:23 via the translation-primed sticky, got $late")
    }

    @Test fun `agreeing tracks still combine normally`() {
        val refs = ReferenceWatcher.process(
            "Послание к римлянам, 6 глава, 4 стих.",
            "Romans chapter 6 verse 4.",
            TestSticky(), now = 1_000L,
        )
        assertTrue(refs.any { it.bookNum == 45 && it.chapter == 6 && it.verseStart == 4 },
            "expected Romans 6:4 when both tracks agree, got $refs")
    }

    // ── Music precision gate ─────────────────────────────────────────────────────

    @Test fun `music segment is suppressed and does not seed sticky`() {
        val sticky = TestSticky()
        // A reference-shaped utterance that WOULD emit, but tagged as music → nothing, and it must
        // not seed the sticky context.
        val sung = ReferenceWatcher.process(
            "Матфея 7 глава 21 стих.", sticky, now = 1_000L, isMusic = true,
        )
        assertTrue(sung.isEmpty(), "music segment must not emit, got $sung")
        assertEquals(null, sticky.watchBook, "music must not seed the sticky book")

        // Same text as normal speech does emit — proves the gate, not the parser, suppressed it.
        val spoken = ReferenceWatcher.process("Матфея 7 глава 21 стих.", TestSticky(), now = 1_000L)
        assertTrue(spoken.any { it.triple() == Triple(40, 7, 21) },
            "spoken reference should emit Matthew 7:21, got $spoken")
    }

    @Test fun `music gate can be disabled via Config`() {
        try {
            Config.suppressDuringMusic = false
            val r = ReferenceWatcher.process("Матфея 7 глава 21 стих.", TestSticky(), now = 1_000L, isMusic = true)
            assertTrue(r.any { it.triple() == Triple(40, 7, 21) }, "disabled gate should let it through, got $r")
        } finally {
            Config.suppressDuringMusic = true
        }
    }

    // ── Aggressiveness-gated recall: off at lower levels, on at the intended level ─

    @Test fun `normalizeStt gates э to е book resolution`() {
        // Book + chapter only (no verse) never emits a Ref, so check the sticky it primes instead.
        val line = "Послание к эфесянам, 6 глава."
        fun stickyBookChapter(): Pair<Int?, Int?> {
            val sticky = TestSticky()
            ReferenceWatcher.process(line, sticky, now = 1_000L)
            return sticky.watchBook to sticky.watchChapter
        }
        try {
            Config.applyLevel("conservative")
            assertEquals(null to null, stickyBookChapter(), "conservative must not normalize э→е")
            Config.applyLevel("balanced")
            assertEquals(49 to 6, stickyBookChapter(), "balanced should resolve Ephesians 6")
            Config.applyLevel("aggressive")
            assertEquals(49 to 6, stickyBookChapter(), "aggressive should resolve Ephesians 6")
        } finally {
            Config.applyLevel("balanced")
        }
    }

    @Test fun `inferBookAtEnd gates book named after its numbers`() {
        val line = "14 стих 3 главы Матфея."
        try {
            Config.applyLevel("balanced")
            assertTrue(run(line).none { it.triple() == Triple(40, 3, 14) },
                "balanced should not infer a trailing book, got ${run(line)}")
            Config.applyLevel("aggressive")
            assertTrue(run(line).any { it.triple() == Triple(40, 3, 14) },
                "aggressive should infer Matthew 3:14, got ${run(line)}")
        } finally {
            Config.applyLevel("balanced")
        }
    }

    @Test fun `gated recall does not regress precision negatives at any level`() {
        val negatives = listOf(
            "где звучали стихотворения.",
            "это самое главное.",
            "глава семьи",
            "читать стихи.",
            "хочу прочитать один стих.",
        )
        try {
            for (level in listOf("conservative", "balanced", "aggressive")) {
                Config.applyLevel(level)
                for (n in negatives) {
                    assertTrue(run(n).isEmpty(), "level=$level should not emit on \"$n\", got ${run(n)}")
                    assertTrue(withSticky(n).isEmpty(),
                        "level=$level should not bind \"$n\" to sticky, got ${withSticky(n)}")
                }
            }
        } finally {
            Config.applyLevel("balanced")
        }
    }


    // ── FP gates: stem over-extension + short aliases (Known Engine Gaps, since closed) ──────

    @Test fun `short alias with adjacent number still resolves`() {
        // Gate B must not cost recall when the citation carries its number nearby.
        // (EN keyword-after-number forms like "Job chapter 3 verse 2" still mis-parse for a
        // pre-existing, unrelated reason — see the keyword-order gap note in TRAINING_PLAN.md.)
        val jobRefs = run("Job 3:2 tells us more.")
        assertTrue(jobRefs.any { it.bookNum == 18 && it.chapter == 3 && it.verseStart == 2 }, "Job 3:2 should resolve, got $jobRefs")
        val revRefs = run("откр 3 глава 12 стих.")
        assertTrue(revRefs.any { it.bookNum == 66 && it.chapter == 3 }, "Rev 3:12 should resolve, got $revRefs")
    }

    @Test fun `multi-word short-book aliases are not gated`() {
        val refs = run("Song of Solomon 2:1")
        assertTrue(refs.any { it.bookNum == 22 && it.chapter == 2 }, "Song of Solomon 2:1 should resolve, got $refs")
    }

    @Test fun `normal grammatical stem endings are not gated`() {
        // Extension of 1-2 chars over the stem (ordinary inflection) stays ungated.
        val refs = run("Матфея 5 глава 3 стих.")
        assertTrue(refs.any { it.bookNum == 40 && it.chapter == 5 }, "Matthew 5:3 should resolve, got $refs")
    }

    @Test fun `over-extended stems in prose never hijack the sticky`() {
        // "открылся"/"повторить" share prefixes with book stems; bare in prose they must
        // neither emit nor move the sticky book (the sticky-pollution path behind many
        // chapter-history FPs in the recorded an earlier session).
        val sticky = TestSticky()
        ReferenceWatcher.process("Послание к римлянам, 6 глава, 4 стих.", sticky, 1_000L)
        val bookBefore = sticky.watchBook
        ReferenceWatcher.process("он удостоил его тому, что открылся народу.", sticky, 2_000L)
        ReferenceWatcher.process("нужно повторить этот отрывок.", sticky, 3_000L)
        assertEquals(bookBefore, sticky.watchBook, "prose stem look-alikes must not move the sticky book")
    }

    @Test fun `short aliases in bare prose never emit or hijack - fuzz`() {
        // Mechanism-level guard over every registered single-token alias of length <= 4:
        // bare in prose (no digits, no markers) it must not emit; framing it as a citation
        // with a number must still resolve to SOME book (recall check on a sample).
        val shortAliases = BookResolver.ALIASES.keys.filter { !it.contains(' ') && it.length in 3..4 }
        assertTrue(shortAliases.isNotEmpty())
        for (alias in shortAliases) {
            val refs = run("we were talking about $alias yesterday evening.")
            assertTrue(refs.isEmpty(), "bare short alias '$alias' emitted $refs")
        }
        for (alias in shortAliases.shuffled(kotlin.random.Random(9_140_709)).take(10)) {
            val refs = run("$alias 3 глава 2 стих.")
            assertTrue(refs.isNotEmpty(), "corroborated short alias '$alias' should resolve")
        }
    }

    // ── Colon-bound citations survive trailing prose (found via DB row 843) ────────

    @Test fun `colon citation followed by prose keeps its verse`() {
        // Real transcript row 843: explicit dot-form citation + the quoted verse text. The prose
        // word after the citation used to wipe the buffered verse (name+count guard over-reach),
        // downgrading an explicit tier-1 detection to a weak staged chapter-scan.
        val ru = run("Исайя 26.3 написано так.")
        assertTrue(
            ru.any { it.bookNum == 23 && it.chapter == 26 && it.verseStart == 3 && it.tier == 1 },
            "Исайя 26.3 + prose should stay an explicit tier-1 ref, got $ru"
        )
        val en = run("Isaiah 26:3 says so.")
        assertTrue(
            en.any { it.bookNum == 23 && it.chapter == 26 && it.verseStart == 3 && it.tier == 1 },
            "Isaiah 26:3 + prose should stay an explicit tier-1 ref, got $en"
        )
    }

    @Test fun `dot between digits is citation notation`() {
        val refs = run("Псалом 118.105")
        assertTrue(
            refs.any { it.bookNum == 19 && it.chapter == 118 && it.verseStart == 105 },
            "Псалом 118.105 should resolve as chapter:verse, got $refs"
        )
    }

    @Test fun `keyword-bound numbers still drop before prose - not verses`() {
        // colonSeen is deliberately the ONLY filler-survival trigger: a chapter keyword followed
        // by an unrelated count must not fabricate a verse.
        val refs = run("Матфея 3 глава, 5 причин.")
        assertTrue(
            refs.none { it.verseStart == 5 },
            "count after keyword-bound chapter must not become verse 5, got $refs"
        )
    }

    // ── Punctuation robustness: apostrophe aliases + ё folding (an earlier audit) ──────────────

    @Test fun `ukrainian apostrophe alias resolves in both spellings`() {
        // "об'явлення" tokenizes to two tokens (apostrophe → space); the registered
        // space/deleted variants are what make the greedy join land on Revelation.
        val withApostrophe = run("Об'явлення 3 глава 12 вірш.")
        assertTrue(
            withApostrophe.any { it.bookNum == 66 && it.chapter == 3 && it.verseStart == 12 },
            "Об'явлення 3:12 should resolve, got $withApostrophe"
        )
        val without = run("Обявлення 3 глава 12 вірш.")
        assertTrue(
            without.any { it.bookNum == 66 && it.chapter == 3 && it.verseStart == 12 },
            "Обявлення 3:12 should resolve, got $without"
        )
    }

    @Test fun `ё spelling of a book name resolves`() {
        // ё→е folds before alias/stem lookup, so a ё-spelled inflection still resolves.
        val refs = run("Матфёя 5 глава 3 стих.")
        assertTrue(
            refs.any { it.bookNum == 40 && it.chapter == 5 && it.verseStart == 3 },
            "ё-spelled Матфёя should fold to Матфея and resolve, got $refs"
        )
    }

    // ── Keyword-first citations ────────

    @Test fun `keyword-first russian citation parses in order`() {
        // Was inverted before the pending-keyword fix: chapter=3, verse=26.
        val refs = run("Матфея глава 26 стих 3.")
        assertTrue(
            refs.any { it.bookNum == 40 && it.chapter == 26 && it.verseStart == 3 },
            "глава 26 стих 3 must parse as 26:3, got $refs"
        )
    }

    @Test fun `keyword-first english citation parses in order`() {
        // The remaining half of the TRAINING_PLAN keyword-order gap.
        val refs = run("Job chapter 3 verse 2.")
        assertTrue(
            refs.any { it.bookNum == 18 && it.chapter == 3 && it.verseStart == 2 },
            "Job chapter 3 verse 2 must parse as 3:2, got $refs"
        )
    }

    @Test fun `number-first forms are unchanged by pending keywords`() {
        val refs = run("Матфея 26 глава 3 стих.")
        assertTrue(
            refs.any { it.bookNum == 40 && it.chapter == 26 && it.verseStart == 3 },
            "26 глава 3 стих must still parse as 26:3, got $refs"
        )
    }

    @Test fun `pending keyword does not bind across prose`() {
        // A keyword followed by filler then an unrelated number must not fabricate a chapter.
        val refs = run("В этой главе, друзья, 26 человек.")
        assertTrue(refs.isEmpty(), "keyword across prose must not bind, got $refs")
    }

    // ── Multi-word book names and the author-name trap ────────────

    @Test fun `Плач Иеремии resolves even when both words are misinflected`() {
        // GATE: classify — BookResolver.resolveStemPhrase joins a multi-word name by per-word
        //       stem, so both halves may be inflected differently than the table spells them.
        // TRACE: S15 row 1 — the STT wrote nominative "Иеремия" where the alias
        //        "плач иеремии" needs the genitive, so the two words classified independently
        //        (Lamentations THEN Jeremiah) and interpret() kept the last book before the
        //        numbers. The whole sermon then ran against Jeremiah 3.
        //
        // The recorded text opened "прочитать сегодня книгу пророка …". That marker was dropped
        // after checking by mutation that this shorter form still fails with the stem-phrase join
        // removed — the join consumes both tokens before the corroboration gate is ever consulted,
        // so the marker was never load-bearing here.
        val refs = run("Плача Иеремия, 3 глава, 24 стих.")
        assertTrue(
            refs.any { it.triple() == Triple(25, 3, 24) },
            "Плача Иеремия 3:24 must resolve to Lamentations, got $refs"
        )
        assertTrue(refs.none { it.bookNum == 24 }, "must not also emit Jeremiah, got $refs")
    }

    @Test fun `author named in prose does not hijack the sticky book`() {
        // GATE: AMBIGUOUS_BOOK_FORMS (nominative "иеремия") + hasAmbiguousBookCorroboration's
        //       FROM_WORDS clause — a "с"-introduced number opens a verse span and must not
        //       corroborate a book named after it.
        // TRACE: S15 — preaching FROM Lamentations, each bare mention of the prophet
        //        re-pointed the sticky at Jeremiah. A bare name emits nothing by itself, so the
        //        damage only surfaced on the NEXT utterance carrying a number: "с 22 стиха" became
        //        Jeremiah 22:22, tier-1 auto-go-live.
        //
        // Three utterances is the minimum — seed, bare mention, then the FROM-bound verse — and the
        // sticky must be asserted, not just the emissions. The recorded 4th utterance repeated the
        // shape of the 2nd.
        val sticky = TestSticky()
        val refs = feed(
            sticky,
            "Плача Иеремия, 3 глава, 24 стих.",
            "Иеремия говорит о надежде.",
            "прочитаем с 22 стиха, Иеремия описывает.",
        )

        assertEquals(25, sticky.watchBook, "sticky must stay on Lamentations, got $refs")
        assertTrue(refs.none { it.bookNum == 24 }, "must never emit Jeremiah, got $refs")
        assertTrue(
            refs.any { it.bookNum == 25 && it.chapter == 3 && it.verseStart == 22 },
            "с 22 стиха must read as Lamentations 3:22, got $refs"
        )
    }

    @Test fun `explicit Jeremiah citation still resolves`() {
        // The gate above must not cost the real citation: the engine detected Jeremiah 33:3
        // correctly and the operator accepted it.
        val refs = run("Иеремия 33 глава 3 стих.")
        assertTrue(
            refs.any { it.triple() == Triple(24, 33, 3) },
            "explicit Jeremiah 33:3 must still resolve, got $refs"
        )
    }

    // ── Mechanism-level: multi-word aliases resolve under any case inflection ──────────────────

    @Test fun `every multi-word russian alias resolves through the stem-phrase index`() {
        // Invariant over the table itself, so a multi-word name added later is covered with no
        // extra test: whatever ALIASES spells, resolveStemPhrase must accept its own spelling.
        val multiWord = BookResolver.ALIASES.entries
            .filter { it.key.contains(' ') && it.key.any { c -> c in 'а'..'я' } }
        assertTrue(multiWord.isNotEmpty(), "expected multi-word Russian aliases in the table")
        for ((alias, book) in multiWord) {
            val resolved = BookResolver.resolveStemPhrase(alias.split(' '))
            // A stemmed phrase shared by two books is dropped as ambiguous by design; only assert
            // that when it DOES resolve it resolves to the right book.
            if (resolved != null) {
                assertEquals(book, resolved, "multi-word alias \"$alias\" resolved to $resolved")
            }
        }
    }

    @Test fun `multi-word aliases survive random russian case endings`() {
        // Fuzz over the same table: re-inflect each word with an arbitrary Russian ending and the
        // phrase must still name the same book — that is what "Плача Иеремия" needed and what the
        // exact-only join could not do. Seeded, so a failure reproduces.
        val endings = listOf("", "а", "и", "ю", "е", "ы", "я")
        val rnd = Random(9_140_805)
        val multiWord = BookResolver.ALIASES.entries
            .filter { it.key.contains(' ') && it.key.any { c -> c in 'а'..'я' } }
        for ((alias, book) in multiWord) {
            if (BookResolver.resolveStemPhrase(alias.split(' ')) == null) continue
            repeat(8) {
                // Only re-inflect all-Cyrillic words longer than the stemmer's 4-char floor.
                // An ordinal like "1-я" in "1-я царств" is notation, not a declinable word, and a
                // short preposition like the "от" of "от матфея" is below the floor — stemOf
                // cannot trim it, so appending an ending changes the stem itself rather than the
                // inflection. Neither is a form a speaker produces; the declinable half of every
                // such alias is still fuzzed.
                val mangled = alias.split(' ').map { w ->
                    if (w.length > 4 && w.all { c -> c in 'а'..'я' }) {
                        BookResolver.stemOf(w) + endings.random(rnd)
                    } else {
                        w
                    }
                }
                assertEquals(
                    book, BookResolver.resolveStemPhrase(mangled),
                    "re-inflected \"${mangled.joinToString(" ")}\" must still name book $book"
                )
            }
        }
    }

    @Test fun `stem-phrase matching never fires on a single token`() {
        // The join must not widen what one bare word resolves to — that is the exact-alias
        // branch's job, with its own short-alias and ambiguity gates.
        assertNull(BookResolver.resolveStemPhrase(listOf("плач")))
        assertNull(BookResolver.resolveStemPhrase(listOf("иеремия")))
    }

    // ── Alias gaps found by cross-checking an externally-written regex list ───────────────────

    @Test fun `Ezekiel resolves in the correct Synodal spelling`() {
        // The table carried only the one-и typo "иезекиль", so the correct "Иезекииль" — and every
        // inflection of it — resolved to nothing at all. Both spellings must work: the STT produces
        // either.
        for (form in listOf("Иезекииль", "Иезекииля", "Иезекиль")) {
            val refs = run("$form 3 глава 5 стих.")
            assertTrue(
                refs.any { it.triple() == Triple(26, 3, 5) },
                "$form 3:5 must resolve to Ezekiel, got $refs"
            )
        }
    }

    @Test fun `genitive Второзакония is a book, not the number two`() {
        // NumberWords matches stem "втор" (2) plus any plausible ending, and "озакония" starts with
        // a vowel — so the standard Russian title of Deuteronomy parsed as the numeral 2 and never
        // reached BookResolver. Only inflected forms were hit: "Второзаконие" is an exact alias and
        // matched before the number check.
        for (form in listOf("Второзаконие", "Второзакония", "Второзаконию")) {
            val refs = run("книга $form 3 глава 5 стих.")
            assertTrue(
                refs.any { it.triple() == Triple(5, 3, 5) },
                "$form 3:5 must resolve to Deuteronomy, got $refs"
            )
        }
    }

    @Test fun `ordinal number words are still numbers`() {
        // The longest-match rule must not turn a real numeral into a book: "второй" matches the
        // number stem "втор" and the book stem "втор" at equal length, so the number wins.
        val refs = run("Матфея вторая глава пятый стих.")
        assertTrue(
            refs.any { it.triple() == Triple(40, 2, 5) },
            "spelled ordinals must still parse as chapter 2 verse 5, got $refs"
        )
    }

    @Test fun `mechanism - no Cyrillic book alias is swallowed by the number lexicon`() {
        // Invariant over the alias table itself, so the next book name that happens to start with a
        // number stem is caught here rather than in a service. A name the engine knows must never
        // be read as a numeral.
        val cyrillic = BookResolver.ALIASES.keys.filter { k -> k.all { it in 'а'..'я' || it == ' ' } }
        assertTrue(cyrillic.size > 50, "expected a substantial Cyrillic alias set, got ${cyrillic.size}")
        // ≤2-char aliases ("пс", "мк") are skipped by classify on purpose — typed-input
        // abbreviations that would fire constantly on spoken prose.
        for (alias in cyrillic.filter { !it.contains(' ') && it.length >= 3 }) {
            val refs = run("$alias 3 глава 5 стих.")
            assertTrue(
                refs.any { it.chapter == 3 && it.verseStart == 5 },
                "alias \"$alias\" resolved to no book in an explicit citation"
            )
        }
    }

    @Test fun `canonical genitive book titles resolve but stay gated in prose`() {
        // "Книга Иова"/"Книга Руфи"/"Книга Ионы" are how these books are actually named in Russian,
        // and none of them resolved. Each is <=4 chars, so it inherits the short-alias
        // corroboration gate — cited, yes; bare prose, no.
        val expected = mapOf("иова" to 18, "руфи" to 8, "ионы" to 32, "тита" to 56)
        for ((form, book) in expected) {
            val cited = run("книга $form 3 глава 5 стих.")
            assertTrue(
                cited.any { it.triple() == Triple(book, 3, 5) },
                "книга $form 3:5 must resolve to book $book, got $cited"
            )
            val sticky = TestSticky()
            ReferenceWatcher.process("и тогда $form сказал это своему другу", sticky, 1_000L)
            assertNull(sticky.watchBook, "bare \"$form\" in prose must not claim the sticky book")
        }
    }

    @Test fun `mechanism - a FROM-bound number never corroborates a book named after it`() {
        // GATE: hasAmbiguousBookCorroboration's FROM_WORDS clause. Before this test the clause was
        //       guarded by exactly ONE case (the Lamentations trace), which the mutation baseline
        //       exposed. Iterating the two tables instead means the next word added to either is
        //       covered with no extra test.
        //
        // "с N стиха <word>" opens a verse span in the book already being read, so <word> must not
        // claim the sticky; "в N главе <word>" is an ordinary chapter citation and still must.
        for ((word, book) in ReferenceWatcher.AMBIGUOUS_BOOK_FORMS) {
            for (from in listOf("с", "со")) {
                val gated = sticky(book = 45, chapter = 3)
                feed(gated, "прочитаем $from 22 стиха, $word описывает.")
                assertEquals(
                    45, gated.watchBook,
                    "\"$from 22 стиха\" must not corroborate \"$word\" into book $book",
                )
            }
            val corroborated = sticky(book = 45, chapter = 3)
            feed(corroborated, "в 22 главе $word написано.")
            assertEquals(
                book, corroborated.watchBook,
                "an ordinary chapter citation must still resolve \"$word\" to book $book",
            )
        }
    }
}
