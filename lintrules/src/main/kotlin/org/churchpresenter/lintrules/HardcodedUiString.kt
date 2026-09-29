package org.churchpresenter.lintrules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

class HardcodedUiString(config: Config) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "User-facing text must be a string resource, and any other text a named constant.",
        Debt.FIVE_MINS,
    )

    private val textCalls: List<String> by config(listOf("Text", "BasicText"))

    private val textArguments: List<String> by config(
        listOf(
            "text",
            "label",
            "title",
            "subtitle",
            "contentDescription",
            "placeholder",
            "tooltip",
            "message",
            "hint",
            "description",
        ),
    )

    private val ignoredCalls: String by config(
        "animate.*|rememberInfiniteTransition|updateTransition|Crossfade|AnimatedContent|AnimatedVisibility",
    )

    private val ignoredCallsRegex by lazy { Regex(ignoredCalls) }

    override fun visitArgument(argument: KtValueArgument) {
        super.visitArgument(argument)
        val literal = argument.getArgumentExpression() as? KtStringTemplateExpression ?: return
        if (!hasWords(literal)) return
        val call = argument.parent?.parent as? KtCallExpression ?: return
        val callName = call.calleeExpression?.text ?: return
        if (ignoredCallsRegex.matches(callName)) return
        if (isTextArgument(argument, call, callName)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(literal),
                    "Hardcoded text ${literal.text} passed to $callName — use a string resource " +
                        "for user-facing text, or a named constant otherwise.",
                ),
            )
        }
    }

    private fun isTextArgument(argument: KtValueArgument, call: KtCallExpression, callName: String): Boolean {
        val name = argument.getArgumentName()?.asName?.asString()
        if (name != null) return name in textArguments
        return callName in textCalls &&
            !isQualified(call) &&
            call.valueArguments.firstOrNull() === argument
    }

    private fun isQualified(call: KtCallExpression): Boolean =
        (call.parent as? KtQualifiedExpression)?.selectorExpression === call

    private fun hasWords(literal: KtStringTemplateExpression): Boolean =
        literal.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .any { entry -> entry.text.any { it.isLetter() } }
}
