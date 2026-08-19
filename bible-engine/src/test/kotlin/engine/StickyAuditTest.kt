package engine

import engine.tools.Category
import engine.tools.StickyRow
import engine.tools.classify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The sticky auditor's bucketing, which decides what a human reads after a service.
 *
 * UNEXPLAINED is the category that has to stay trustworthy — it means "a jump no alias in the text
 * accounts for", i.e. the thing to look at first. Anything the live engine resolves by a route the
 * auditor doesn't ask about lands there wrongly and buries the real ones: across the recorded
 * sessions, 4 of 5 UNEXPLAINED rows were perfectly ordinary numbered-book citations.
 */
class StickyAuditTest {

    private fun row(
        transcript: String,
        newBook: Int?,
        prevBook: Int? = null,
        prevChapter: Int? = null,
        newChapter: Int? = null,
        translation: String = "",
    ) = StickyRow(
        ts = "a-recorded-moment",
        prevBook = prevBook, prevChapter = prevChapter,
        newBook = newBook, newChapter = newChapter,
        transcript = transcript, translation = translation,
    )

    @Test fun `an ordinal numbered-book citation is explained, not flagged (real session trace)`() {
        // sticky-log-S10.jsonl, — "Во втором послании Коринфянам"
        // resolves to 2 Corinthians through resolveNumberedBookAt; no alias spells it.
        val v = classify(
            row(
                "Почему? Теперь давайте задумаемся, собственно, о том, какой был отклик, наш " +
                    "отклик на то, что сделал для нас Господь. Во втором послании Коринфянам.",
                newBook = 47,
            )
        )
        assertEquals(Category.CONFIDENT, v.category, v.detail)
    }

    @Test fun `a bare epistle marker defaulting to the first is explained too (real session trace)`() {
        // sticky-log-S11.jsonl, — "в послании к Иоанну" → 1 John by the
        // John/Peter marker-alone convention.
        val v = classify(
            row("Апостол Павел очень интересно говорит в послании к Иоанну.", newBook = 62)
        )
        assertEquals(Category.CONFIDENT, v.category, v.detail)
    }

    @Test fun `a jump with nothing in the text to explain it is still flagged`() {
        val v = classify(row("Мы говорим, друзья нас не поняли, не поддержали.", newBook = 65))
        assertEquals(Category.UNEXPLAINED, v.category)
    }

    @Test fun `a short exact alias is still flagged for review`() {
        val v = classify(row("Я слышал их плач в ту ночь.", newBook = 25))
        assertEquals(Category.SHORT_ALIAS, v.category)
    }

    @Test fun `a same-book chapter clear is still flagged structurally`() {
        val v = classify(row("...", newBook = 19, prevBook = 19, prevChapter = 14, newChapter = null))
        assertEquals(Category.CHAPTER_CLEARED, v.category)
    }

    // ── Rows that are not book changes at all ─────────────────────────────────

    @Test fun `a row with no new book is not a jump`() {
        assertEquals(Category.OTHER, classify(row("что-то сказано", newBook = null, prevBook = 43)).category)
    }

    @Test fun `staying on the same book while changing chapter is not a jump`() {
        val v = classify(row("...", newBook = 43, prevBook = 43, prevChapter = 1, newChapter = 2))
        assertEquals(Category.OTHER, v.category, "only the book is audited here; chapters move constantly")
    }

    @Test fun `a chapter clear on a different book is judged on its text, not structurally`() {
        // CHAPTER_CLEARED is specifically the same-book reflush shape.
        val v = classify(row("совсем ничего", newBook = 65, prevBook = 43, prevChapter = 3, newChapter = null))
        assertEquals(Category.UNEXPLAINED, v.category)
    }

    // ── Where the supporting text is found ────────────────────────────────────

    @Test fun `the translation side can explain a jump on its own`() {
        // A bilingual feed carries the citation on whichever side was spoken.
        val v = classify(row("", newBook = 66, translation = "откровение"))
        assertEquals(Category.CONFIDENT, v.category, v.detail)
    }

    @Test fun `case and punctuation do not prevent a match`() {
        val v = classify(row("Итак, ОТКРОВЕНИЕ!", newBook = 66))
        assertEquals(Category.CONFIDENT, v.category, v.detail)
    }

    @Test fun `an ordinary grammatical ending is not treated as suspicious`() {
        // Russian inflection routinely adds a character or two ("Луки"); flagging that would flag
        // most rows and make the whole report noise.
        val v = classify(row("евангелие от луки", newBook = 42))
        assertEquals(Category.CONFIDENT, v.category, v.detail)
    }

    @Test fun `a first jump with no previous book is classified on its text like any other`() {
        assertEquals(Category.CONFIDENT, classify(row("откровение", newBook = 66, prevBook = null)).category)
    }

    @Test fun `every verdict carries the row it judged, so the report can print it`() {
        val original = row("откровение", newBook = 66)
        assertEquals(original, classify(original).row)
    }
}
