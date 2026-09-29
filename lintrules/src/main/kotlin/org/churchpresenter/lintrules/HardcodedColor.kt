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
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class HardcodedColor(config: Config) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "Colors must come from the theme — MaterialTheme.colorScheme or the semantic colors.",
        Debt.FIVE_MINS,
    )

    private val colorClass: String by config("Color")

    private val namedColors: List<String> by config(
        listOf("Black", "DarkGray", "Gray", "LightGray", "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta"),
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != colorClass) return
        val arguments = expression.valueArguments
        if (arguments.isEmpty()) return
        if (arguments.all { it.getArgumentExpression() is KtConstantExpression }) {
            reportColor(expression, expression.text)
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.receiverExpression.text != colorClass) return
        val name = (expression.selectorExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return
        if (name in namedColors) reportColor(expression, expression.text)
    }

    private fun reportColor(expression: KtExpression, text: String) {
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Hardcoded color $text — use a theme color (MaterialTheme.colorScheme or the semantic colors).",
            ),
        )
    }
}
