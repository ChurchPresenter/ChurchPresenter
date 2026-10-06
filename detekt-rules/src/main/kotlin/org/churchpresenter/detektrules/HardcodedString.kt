package org.churchpresenter.detektrules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

internal const val ALLOWED_CALLS = "allowedCalls"
private val DEFAULT_ALLOWED_CALLS = listOf("Log.info", "Log.warn", "Log.error")
private const val MAX_QUOTED = 60

/**
 * Every string literal is either a `const val` or a string resource.
 *
 * A literal is allowed only as a `const val` initializer, inside an annotation, or as an argument
 * to one of [allowedCalls] (the `Log` calls by default). A literal with no text of its own -- `""`,
 * or a template made only of interpolations and whitespace -- is not hard-coded and is ignored.
 */
class HardcodedString(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Maintainability,
        "A string literal must be a const val or a string resource.",
        Debt.FIVE_MINS,
    )

    private val allowedCalls: Set<String> = valueOrDefault(ALLOWED_CALLS, DEFAULT_ALLOWED_CALLS).toSet()

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        if (!expression.hasOwnText() || expression.parents.any(::isExemptContext)) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Hard-coded string ${expression.text.take(MAX_QUOTED)}: move it to a const val or a string resource.",
            ),
        )
    }

    private fun KtStringTemplateExpression.hasOwnText(): Boolean = entries.any { entry ->
        when (entry) {
            is KtLiteralStringTemplateEntry -> entry.text.isNotBlank()
            is KtEscapeStringTemplateEntry -> true
            else -> false
        }
    }

    private fun isExemptContext(element: PsiElement): Boolean = when (element) {
        is KtAnnotationEntry -> true
        is KtProperty -> element.hasModifier(KtTokens.CONST_KEYWORD)
        is KtCallExpression -> callName(element) in allowedCalls
        else -> false
    }

    private fun callName(call: KtCallExpression): String {
        val callee = call.calleeExpression?.text.orEmpty()
        val qualified = call.parent as? KtDotQualifiedExpression
        return if (qualified?.selectorExpression == call) "${qualified.receiverExpression.text}.$callee" else callee
    }
}
