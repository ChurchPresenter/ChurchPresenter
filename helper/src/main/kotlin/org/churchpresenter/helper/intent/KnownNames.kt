package org.churchpresenter.helper.intent

/** The profile [text] names as whole words — the longest name when several fit — or null. */
internal fun ResolveContext.profileNamedIn(text: String): KnownProfile? =
    profiles.filter { mentions(text, it.name) }.maxByOrNull { normalize(it.name).length }

/** The output [text] names as whole words — the longest label when several fit — or null. */
internal fun ResolveContext.outputNamedIn(text: String): KnownOutput? =
    outputs.filter { mentions(text, it.label) }.maxByOrNull { normalize(it.label).length }

/**
 * [text] with [name] taken out, and the small words that tie a name on — "on the Stage profile",
 * "for livestream" — so what is left says only what the request is about.
 */
internal fun withoutName(text: String, name: String): String {
    val words = Regex.escape(normalize(name))
    return normalize(text)
        .replace(Regex("""\b(?:(?:on|in|for|of|to) )?(?:the |my |our )?$words(?: (?:profile|output|screen))?\b"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun mentions(text: String, name: String): Boolean {
    val words = normalize(name)
    return words.length >= MIN_NAME && Regex("""\b${Regex.escape(words)}\b""").containsMatchIn(normalize(text))
}

/** A name shorter than this is too likely to be an ordinary word of the request. */
private const val MIN_NAME = 2
