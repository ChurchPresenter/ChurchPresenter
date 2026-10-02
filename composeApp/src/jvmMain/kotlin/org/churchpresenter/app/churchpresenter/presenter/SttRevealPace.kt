package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.stt.STTSegment
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.RSVP_FLASH_PHRASE
import org.churchpresenter.settings.STTSettings
import kotlin.math.roundToLong

/**
 * How fast captions are revealed: the pace each presentation style steps at, the speaker's own pace
 * measured from the STT server's timings, and how long an RSVP flash is held. The cursor arithmetic
 * the pace drives lives in `SttDripFeed.kt`.
 */

/** What the reveal steps over: a letter, a whole word, a whole segment, or an RSVP flash of words. */
internal enum class RevealUnit { LETTER, WORD, SEGMENT, FLASH }

/**
 * How the caption is revealed: [delayMs] per character, stepping a [unit] at a time. A [RevealUnit.FLASH]
 * steps [wordsPerStep] words -- or, at [RSVP_FLASH_PHRASE], a phrase (see [phraseEnd]) -- and is held for
 * at least [minMsPerWord] a word: see [flashDelayMs].
 */
internal data class RevealPace(
    val delayMs: Long,
    val unit: RevealUnit,
    val wordsPerStep: Int = 1,
    val minMsPerWord: Long = 0,
)

/** A minute in milliseconds, to turn RSVP's words a minute into the time each word is held for. */
private const val MS_PER_MINUTE = 60_000L
private val RSVP_WPM_RANGE = 60..1000
private val RSVP_WORDS_RANGE = 1..3

private const val READING_MS_PER_SECOND = 1000L

/**
 * The pace [s] reveals its captions at, or null when they appear all at once.
 *
 * The drip feed types letter by letter at its own speed -- or, matching the speaker, at the
 * speaker's measured [speakerMsPerChar] once there is one; without the drip feed, matching the
 * speaker brings the words a word at a time at that pace, starting at [TYPICAL_SPEECH_MS_PER_CHAR]
 * until it can be measured. The reading-speed limit never lets words
 * arrive faster than that many characters a second: on its own it brings them a word at a time, and
 * otherwise it slows the reveal down to it when the reveal is faster. RSVP always paces: a flash of
 * words at a time, at the speaker's pace or none, never past its words a minute. Pop-on never types -- its words
 * arrive in whole segments -- so there the limit holds each segment back instead; the ticker keeps
 * its own scroll speed, and both ignore the speaker's.
 */
internal fun revealPace(s: STTSettings, speakerMsPerChar: Long? = null): RevealPace? {
    val reading = s.reading
    val cps = reading.readingSpeedCps.coerceAtLeast(1)
    val limitMs = if (reading.readingSpeedLimit) READING_MS_PER_SECOND / cps else 0L
    val speaker = speakerMsPerChar?.takeIf { s.matchSpeakerPace }
    return when {
        reading.style == CAPTION_STYLE_RSVP -> RevealPace(
            // Matching the speaker, their pace per letter; at a fixed speed, only the words a minute
            delayMs = maxOf(if (s.matchSpeakerPace) speaker ?: TYPICAL_SPEECH_MS_PER_CHAR else 0L, limitMs),
            unit = RevealUnit.FLASH,
            wordsPerStep = reading.rsvpWordsPerFlash.let {
                if (it == RSVP_FLASH_PHRASE) it else it.coerceIn(RSVP_WORDS_RANGE)
            },
            minMsPerWord = MS_PER_MINUTE / reading.rsvpWpm.coerceIn(RSVP_WPM_RANGE),
        )
        reading.style == CAPTION_STYLE_POP_ON -> RevealPace(limitMs, RevealUnit.SEGMENT).takeIf { limitMs > 0 }
        // A ticker adds what is new as one piece, so it types nothing either: the limit paces words
        reading.style == CAPTION_STYLE_TICKER -> RevealPace(limitMs, RevealUnit.WORD).takeIf { limitMs > 0 }
        s.dripFeedEnabled -> {
            val typing = speaker ?: s.dripFeedSpeed.toLong().coerceAtLeast(1L)
            RevealPace(maxOf(typing, limitMs), RevealUnit.LETTER)
        }
        s.matchSpeakerPace -> RevealPace(maxOf(speaker ?: TYPICAL_SPEECH_MS_PER_CHAR, limitMs), RevealUnit.WORD)
        limitMs > 0 -> RevealPace(limitMs, RevealUnit.WORD)
        else -> null
    }
}

/**
 * An ordinary speaking pace -- about 150 words a minute, 14 characters a second -- for the words
 * matching a speaker who has not said enough yet to be measured. Without it the first words of a
 * service would land all at once and only the ones after them be paced.
 */
internal const val TYPICAL_SPEECH_MS_PER_CHAR = 70L

/** How much of the newest timed speech the speaker's pace is measured over, in seconds. */
private const val PACE_WINDOW_SECONDS = 10.0

/** Less timed speech than this and there is no pace yet -- one short phrase says little. */
private const val MIN_PACE_SECONDS = 2.0

/** A segment shorter than this carries no usable timing -- including one the server sent none for. */
private const val MIN_SEGMENT_SECONDS = 0.3

/** The pace is held between a very fast talker (50 characters a second) and a very slow one (4). */
private const val FASTEST_MS_PER_CHAR = 20L
private const val SLOWEST_MS_PER_CHAR = 250L

/**
 * How long the speaker takes per character, in milliseconds, or null while it cannot be told.
 *
 * Measured from the audio timings the STT server gives each segment: the characters of the newest
 * completed segments over the seconds of speech they span, taken back until about
 * [PACE_WINDOW_SECONDS] of speech -- enough to smooth one quick phrase out, little enough to follow a
 * speaker who slows down or speeds up within a sentence or two. A segment without timings (a server
 * that sends none reads them as 0) is skipped, and with less than [MIN_PACE_SECONDS] of timed speech
 * there is no pace: the caller's fixed speed stands in.
 */
internal fun speakerMsPerChar(segments: List<STTSegment>): Long? {
    var chars = 0
    var seconds = 0.0
    for (segment in segments.asReversed()) {
        if (seconds >= PACE_WINDOW_SECONDS) break
        val span = segment.end - segment.start
        val text = normalizeSegmentText(segment.text)
        if (segment.completed && span >= MIN_SEGMENT_SECONDS && text.isNotEmpty()) {
            chars += text.length
            seconds += span
        }
    }
    if (seconds < MIN_PACE_SECONDS || chars == 0) return null
    return (seconds * READING_MS_PER_SECOND / chars).roundToLong().coerceIn(FASTEST_MS_PER_CHAR, SLOWEST_MS_PER_CHAR)
}

/**
 * Where one RSVP flash that starts at [from] in [text] ends: after [words] words, or, at
 * [RSVP_FLASH_PHRASE], at the end of the phrase -- see [phraseEnd].
 */
internal fun flashEnd(text: String, from: Int, words: Int): Int {
    if (words == RSVP_FLASH_PHRASE) return phraseEnd(text, from)
    var end = from
    repeat(words.coerceAtLeast(1)) { end = nextWordEnd(text, end) }
    return end
}

/** A phrase flash never holds more words than this, however the words run on. */
private const val PHRASE_MAX_WORDS = 4

/** The words a phrase flash aims for when nothing in the text breaks it sooner. */
private const val PHRASE_WORDS = 3

/** A word this short or shorter -- "of", "the", "and", "to" -- belongs with the word after it. */
private const val SHORT_WORD_LETTERS = 3

private const val PHRASE_PUNCTUATION = ",.;:!?\u2026\u2014"

/**
 * Where the natural phrase starting at [from] in [text] ends, for RSVP's chunking: words are taken
 * until one ends in punctuation, or until [PHRASE_WORDS] are taken and the last of them is a full
 * word, or until [PHRASE_MAX_WORDS]. A short word is never left at the end of a phrase while there is
 * room, since it reads with the word after it -- "the", "of", "and" -- so "Blessed are the
 * peacemakers," is one phrase and "for they shall" the next. Punctuation and word length rather than
 * lists of words, so it works the same in every language a service is captioned in.
 */
internal fun phraseEnd(text: String, from: Int): Int {
    var end = from.coerceIn(0, text.length)
    var words = 0
    var phraseOver = false
    while (!phraseOver && words < PHRASE_MAX_WORDS) {
        val next = nextWordEnd(text, end)
        val word = text.substring(end, next).trim()
        end = next
        if (word.isEmpty()) {
            phraseOver = true
        } else {
            words++
            val letters = word.count { it.isLetterOrDigit() }
            phraseOver = word.last() in PHRASE_PUNCTUATION || (words >= PHRASE_WORDS && letters > SHORT_WORD_LETTERS)
        }
    }
    return end
}

/** How many words lie between [from] and [to] in [text] -- the size of the flash a step just showed. */
internal fun wordsBetween(text: String, from: Int, to: Int): Int =
    text.substring(from.coerceIn(0, text.length), to.coerceIn(0, text.length)).split(' ').count { it.isNotBlank() }

/**
 * How many words the flash ending [text] holds when an RSVP output opens on text it did not reveal
 * itself: the last phrase of it, or [words] at a set count.
 */
internal fun lastFlashWords(text: String, words: Int): Int {
    if (words != RSVP_FLASH_PHRASE) return words.coerceAtLeast(1)
    var start = 0
    var last = 0
    while (start < text.length) {
        val end = phraseEnd(text, start)
        if (end == start) break
        last = wordsBetween(text, start, end)
        start = end
    }
    return last.coerceAtLeast(1)
}

/**
 * How long the RSVP flash of [text] from [from] to [to] is held: its characters at [RevealPace.delayMs]
 * each -- the space before it included, so a sentence's flashes add up to the time it was spoken in --
 * but never less than [RevealPace.minMsPerWord] a word, the words-a-minute ceiling. It is
 * never shortened to catch up: a flash too fast to read helps nobody, so a speaker past the ceiling
 * is fallen behind instead, and caught up with when they pause.
 */
internal fun flashDelayMs(pace: RevealPace, text: String, from: Int, to: Int): Long {
    val span = text.substring(from.coerceIn(0, text.length), to.coerceIn(0, text.length))
    val words = span.split(' ').count { it.isNotEmpty() }.coerceAtLeast(1)
    return maxOf(pace.delayMs * span.length, pace.minMsPerWord * words)
}
