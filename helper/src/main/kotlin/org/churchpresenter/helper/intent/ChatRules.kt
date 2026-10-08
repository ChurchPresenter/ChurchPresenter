package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction

// Talking to the helper rather than asking it for something. Checked last, after every request rule.

private val COMMANDS = listOf(
    "commands", "command list", "list of commands", "all commands", "show commands", "what can i say",
    "what can i type", "what can i ask", "what do you understand", "what can you understand",
)
private val HELP_ONLY = setOf("help", "commands", "command", "list", "please", "wick", "show", "me", "the", "all")

/** "Help", "commands", "what can I type": the table of everything Wick understands. */
internal fun commandsRule(r: Request): Resolution? {
    val justHelp = r.words.all { it in HELP_ONLY } && r.words.any { it == "help" || it.startsWith("command") }
    return if (justHelp || r.hasPhrase(COMMANDS)) act(HelperAction.ShowCommands) else null
}

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
