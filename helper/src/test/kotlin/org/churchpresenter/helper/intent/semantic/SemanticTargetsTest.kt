package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SemanticTargetsTest {

    private val context = ResolveContext(visibleTabs = setOf(Tabs.SONGS), language = "en")
    private val resolver = SemanticIntentResolver(RuleIntentResolver(), SemanticMatcher({ error("no model") }))

    private fun decide(target: CatalogTarget) = resolver.decide(listOf(Scored(target, 0.9f)), context, "x")

    private fun stepOf(resolution: Resolution) =
        assertIs<HelperAction.Highlight>(assertIs<Resolution.Act>(resolution).action).tour.steps.single()

    @Test
    fun `a control opens its settings page first, or nothing when it needs nothing open`() {
        val projection = CatalogTarget.Before.OnSettings(SettingsPage.PROJECTION)
        val onSettings = CatalogTarget.Control("x", "new_song", projection)
        val step = stepOf(decide(onSettings))
        assertEquals(HelperAction.OpenSettings(SettingsPage.PROJECTION), step.before)
        val anywhere = CatalogTarget.Control("x", "new_song", null)
        val bare = stepOf(decide(anywhere))
        assertNull(bare.before)
    }

    @Test
    fun `a control with a label this build lacks, or on a hidden tab, or nothing ranked, is not understood`() {
        assertEquals(Resolution.Unknown, decide(CatalogTarget.Control("x", "no_such_string_key", null)))
        assertNull(resolver.resolutionFor(CatalogTarget.Control("x", "no_such_string_key", null), context))
        val hidden = CatalogTarget.Control("x", "new_song", CatalogTarget.Before.OnTab(Tabs.MEDIA))
        assertEquals(Resolution.Unknown, decide(hidden))
        assertEquals(Resolution.Unknown, resolver.decide(emptyList(), context, "x"))
        assertEquals(Resolution.Unknown, decide(CatalogTarget.Request("qwerty zxcvb")))
    }
}
