package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.models.Tabs
import java.util.Locale

/** What the resolver may know about the app when it reads a request — plain values, nothing live. */
data class ResolveContext(
    val currentTab: Tabs? = null,
    val visibleTabs: Set<Tabs> = Tabs.entries.toSet(),
    val language: String = Locale.getDefault().language,
)

/** What a typed request came to. */
sealed interface Resolution {
    /** It asks for [action]. */
    data class Act(val action: HelperAction) : Resolution

    /** It could mean any of [options]; [question] asks which. */
    data class Clarify(val question: HelperText, val options: List<HelperAction>) : Resolution

    /** It was not understood. */
    data object Unknown : Resolution

    /** It was not understood, but it reads closest to [requests], best first — offered as chips. */
    data class Closest(val requests: List<SuggestedRequest>) : Resolution
}

/**
 * Turns a typed request into one of the helper's actions.
 *
 * The rules ([RuleIntentResolver]) read it first; `SemanticIntentResolver` adds a small local model for
 * what they miss. Either may only answer with a [HelperAction] — so whatever reads the request, the
 * helper can do no more than its own list, and still asks before doing it.
 */
fun interface IntentResolver {
    suspend fun resolve(input: String, context: ResolveContext): Resolution
}
