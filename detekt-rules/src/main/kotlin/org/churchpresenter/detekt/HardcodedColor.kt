package org.churchpresenter.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

class HardcodedColor(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Style,
        "Colors come from the theme (MaterialTheme.colorScheme / MaterialTheme.semantic), not literals.",
        Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression as? KtNameReferenceExpression ?: return
        if (callee.getReferencedName() != COLOR) return
        val args = expression.valueArguments.mapNotNull { it.getArgumentExpression() }
        if (args.isEmpty() || !args.all { it.isLiteral() }) return
        if (expression.landing() is KtParameter) return
        report(expression, "Hard-coded color `${expression.text}`")
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return
        if (receiver.getReferencedName() != COLOR) return
        val selector = expression.selectorExpression as? KtNameReferenceExpression ?: return
        val name = selector.getReferencedName()
        if (name !in NAMED_COLORS) return
        val landing = expression.landing()
        if (landing is KtParameter) return
        if (name == BLACK && landing.isBackgroundSlot()) return
        report(expression, "Hard-coded color `${expression.text}`")
    }

    private fun report(expression: KtExpression, message: String) {
        this.report(CodeSmell(issue, Entity.from(expression), message))
    }

    // Where the value ends up, past `.copy(…)`, parentheses, if/when branches and `remember { }`.
    private fun KtExpression.landing(): PsiElement? {
        var node: PsiElement = this
        while (true) {
            val parent = node.parent ?: return null
            node = when {
                parent is KtDotQualifiedExpression && parent.receiverExpression == node &&
                    (parent.selectorExpression as? KtCallExpression)?.calleeName() == COPY -> parent
                parent is KtParenthesizedExpression || parent is KtContainerNode ||
                    parent is KtIfExpression || parent is KtWhenEntry || parent is KtWhenExpression ||
                    parent is KtFunctionLiteral || parent is KtLambdaExpression ||
                    parent is KtLambdaArgument -> parent
                parent is KtBlockExpression && parent.statements.lastOrNull() == node -> parent
                parent is KtCallExpression && node is KtLambdaArgument -> parent
                else -> return parent
            }
        }
    }

    // A black projection background: `Modifier.background(Color.Black)`, `background = …`,
    // `screenColor = …`, `SomeBackground(color = …)` or `val backgroundColor = …`.
    private fun PsiElement?.isBackgroundSlot(): Boolean = when (this) {
        is KtValueArgument -> {
            val argName = getArgumentName()?.asName?.asString()
            val call = (parent as? KtValueArgumentList)?.parent as? KtCallExpression
            val callee = call?.calleeName().orEmpty()
            argName?.let { BACKGROUND_NAME.containsMatchIn(it) || it == SCREEN_COLOR } == true ||
                callee == BACKGROUND ||
                (BACKGROUND_NAME.containsMatchIn(callee) && argName == COLOR_ARG)
        }
        is KtProperty -> name?.let { BACKGROUND_NAME.containsMatchIn(it) } == true
        else -> false
    }

    private fun KtCallExpression.calleeName(): String? =
        (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

    private fun KtExpression.isLiteral(): Boolean = when (this) {
        is KtConstantExpression -> true
        is KtPrefixExpression -> baseExpression?.isLiteral() == true
        is KtParenthesizedExpression -> expression?.isLiteral() == true
        is KtBinaryExpressionWithTypeRHS -> left.isLiteral()
        is KtDotQualifiedExpression ->
            receiverExpression.isLiteral() && selectorExpression is KtCallExpression &&
                (selectorExpression as KtCallExpression).valueArguments.isEmpty()
        else -> false
    }

    private companion object {
        const val COLOR = "Color"
        const val BLACK = "Black"
        const val COPY = "copy"
        const val BACKGROUND = "background"
        const val SCREEN_COLOR = "screenColor"
        const val COLOR_ARG = "color"
        val BACKGROUND_NAME = Regex("background|Bg", RegexOption.IGNORE_CASE)

        val NAMED_COLORS = setOf(
            // Compose
            "Black", "DarkGray", "Gray", "LightGray", "White",
            "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta",
            // java.awt
            "BLACK", "DARK_GRAY", "GRAY", "LIGHT_GRAY", "WHITE", "RED", "GREEN", "BLUE",
            "YELLOW", "CYAN", "MAGENTA", "ORANGE", "PINK",
            "black", "darkGray", "gray", "lightGray", "white", "red", "green", "blue",
            "yellow", "cyan", "magenta", "orange", "pink",
        )
    }
}
