package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.IntentResolver
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.intent.glossary.Glossaries
import org.churchpresenter.helper.intent.normalize
import org.churchpresenter.helper.suggest.keywordScores
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allStringResources
import org.churchpresenter.strings.generated.resources.helper_hint_control

/**
 * Wick's reader: the rules first, then — only for what they do not understand — a small sentence model
 * that matches the request by meaning against a catalog generated from the codebase.
 *
 * Whatever the model finds still becomes one of the helper's own actions and goes through the same
 * confirmation; the model never acts on a weak match. When it is sure ([ACT]) it answers with that
 * action; otherwise it offers the chips it reads closest to; with nothing close it is [Resolution.Unknown].
 */
class SemanticIntentResolver internal constructor(
    private val rules: RuleIntentResolver,
    private val matcher: SemanticMatcher,
) : IntentResolver {

    constructor() : this(RuleIntentResolver(), SemanticMatcher())

    override suspend fun resolve(input: String, context: ResolveContext): Resolution {
        val ruled = rules.resolveNow(input, context)
        // Only what the rules do not understand is read by the model.
        val readings = if (ruled == Resolution.Unknown) readingsOf(input, context) else emptyList()
        if (readings.isEmpty()) return ruled
        val ranked = matcher.rank(readings) ?: return Resolution.Unknown
        return decide(ranked, context, readings.last())
    }

    /**
     * What the ranking comes to: the action, when the best match is sure; else the chips closest to
     * [text] — by the model's score, nudged by how alike the words are ([keywordScores]), which catches
     * the typos a model reading whole words misses. Measured on the requests the rules miss, the blend
     * reaches more than either alone. (Offering the nearest actions of every kind instead reached fewer:
     * a Settings page or a near-duplicate phrase crowds the right chip out of the first three.)
     */
    internal fun decide(ranked: List<Scored>, context: ResolveContext, text: String): Resolution {
        val reachable = ranked.filter { it.target.reachableIn(context) }
        val best = reachable.firstOrNull()
        if (best != null && best.score >= ACT) {
            (resolutionFor(best.target, context) as? Resolution.Act)?.let { return it }
        }
        val words = keywordScores(text)
        val chips = reachable.mapNotNull { scored ->
            val chip = (scored.target as? CatalogTarget.Suggested)?.request ?: return@mapNotNull null
            chip to scored.score + KEYWORDS * (words[chip] ?: 0.0)
        }
            .filter { (_, score) -> score >= CHIP_FLOOR }
            .sortedByDescending { (_, score) -> score }
            .map { (request, _) -> request }
            .distinct().take(CHIPS)
        return if (chips.isEmpty()) Resolution.Unknown else Resolution.Closest(chips)
    }

    /** What [target] leads to — through the rules for the requests they already understand. */
    internal fun resolutionFor(target: CatalogTarget, context: ResolveContext): Resolution? = when (target) {
        is CatalogTarget.Suggested -> rules.resolveNow(target.request.request, context)
        is CatalogTarget.Request -> rules.resolveNow(target.text, context)
        is CatalogTarget.Control -> highlight(target)?.let(Resolution::Act)
        is CatalogTarget.Settings -> Resolution.Act(HelperAction.OpenSettings(target.page))
        is CatalogTarget.Tab -> Resolution.Act(HelperAction.SelectTab(target.tab))
        is CatalogTarget.Shortcut -> Resolution.Act(HelperAction.ShowShortcut(target.action))
    }?.takeIf { it != Resolution.Unknown }

    private fun highlight(control: CatalogTarget.Control): HelperAction? {
        val label = Res.allStringResources[control.labelKey]?.let { HelperText.Res(it) } ?: return null
        val before = when (val place = control.before) {
            is CatalogTarget.Before.OnTab -> HelperAction.SelectTab(place.tab)
            is CatalogTarget.Before.OnSettings -> HelperAction.OpenSettings(place.page)
            null -> null
        }
        val step = GuideStep(GuideTarget(control.id), helperText(Res.string.helper_hint_control, label), before)
        return HelperAction.Highlight(GuideTour(listOf(step)), label)
    }

    private fun CatalogTarget.reachableIn(context: ResolveContext): Boolean = when (this) {
        is CatalogTarget.Tab -> tab in context.visibleTabs
        is CatalogTarget.Control ->
            (before as? CatalogTarget.Before.OnTab)?.tab?.let { it in context.visibleTabs } ?: true
        else -> true
    }

    internal companion object {
        /** At or above this the model's best match is acted on (after the usual confirmation). */
        const val ACT = 0.7f

        /** Below this a chip is too far from the request to offer. */
        const val CHIP_FLOOR = 0.2

        /** How much the keyword likeness adds to a chip's score — a nudge; the model leads. */
        private const val KEYWORDS = 0.1

        private const val CHIPS = 4

        /**
         * The English readings of [input] the model reads: the app language's glossary rewrite, and the
         * text as typed — the model reads English, and two readings keep a request to two passes.
         */
        fun readingsOf(input: String, context: ResolveContext): List<String> {
            val text = normalize(input)
            if (text.isEmpty()) return emptyList()
            // The first reading is the app language's rewrite when it has one, else the text itself.
            return listOf(Glossaries.readings(text, context.language).first(), text).distinct()
        }
    }
}
