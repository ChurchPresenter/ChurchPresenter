package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction

// Talking to the helper rather than asking it for something. Checked last, after every request rule.

/** "Hello", "help", "what can you do", "thanks" — a short message that is only that. */
internal fun chatRule(r: Request): Resolution? = when {
    r.hasPhrase(Vocabulary.THANKS) && r.words.size <= SHORT_CHAT -> act(HelperAction.Thanks)
    r.hasPhrase(Vocabulary.ABOUT_HELPER) -> act(HelperAction.Greet)
    r.words.size <= SHORT_CHAT && r.words.all { it in Vocabulary.GREETING || it in Vocabulary.FILLER } ->
        act(HelperAction.Greet)
    else -> null
}

/** How many words a greeting or a thank-you runs to before it is probably a request with manners. */
private const val SHORT_CHAT = 4
