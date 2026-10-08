package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.glossary.Glossaries

/**
 * Lower case, apostrophes made plain, Arabic vowel marks dropped, punctuation other than `: - #`
 * turned to spaces, runs of spaces collapsed.
 */
internal fun normalize(input: String): String =
    asciiDigits(input).lowercase()
        .replace(Regex("[’‘`ʻʼ]"), "'")
        .replace('：', ':')
        .replace(Regex("[\u064B-\u0652\u0640]"), "")
        .replace(Regex("""[^\p{L}\p{M}\p{N}:#'\-\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** Every script's digits as 0–9 — "۵", "५", "๕" are a 5 to the rules, as in a verse or a countdown. */
private fun asciiDigits(text: String): String =
    if (text.none { it.isDigit() && it !in '0'..'9' }) {
        text
    } else {
        buildString { text.forEach { append(if (it.isDigit()) ('0' + Character.digit(it, 10)) else it) } }
    }

/** Whether [phrase] appears here as whole words. */
internal fun String.containsPhrase(phrase: String): Boolean = " $this ".contains(" $phrase ")

/** Whether a word here starts with [stem] — "songs" and "song's" both name the Songs tab. */
internal fun String.containsWordPrefix(stem: String): Boolean =
    if (' ' in stem) containsPhrase(stem) else split(' ').any { it.startsWith(stem) }

/** One typed request, normalized, as the rules read it. */
internal class Request(
    val text: String,
    val words: List<String>,
    val context: ResolveContext,
    /** What was typed, before normalizing — for text the operator wants shown as written. */
    val raw: String = text,
) {
    val first: String get() = words.first()
    fun has(set: Set<String>) = words.any { it in set }
    fun hasPhrase(phrases: List<String>) = phrases.any { text.containsPhrase(it) }
    fun says(vararg phrases: String) = phrases.any { text.containsPhrase(it) }
    val isQuestion get() = hasPhrase(Vocabulary.WHERE)
}

/** The request comes to [action]. */
internal fun act(action: HelperAction): Resolution = Resolution.Act(action)

/**
 * The helper's rule-based reader. Each rule looks for the words that mean one request; the first
 * rule that matches decides. Plain Kotlin with no state, so every phrase it handles is one test row.
 */
class RuleIntentResolver : IntentResolver {

    override suspend fun resolve(input: String, context: ResolveContext): Resolution = resolveNow(input, context)

    /** [resolve] without the suspension, for callers and tests that have no coroutine. */
    fun resolveNow(input: String, context: ResolveContext): Resolution {
        val text = normalize(input)
        if (text.isEmpty()) return Resolution.Unknown
        return Glossaries.readings(text, context.language)
            .map { reading ->
                val request = Request(reading, reading.split(' '), context, raw = input.trim())
                RULES.firstNotNullOfOrNull { it(request) }
            }
            .firstOrNull { it != null } ?: Resolution.Unknown
    }

    private companion object {
        /** In order: the more specific reading of a phrase comes before the looser one. */
        val RULES: List<(Request) -> Resolution?> = listOf(
            ::undoRule,
            // First: "help" alone asks for the list, before display setup reads "help" as a setup.
            ::commandsRule,
            ::setupWizardRule,
            // What shows on the stage monitor, before its setup below.
            ::stageTopicsRule,
            // Before display setup: "set up a stage monitor" is a profile, not the audience screen.
            ::outputTopicsRule,
            ::lowerThirdRule,
            ::displaySetupRule,
            ::backgroundColorRule,
            ::fontSizeRule,
            ::shortcutRule,
            // Before the tours of the same things: "a 5 minute countdown" starts one.
            ::countdownRule,
            ::announcementRule,
            ::addToScheduleRule,
            ::scheduleStepRule,
            ::whatsLiveRule,
            ::versionRule,
            // Before the converter: "import from Planning Center" is not a song conversion.
            ::featureTopicsRule,
            ::convertSongsRule,
            ::songLibraryRule,
            ::calendarRule,
            ::songTranslationRule,
            ::songChordsRule,
            ::bibleTranslationRule,
            ::mediaTopicsRule,
            ::navigationRule,
            ::openSettingsRule,
            // Before the tab rule: "show song 245" is that song, not the Songs tab.
            ::namedSongRule,
            ::verseRule,
            ::switchTabRule,
            ::nextOrPreviousRule,
            ::clearRule,
            ::takeRule,
            ::outputsRule,
            // Late: "show amazing grace" is a song only once nothing else claimed it.
            ::songLookupRule,
            // Last: "help me set up the screens" and "thanks, now clear it" are requests first.
            ::chatRule,
        )
    }
}
