package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.IntentResolver
import org.churchpresenter.helper.intent.KnownProfile
import org.churchpresenter.helper.intent.audienceProfile
import org.churchpresenter.helper.intent.helperTabName
import org.churchpresenter.helper.intent.profileNamedIn
import org.churchpresenter.helper.intent.withoutName
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.helper_hint_profile_row
import org.churchpresenter.strings.generated.resources.helper_hint_tab
import org.churchpresenter.strings.generated.resources.helper_row_on_page
import org.churchpresenter.strings.generated.resources.helper_settings_named
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.intent.glossary.Glossaries
import org.churchpresenter.helper.intent.normalize
import org.churchpresenter.helper.pack.WickPack
import org.churchpresenter.helper.pack.WickPacks
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
    private val pack: () -> WickPack? = { WickPacks.current.value },
) : IntentResolver {

    constructor() : this(RuleIntentResolver(), SemanticMatcher())

    override suspend fun resolve(input: String, context: ResolveContext): Resolution {
        val ruled = rules.resolveNow(input, context)
        // Only what the rules do not understand is read by the model — without a profile's name, which
        // says which profile and nothing about which setting.
        val named = context.profileNamedIn(input)
        val readings = if (ruled == Resolution.Unknown) {
            readingsOf(named?.let { withoutName(input, it.name) } ?: input, context)
        } else {
            emptyList()
        }
        if (readings.isEmpty()) return ruled
        val ranked = matcher.rank(readings) ?: return Resolution.Unknown
        return decide(ranked, context, readings.last(), named)
    }

    /**
     * What the ranking comes to: the action, when the best match is sure ([ACT]); else that one best
     * match, asked about ([GUESS]); with nothing that close, [Resolution.Unknown]. [named] is the profile
     * the request named, for a profile row.
     */
    internal fun decide(
        ranked: List<Scored>,
        context: ResolveContext,
        text: String,
        named: KnownProfile? = null,
    ): Resolution {
        val reachable = ranked.filter { it.target.reachableIn(context) }
        val best = reachable.firstOrNull() ?: return Resolution.Unknown
        val action = (resolutionFor(best.target, context, named) as? Resolution.Act)?.action
        val words = keywordScores(text)
        val nudge = (best.target as? CatalogTarget.Suggested)?.let { KEYWORDS * (words[it.request] ?: 0.0) } ?: 0.0
        return when {
            action == null -> Resolution.Unknown
            best.score >= ACT -> Resolution.Act(action)
            best.score + nudge >= GUESS -> Resolution.DidYouMean(labelOf(best.target, action), action)
            else -> Resolution.Unknown
        }
    }

    /** What [target] leads to — through the rules for the requests they already understand. */
    internal fun resolutionFor(
        target: CatalogTarget,
        context: ResolveContext,
        named: KnownProfile? = null,
    ): Resolution? = when (target) {
        is CatalogTarget.Suggested -> rules.resolveNow(target.request.request, context)
        is CatalogTarget.Request -> rules.resolveNow(target.text, context)
        is CatalogTarget.Control -> highlight(target)?.let(Resolution::Act)
        is CatalogTarget.ProfileRow -> profileRow(target, named ?: context.audienceProfile)?.let(Resolution::Act)
        is CatalogTarget.PageRow -> pageRow(target)?.let(Resolution::Act)
        is CatalogTarget.Settings -> Resolution.Act(HelperAction.OpenSettings(target.page))
        is CatalogTarget.Tab -> Resolution.Act(HelperAction.SelectTab(target.tab))
        is CatalogTarget.Shortcut -> Resolution.Act(HelperAction.ShowShortcut(target.action))
        is CatalogTarget.PackTour -> pack()?.tours?.get(target.id)?.action?.let(Resolution::Act)
    }?.takeIf { it != Resolution.Unknown }

    /** How "Did you mean …?" names [target], which leads to [action]. */
    private fun labelOf(target: CatalogTarget, action: HelperAction): HelperText = when (target) {
        is CatalogTarget.Suggested -> HelperText.Res(target.request.label)
        is CatalogTarget.Request -> HelperText.Plain(target.text)
        is CatalogTarget.Control, is CatalogTarget.ProfileRow, is CatalogTarget.PageRow ->
            (action as? HelperAction.Highlight)?.label ?: HelperText.Plain(target.format())
        is CatalogTarget.Settings ->
            helperText(Res.string.helper_settings_named, HelperText.Res(settingsPageLabel(target.page)))
        is CatalogTarget.Tab -> helperText(Res.string.helper_hint_tab, helperTabName(target.tab))
        is CatalogTarget.Shortcut -> HelperText.Res(target.action.descriptionRes)
        is CatalogTarget.PackTour -> (action as? HelperAction.Highlight)?.label ?: HelperText.Plain(target.id)
    }

    /** One step: Settings opened on the row's page, and the row ringed. */
    private fun pageRow(row: CatalogTarget.PageRow): HelperAction? {
        val label = Res.allStringResources[row.labelKey]?.let { HelperText.Res(it) } ?: return null
        val step = GuideStep(
            GuideTargets.settingsRow(row.labelKey),
            helperText(Res.string.helper_hint_control, label),
            HelperAction.OpenSettings(row.page),
        )
        val where = helperText(Res.string.helper_row_on_page, label, HelperText.Res(settingsPageLabel(row.page)))
        return HelperAction.Highlight(GuideTour(listOf(step)), where)
    }

    /** One step: Settings opened on [profile]'s page for the row, and the row ringed. */
    private fun profileRow(row: CatalogTarget.ProfileRow, profile: KnownProfile?): HelperAction? {
        val label = Res.allStringResources[row.labelKey]?.let { HelperText.Res(it) } ?: return null
        val focus = ProfileFocus(profileId = profile?.id, page = row.page, rowKey = row.labelKey)
        val hint = if (profile != null && profile.name.isNotBlank()) {
            helperText(Res.string.helper_hint_profile_row, label, profile.name)
        } else {
            helperText(Res.string.helper_hint_control, label)
        }
        val step = GuideStep(
            GuideTargets.settingsRow(row.labelKey),
            hint,
            HelperAction.OpenSettings(SettingsPage.PROFILES, focus),
        )
        val where = profilePageLabel(row.page)
            ?.let { helperText(Res.string.helper_row_on_page, label, HelperText.Res(it)) }
        return HelperAction.Highlight(GuideTour(listOf(step)), where ?: label)
    }

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

        /** At or above this, below [ACT], the best match is offered as "Did you mean …?". */
        const val GUESS = 0.45

        /** How much the keyword likeness adds to a chip's score — a nudge; the model leads. */
        private const val KEYWORDS = 0.1

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
