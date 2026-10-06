package org.churchpresenter.detektrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ChurchPresenterRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, listOf(HardcodedString(config)))

    companion object {
        const val RULE_SET_ID = "churchpresenter"
    }
}
