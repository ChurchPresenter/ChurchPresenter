package org.churchpresenter.lintrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class ChurchPresenterRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "churchpresenter"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(HardcodedUiString(config), HardcodedColor(config)))
}
