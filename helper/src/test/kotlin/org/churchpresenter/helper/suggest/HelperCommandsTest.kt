package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class HelperCommandsTest {

    private val resolver = RuleIntentResolver()

    private fun unknown(requests: List<String>) =
        requests.filter { resolver.resolveNow(it, ResolveContext(language = "en")) == Resolution.Unknown }

    @Test
    fun `every request in the help table is understood`() {
        val requests = HELPER_COMMANDS.flatMap { section -> section.commands.map { it.request } }
        assertEquals(emptyList(), unknown(requests))
    }

    @Test
    fun `every suggestion chip is understood`() {
        assertEquals(emptyList(), unknown(SuggestedRequest.entries.map { it.request }))
    }

    @Test
    fun `no request is listed twice in the help table`() {
        val requests = HELPER_COMMANDS.flatMap { section -> section.commands.map { it.request } }
        val twice = requests.groupBy { it }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), twice)
    }
}
