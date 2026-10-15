package org.churchpresenter.detektrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** The build's own rules, as `live-safe:` in config/detekt/detekt.yml. */
class LiveSafeRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "live-safe"

    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, listOf(BlockingCallOnEventThread(config)))
}
