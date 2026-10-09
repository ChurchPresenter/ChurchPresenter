package org.churchpresenter.helper.intent.semantic

import kotlinx.coroutines.runBlocking
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What the resolver makes of a ranking: driven with hand-made rankings, so each rule — act only when
 * sure, otherwise offer the closest chips, never offer what cannot be reached — is shown without the
 * model's own judgement in the way.
 */
class SemanticIntentResolverTest {

    private val rules = RuleIntentResolver()
    private val context = ResolveContext(visibleTabs = Tabs.entries.toSet(), language = "en")
    private val resolver = SemanticIntentResolver(rules, SemanticMatcher({ error("no model in this test") }))

    private fun scored(target: CatalogTarget, score: Float) = Scored(target, score)

    @Test
    fun `a sure match is acted on, through the rules when it is one of their requests`() {
        val ranked = listOf(scored(CatalogTarget.Suggested(SuggestedRequest.CLEAR), 0.82f))

        val resolution = resolver.decide(ranked, context, "make the screen go black")

        assertEquals(rules.resolveNow(SuggestedRequest.CLEAR.request, context), resolution)
    }

    @Test
    fun `a sure match on a Settings page, a tab or a shortcut opens or shows it`() {
        fun actOn(target: CatalogTarget) = resolver.decide(listOf(scored(target, 0.9f)), context, "anything")

        assertEquals(
            Resolution.Act(HelperAction.OpenSettings(SettingsPage.PROJECTION)),
            actOn(CatalogTarget.Settings(SettingsPage.PROJECTION)),
        )
        assertEquals(Resolution.Act(HelperAction.SelectTab(Tabs.MEDIA)), actOn(CatalogTarget.Tab(Tabs.MEDIA)))
        assertEquals(
            Resolution.Act(HelperAction.ShowShortcut(ShortcutAction.entries.first())),
            actOn(CatalogTarget.Shortcut(ShortcutAction.entries.first())),
        )
    }

    @Test
    fun `a sure match on a tagged control rings it, opening where it lives first`() {
        val control = CatalogTarget.Control("songs.new", "new_song", CatalogTarget.Before.OnTab(Tabs.SONGS))

        val action = assertIs<Resolution.Act>(resolver.decide(listOf(scored(control, 0.9f)), context, "x")).action

        val step = assertIs<HelperAction.Highlight>(action).tour.steps.single()
        assertEquals("songs.new", step.target.id)
        assertEquals(HelperAction.SelectTab(Tabs.SONGS), step.before)
    }

    @Test
    fun `a match short of sure is offered as chips, best first, and never acted on`() {
        val ranked = listOf(
            scored(CatalogTarget.Settings(SettingsPage.SYSTEM), 0.6f),
            scored(CatalogTarget.Suggested(SuggestedRequest.CLEAR), 0.55f),
            scored(CatalogTarget.Suggested(SuggestedRequest.NEXT_SLIDE), 0.4f),
        )

        val resolution = resolver.decide(ranked, context, "hide everything")

        assertEquals(Resolution.Closest(listOf(SuggestedRequest.CLEAR, SuggestedRequest.NEXT_SLIDE)), resolution)
    }

    @Test
    fun `alike words lift a chip the model ranks a little lower`() {
        val ranked = listOf(
            scored(CatalogTarget.Suggested(SuggestedRequest.REMOTE), 0.33f),
            scored(CatalogTarget.Suggested(SuggestedRequest.CHORDS), 0.29f),
        )

        val chips = assertIs<Resolution.Closest>(resolver.decide(ranked, context, "how do i add chrods")).requests

        assertEquals(SuggestedRequest.CHORDS, chips.first(), "\"chrods\" is one letter swap from \"chords\"")
    }

    @Test
    fun `nothing close enough is not understood`() {
        val ranked = listOf(scored(CatalogTarget.Suggested(SuggestedRequest.CLEAR), 0.1f))

        assertEquals(Resolution.Unknown, resolver.decide(ranked, context, "order a pizza"))
    }

    @Test
    fun `a tab the operator has hidden is neither acted on nor offered`() {
        val withoutWeb = context.copy(visibleTabs = Tabs.entries.toSet() - Tabs.WEB)
        val ranked = listOf(
            scored(CatalogTarget.Tab(Tabs.WEB), 0.9f),
            scored(CatalogTarget.Control("web.url", "web_url", CatalogTarget.Before.OnTab(Tabs.WEB)), 0.85f),
        )

        assertEquals(Resolution.Unknown, resolver.decide(ranked, withoutWeb, "open a website"))
    }

    @Test
    fun `what the rules understand never reaches the model`() {
        val resolution = runBlocking { resolver.resolve("clear the screen", context) }

        assertEquals(rules.resolveNow("clear the screen", context), resolution)
    }

    @Test
    fun `without its model the helper reads requests with the rules alone`() {
        val resolution = runBlocking { resolver.resolve("the band wants something odd", context) }

        assertEquals(Resolution.Unknown, resolution)
    }

    @Test
    fun `the model reads the app language's reading and the text as typed`() {
        assertEquals(listOf("clear the screen"), SemanticIntentResolver.readingsOf("Clear the screen!", context))
        assertTrue(SemanticIntentResolver.readingsOf("  ", context).isEmpty())
    }
}
