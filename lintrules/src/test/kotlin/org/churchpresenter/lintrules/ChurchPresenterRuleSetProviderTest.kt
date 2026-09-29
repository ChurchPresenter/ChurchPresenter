package org.churchpresenter.lintrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChurchPresenterRuleSetProviderTest {

    @Test
    fun `provides the churchpresenter rule set with every rule`() {
        val ruleSet = ChurchPresenterRuleSetProvider().instance(Config.empty)
        assertEquals("churchpresenter", ruleSet.id)
        assertEquals(listOf("HardcodedUiString"), ruleSet.rules.map { it.ruleId })
    }

    @Test
    fun `is registered for detekt to discover`() {
        val providers = ServiceLoader.load(RuleSetProvider::class.java).map { it.ruleSetId }
        assertTrue("churchpresenter" in providers)
    }
}
