package org.churchpresenter.bibleengine.engine

import org.churchpresenter.bibleengine.Config
import org.churchpresenter.bibleengine.bible.EngineTranslation
import org.churchpresenter.bibleengine.bible.EngineVerse
import org.churchpresenter.bibleengine.bible.Script
import org.churchpresenter.bibleengine.detection.ReferenceWatcher

/** How many verses a reverse hit may step back to reach the start of the passage being read. */
private const val MAX_PASSAGE_BACK_STEPS = 2

/** Builds the [ScriptureEvent]s [DetectionEngine] emits, against the [translations] it loaded. */
internal class DetectionEvents(private val translations: List<EngineTranslation>) {

    fun buildRefEvent(state: UtteranceState, ref: ReferenceWatcher.Ref): ScriptureEvent? {
        val t = pickTranslation(state)
        // Fail closed on both lookups: never fabricate a verse the speaker didn't cite. A null
        // verseStart must not become "verse 1", and a verse number that doesn't exist in this
        // translation (misheard number, versification mismatch) must not silently substitute the
        // chapter's first verse — this event can go live unattended at 0.95 confidence.
        val verseStart = ref.verseStart ?: return null
        val verse = t.lookupVerse(ref.bookNum, ref.chapter, verseStart)
            ?.takeIf { !it.isHeader }
            ?: return null
        val verseEnd = ref.verseEnd?.takeIf { it > verse.verse }
        val endCode = verseEnd?.let { t.lookupVerse(ref.bookNum, ref.chapter, it)?.code }
        val bookName = t.bookName(ref.bookNum)
        val displayRef = if (verseEnd != null) "$bookName ${ref.chapter}:${verse.verse}-$verseEnd"
        else "$bookName ${ref.chapter}:${verse.verse}"

        // Tier 2 (sticky, no book spoken) is corroborated by the spoken verse content; tier 1
        // (explicit book+chapter+verse) is trusted outright.
        val confidence = when (ref.tier) {
            1 -> 0.95
            else -> {
                val agree = AgreementScorer.score(verse.text, state.transcript, state.translation)
                (0.60 + (agree * 0.30)).coerceIn(0.60, 0.88)
            }
        }
        val matchType = if (ref.tier == 2) "continuation" else "explicit"
        val type = if (ref.tier == 2) "scripture.continuation" else "scripture.detected"

        return ScriptureEvent(
            type = type,
            id = state.id,
            reference = ScriptureReference(
                bookId = ref.bookNum,
                bookName = bookName,
                chapter = ref.chapter,
                verseStart = verse.verse,
                verseEnd = verseEnd,
                displayRef = displayRef,
                canonicalCodeStart = verse.code,
                canonicalCodeEnd = endCode,
                numbering = t.numbering,
            ),
            verseText = verse.text,
            confidence = confidence,
            matchType = matchType,
            translation = t.abbreviation,
            tier = ref.tier,
        )
    }

    /** A single-verse detection of [verse] in [translation]. */
    fun buildEvent(
        id: String,
        verse: EngineVerse,
        translation: EngineTranslation,
        confidence: Double,
        matchType: String,
    ): ScriptureEvent {
        val bookName = translation.bookName(verse.bookNum)
        val displayRef = "$bookName ${verse.chapter}:${verse.verse}"
        return ScriptureEvent(
            type = "scripture.detected",
            id = id,
            reference = ScriptureReference(
                bookId = verse.bookNum,
                bookName = bookName,
                chapter = verse.chapter,
                verseStart = verse.verse,
                verseEnd = null,
                displayRef = displayRef,
                canonicalCodeStart = verse.code,
                canonicalCodeEnd = null,
                numbering = translation.numbering,
            ),
            verseText = verse.text,
            confidence = confidence,
            matchType = matchType,
            translation = translation.abbreviation,
        )
    }

    /**
     * Prefer the START of the contiguous covered passage: the reverse window keeps only the newest
     * words, so when a reading straddles verses N-1 and N, BM25 favors N — but the passage (and the
     * operator) started at N-1. Step back while the previous verse of the same chapter is itself
     * substantially present in either track (real case: Matthew 11:28-29 read across one window;
     * the engine offered 29, the operator wanted 28).
     */
    fun passageStart(t: EngineTranslation, hit: EngineVerse, state: UtteranceState): EngineVerse {
        var verse = hit
        repeat(MAX_PASSAGE_BACK_STEPS) {
            val prev = t.lookupVerse(verse.bookNum, verse.chapter, verse.verse - 1)?.takeIf { !it.isHeader }
            val coverage = prev?.let {
                maxOf(
                    AgreementScorer.coverage(it.text, state.transcript),
                    AgreementScorer.coverage(it.text, state.translation),
                )
            }
            if (prev == null || coverage == null || coverage < Config.continuationMinCoverage) return verse
            verse = prev
        }
        return verse
    }

    /**
     * Picks the translation whose verse text is displayed for an explicit/sticky/chapter-scope
     * detection: match the citing track's dominant script against each loaded bible's
     * content-derived [Script] (ids/language fields are filename-derived and unreliable — the
     * old hardcoded id lookup showed KJV text for Russian citations whenever filenames didn't
     * happen to match "RUS_RST"/"ENG_KJV"). The transcript track is the citing track; the
     * translation track only decides when the transcript is blank.
     */
    fun pickTranslation(state: UtteranceState): EngineTranslation {
        val citing = state.transcript.ifBlank { state.translation }
        val script = dominantScript(citing)
        return translations.firstOrNull { it.script == script } ?: translations.first()
    }
}

internal fun dominantScript(text: String): Script {
    var latin = 0
    var cyrillic = 0
    for (ch in text) {
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            ch in 'Ѐ'..'ӿ' -> cyrillic++
        }
    }
    return when {
        cyrillic > latin -> Script.CYRILLIC
        latin > 0 -> Script.LATIN
        else -> Script.OTHER
    }
}
