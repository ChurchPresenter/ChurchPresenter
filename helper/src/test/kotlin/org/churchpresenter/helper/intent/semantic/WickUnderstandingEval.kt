package org.churchpresenter.helper.intent.semantic

import kotlinx.coroutines.runBlocking
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.suggest.SuggestedRequest
import org.churchpresenter.sharedui.models.Tabs
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How well Wick understands what an operator types, measured on `wick/understanding.tsv`: rewordings of
 * every chip that share few words with it, typos, and requests it should not act on.
 *
 * Each row ends one of four ways: acted on rightly, offered the right chip among the first three, not
 * understood, or — the one that must never happen — acted on wrongly. It prints the table and the
 * totals, and fails on any wrong action or when the right answer is reached for fewer than [MIN_REACHED]
 * of the rows that have one.
 *
 * Tagged `eval` and run by `./gradlew :helper:wickEval`, not `test`: every row runs the model, which
 * puts the whole table over the unit suite's one-second budget.
 */
@Tag("eval")
class WickUnderstandingEval {

    private val rules = RuleIntentResolver()
    private val resolver = TestModel.resolver()
    private val context = ResolveContext(visibleTabs = Tabs.entries.toSet(), language = "en")

    private class Row(val text: String, val expected: SuggestedRequest?, val alternates: List<CatalogTarget>)

    /**
     * How a row ended. [RULES_WRONG] is the rules acting on something other than what was meant — their
     * own behaviour, reported here but not this suite's to fix; [WRONG] is the model doing so, never allowed.
     */
    private enum class Outcome { RULES, ACTED, GUESS, MISSED, WRONG, RULES_WRONG, STAYED_OUT }

    private val rows: List<Row> = javaClass.classLoader.getResourceAsStream("wick/understanding.tsv")!!
        .bufferedReader().readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('\t')
            Row(
                parts[0],
                SuggestedRequest.entries.find { it.name == parts[1] },
                parts.getOrNull(2)?.split(',')?.mapNotNull { CatalogTarget.parse(it.trim()) }.orEmpty(),
            )
        }

    @Test
    fun `wick understands rewordings without acting wrongly`() {
        val outcomes = rows.map { row -> row to outcomeOf(row) }
        for ((row, outcome) in outcomes) println("%-11s %-22s %s".format(outcome, row.expected ?: "NONE", row.text))
        val counts = outcomes.groupingBy { it.second }.eachCount()
        val answerable = rows.count { it.expected != null }
        val reached = listOf(Outcome.RULES, Outcome.ACTED, Outcome.GUESS).sumOf { counts[it] ?: 0 }
        println(
            "Reached $reached/$answerable (rules ${counts[Outcome.RULES] ?: 0}, " +
                "model acted ${counts[Outcome.ACTED] ?: 0}, " +
                "right guess ${counts[Outcome.GUESS] ?: 0}), missed ${counts[Outcome.MISSED] ?: 0}, " +
                "model wrong ${counts[Outcome.WRONG] ?: 0}, rules wrong ${counts[Outcome.RULES_WRONG] ?: 0}",
        )
        assertEquals(0, counts[Outcome.WRONG] ?: 0, "the model acted wrongly")
        // What the rules already get wrong is out of the model's reach: it only reads what they miss.
        val reachable = answerable - (counts[Outcome.RULES_WRONG] ?: 0)
        assertTrue(reached >= reachable * MIN_REACHED, "reached only $reached of the $reachable the model can see")
    }

    private fun outcomeOf(row: Row): Outcome {
        val expected = row.expected
        val ruled = rules.resolveNow(row.text, context)
        val resolution = runBlocking { resolver.resolve(row.text, context) }
        val acted = (resolution as? Resolution.Act)?.action?.unnamed()
        val right = rightActions(row)
        val byRules = ruled != Resolution.Unknown
        return when {
            expected == null -> if (acted == null) Outcome.STAYED_OUT else Outcome.WRONG
            acted != null && acted in right -> if (byRules) Outcome.RULES else Outcome.ACTED
            acted != null && byRules -> Outcome.RULES_WRONG
            acted != null -> Outcome.WRONG
            resolution is Resolution.Clarify && resolution.options.take(CHIPS_SEEN).any { it.unnamed() in right } ->
                if (byRules) Outcome.RULES else Outcome.GUESS
            resolution is Resolution.DidYouMean && resolution.action.unnamed() in right -> Outcome.GUESS
            else -> Outcome.MISSED
        }
    }

    /** Every action that answers [row] rightly: what its chip leads to, and its listed alternates. */
    private fun rightActions(row: Row): Set<HelperAction> {
        val chip = row.expected ?: return emptySet()
        val targets = listOf(CatalogTarget.Suggested(chip)) + row.alternates
        return targets.flatMap { target ->
            when (val r = resolver.resolutionFor(target, context)) {
                is Resolution.Act -> listOf(r.action)
                is Resolution.Clarify -> r.options
                else -> emptyList()
            }
        }.map { it.unnamed() }.toSet()
    }

    /** [this] without the name a "did you mean" choice gives a tour: the same action either way. */
    private fun HelperAction.unnamed(): HelperAction = if (this is HelperAction.Highlight) copy(label = null) else this

    private companion object {
        /** The right chip counts when it is among the first three the operator sees. */
        const val CHIPS_SEEN = 3

        /** The share of answerable rows Wick must reach — by the rules, by acting, or by the right chip. */
        const val MIN_REACHED = 0.9
    }
}
