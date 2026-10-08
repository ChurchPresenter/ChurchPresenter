package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.glossary.Glossaries

/**
 * Lower case, apostrophes made plain, Arabic vowel marks dropped, punctuation other than `: - #`
 * turned to spaces, runs of spaces collapsed.
 */
internal fun normalize(input: String): String =
    input.lowercase()
        .replace(Regex("[’‘`ʻʼ]"), "'")
        .replace('：', ':')
        .replace(Regex("[\u064B-\u0652\u0640]"), "")
        .replace(Regex("""[^\p{L}\p{M}\p{N}:#'\-\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

/** Whether [phrase] appears here as whole words. */
internal fun String.containsPhrase(phrase: String): Boolean = " $this ".contains(" $phrase ")

/** Whether a word here starts with [stem] — "songs" and "song's" both name the Songs tab. */
internal fun String.containsWordPrefix(stem: String): Boolean =
    if (' ' in stem) containsPhrase(stem) else split(' ').any { it.startsWith(stem) }

/** One typed request, normalized, as the rules read it. */
internal class Request(val text: String, val words: List<String>, val context: ResolveContext) {
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
            .map { reading -> RULES.firstNotNullOfOrNull { it(Request(reading, reading.split(' '), context)) } }
            .firstOrNull { it != null } ?: Resolution.Unknown
    }

    private companion object {
        /** In order: the more specific reading of a phrase comes before the looser one. */
        val RULES: List<(Request) -> Resolution?> = listOf(
            ::undoRule,
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
            ::convertSongsRule,
            ::songLibraryRule,
            ::calendarRule,
            ::songTranslationRule,
            ::songChordsRule,
            ::bibleTranslationRule,
            ::mediaTopicsRule,
            ::navigationRule,
            ::openSettingsRule,
            ::verseRule,
            ::switchTabRule,
            ::nextOrPreviousRule,
            ::clearRule,
            ::takeRule,
            ::outputsRule,
            // Last: "help me set up the screens" and "thanks, now clear it" are requests first.
            ::chatRule,
        )
    }
}
