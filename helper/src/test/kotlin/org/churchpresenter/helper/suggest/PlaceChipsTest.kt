package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.guide.WickPlace
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaceChipsTest {

    private val resolver = RuleIntentResolver()
    private val mainTabs = Tabs.entries.map(GuideTargets::mainTab).toSet()

    @Test
    fun `a main tab offers what it is for`() {
        assertEquals(
            listOf(
                SuggestedRequest.VERSE,
                SuggestedRequest.BIBLE_SEARCH,
                SuggestedRequest.MULTI_VERSE,
                SuggestedRequest.CROSS_REFS,
            ),
            chipsFor(WickPlace.Main(Tabs.BIBLE)),
        )
        assertTrue(SuggestedRequest.NEW_SONG in chipsFor(WickPlace.Main(Tabs.SONGS)))
        assertTrue(SuggestedRequest.VERSE !in chipsFor(WickPlace.Main(Tabs.SONGS)))
    }

    @Test
    fun `the main window before its tab is known offers a little of everything`() =
        assertEquals(SuggestedRequest.DEFAULTS, chipsFor(WickPlace.Main(null)))

    @Test
    fun `a place with nothing that fits offers nothing`() {
        assertEquals(emptyList(), chipsFor(WickPlace.Main(Tabs.CANVAS)))
        assertEquals(emptyList(), chipsFor(WickPlace.Settings(SettingsPage.ATEM)))
        assertEquals(emptyList(), chipsFor(WickPlace.Settings(SettingsPage.INTEGRATIONS)))
        assertEquals(emptyList(), chipsFor(WickPlace.Settings(null)))
        assertEquals(emptyList(), chipsFor(WickPlace.Other))
    }

    @Test
    fun `a Settings page offers what is done there`() {
        assertEquals(
            listOf(
                SuggestedRequest.TEXT_SIZE,
                SuggestedRequest.LYRICS_STYLE,
                SuggestedRequest.LYRICS_POSITION,
                SuggestedRequest.TITLE_STYLE,
            ),
            chipsFor(WickPlace.Settings(SettingsPage.PROFILES)),
        )
        assertTrue(SuggestedRequest.IDENTIFY in chipsFor(WickPlace.Settings(SettingsPage.PROJECTION)))
    }

    @Test
    fun `every chip anywhere is one the rules act on`() {
        val places = Tabs.entries.map { WickPlace.Main(it) } + SettingsPage.entries.map { WickPlace.Settings(it) }
        places.flatMap(::chipsFor).distinct().forEach { chip ->
            assertIs<Resolution.Act>(resolver.resolveNow(chip.request, ResolveContext(language = "en")), chip.name)
        }
    }

    @Test
    fun `a Settings chip never sends the operator back to a main tab`() {
        SettingsPage.entries.flatMap { chipsFor(WickPlace.Settings(it)) }.forEach { chip ->
            val action = (resolver.resolveNow(chip.request, ResolveContext(language = "en")) as Resolution.Act).action
            assertTrue(action !is HelperAction.SelectTab, chip.name)
            val steps = (action as? HelperAction.Highlight)?.tour?.steps.orEmpty()
            assertTrue(steps.none { it.target in mainTabs || it.before is HelperAction.SelectTab }, chip.name)
        }
    }
}
