package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.models.Tabs
import java.util.Locale

/** What the resolver may know about the app when it reads a request — plain values, nothing live. */
data class ResolveContext(
    val currentTab: Tabs? = null,
    val visibleTabs: Set<Tabs> = Tabs.entries.toSet(),
    val language: String = Locale.getDefault().language,
    /** The operator's output profiles, so a request can name one. */
    val profiles: List<KnownProfile> = emptyList(),
    /** Every output and the profile it draws with, in the Projection page's order. */
    val outputs: List<KnownOutput> = emptyList(),
    /**
     * The loaded Bible's books, each as the names it goes by — the Bible's own, then the standard English
     * one — so a typed book is matched as that Bible names it. Empty when none is loaded.
     */
    val bibleBooks: List<List<String>> = emptyList(),
)

/** An output profile, by the name the operator gave it; [stageMonitor] when it is one. */
data class KnownProfile(val id: String, val name: String, val stageMonitor: Boolean = false)

/**
 * One output as the Projection page lists it: its [label] ("Screen 2", an NDI output's name), its
 * card's [kind] (`screen`, `browser`, `ndi`, `omt`) and [index] there, and the profile it draws with.
 */
data class KnownOutput(val label: String, val kind: String, val index: Int, val profileId: String?)

/** The profile the first screen output — the audience screen — draws with. */
val ResolveContext.audienceProfile: KnownProfile?
    get() = outputs.firstOrNull { it.kind == "screen" }?.profileId?.let { id -> profiles.find { it.id == id } }

/** What a typed request came to. */
sealed interface Resolution {
    /** It asks for [action]. */
    data class Act(val action: HelperAction) : Resolution

    /** It could mean any of [options]; [question] asks which. */
    data class Clarify(val question: HelperText, val options: List<HelperAction>) : Resolution

    /** It was not understood. */
    data object Unknown : Resolution

    /** It was not understood for sure, but reads most like [action], described as [label] — offered as a question. */
    data class DidYouMean(val label: HelperText, val action: HelperAction) : Resolution
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
