package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitionRuleTest {

    private val resolver = RuleIntentResolver()

    private fun actionOf(text: String, context: ResolveContext = ResolveContext(language = "en")): HelperAction? =
        (resolver.resolveNow(text, context) as? Resolution.Act)?.action

    private fun HelperAction?.ringsTransition(): Boolean =
        (this as? HelperAction.Highlight)?.tour?.steps?.any { it.target == GuideTargets.PROFILE_TRANSITION } == true

    private fun HelperAction?.onBiblePage(): Boolean =
        (this as? HelperAction.Highlight)?.tour?.steps?.any { it.target == GuideTargets.PROFILE_BIBLE_PAGE } == true

    @Test
    fun `asked about transitions, however it is spelled, Wick goes to the Transition group`() {
        listOf(
            "can i change the transitation", "change the transition", "how do I change transistion speed",
            "make the songs crossfade", "turn off fade in", "slower fade between songs",
        ).forEach { assertTrue(actionOf(it).ringsTransition(), it) }
    }

    @Test
    fun `the Bible's transition is on the Bible page, a song's on the Songs page`() {
        assertTrue(actionOf("change the bible transition").onBiblePage())
        assertTrue(actionOf("crossfade between verses").onBiblePage())
        assertTrue(actionOf("can i change the transitation", ResolveContext(currentTab = Tabs.BIBLE)).onBiblePage())
        assertFalse(actionOf("change the song transition", ResolveContext(currentTab = Tabs.BIBLE)).onBiblePage())
        assertFalse(actionOf("change the transition").onBiblePage())
    }

    @Test
    fun `pictures and slides keep their own transition`() {
        listOf("change the picture transition", "slide transition speed").forEach {
            assertFalse(actionOf(it).ringsTransition(), it)
        }
    }

    @Test
    fun `texting shorthand is read as written out`() {
        assertEquals(HelperAction.Greet, actionOf("what can u do"))
        assertEquals(HelperAction.Greet, actionOf("wat can u do pls"))
        assertEquals(actionOf("what can you do"), actionOf("what can u do", ResolveContext(language = "uz")))
    }
}
