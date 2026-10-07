package org.churchpresenter.helper

import androidx.compose.runtime.mutableStateListOf
import org.churchpresenter.helper.action.describe
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_shortcut_is
import org.churchpresenter.strings.generated.resources.helper_unknown
import org.jetbrains.compose.resources.StringResource

/** One line of the conversation so far, above the reply that is still waiting on the operator. */
sealed interface ThreadEntry {
    /** What the operator typed, picked or pressed. */
    data class Operator(val text: String) : ThreadEntry

    /** What Wick said; [topic] is the tag it carried and [about] the suggestion id it came from. */
    data class Wick(
        val text: HelperText,
        val topic: StringResource? = null,
        val about: String? = null,
    ) : ThreadEntry
}

/** The conversation so far, oldest first, kept to the last [THREAD_LIMIT] lines. */
class HelperThread {
    val entries = mutableStateListOf<ThreadEntry>()

    /** Adds what the operator said or pressed. */
    fun said(text: String) = add(ThreadEntry.Operator(text))

    /** Adds a line Wick showed outside a reply — a suggestion or a tip. */
    fun keep(text: HelperText, topic: StringResource? = null, about: String? = null) =
        add(ThreadEntry.Wick(text, topic, about))

    fun clear() = entries.clear()

    internal fun add(entry: ThreadEntry) {
        entries += entry
        while (entries.size > THREAD_LIMIT) entries.removeAt(0)
    }
}

/** What [this] reply said, as a line to keep in the conversation once it is answered; null for none. */
internal fun HelperReply.summary(undoLabel: HelperText?): HelperText? = when (this) {
    is HelperReply.Confirm -> action.describe(undoLabel)
    is HelperReply.Clarify -> question
    is HelperReply.Message -> text
    is HelperReply.Shortcut ->
        helperText(Res.string.helper_shortcut_is, helperText(action.descriptionRes), HelperText.KeyFor(action))
    HelperReply.Unknown -> helperText(Res.string.helper_unknown)
    is HelperReply.Touring -> tour.steps[index].hint
    HelperReply.Idle, HelperReply.DisplaySetup -> null
}

private const val THREAD_LIMIT = 60
