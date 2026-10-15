package org.churchpresenter.detektrules

import io.github.detekt.psi.absolutePath
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * A blocking call -- disk, network, a decode, a sleep -- that runs on the AWT event thread because
 * it sits in a `@Composable` function: in its body, in a `remember` or `derivedStateOf`, or in an
 * effect that never leaves the thread. Every output window composes on that thread, so the call
 * freezes the projector for as long as it takes (AGENT.md, "Live-safe output paths").
 *
 * It reads names, not types: `exists()` on anything is taken for a file check. A call that must
 * stay is listed in [allowlist] with its reason. An entry whose call is gone from its file is
 * reported, so the list only shrinks.
 */
class BlockingCallOnEventThread(
    config: Config = Config.empty,
    private val allowlist: LiveSafeAllowlist? = null,
) : Rule(config) {

    override val issue = Issue(
        javaClass.simpleName,
        Severity.Performance,
        "A blocking call on the event thread inside a composable freezes every output window. Load in a " +
            "Dispatchers.IO coroutine that writes state, and read the state here.",
        Debt.TWENTY_MINS,
    )

    private var source = ""
    private var fileAllowed: List<AllowedCall> = emptyList()
    private val matched = mutableSetOf<AllowedCall>()

    override fun preVisit(root: KtFile) {
        matched.clear()
        val path = root.absolutePath().toString().replace('\\', '/')
        val list = allowlist ?: LiveSafeAllowlist.near(path, valueOrDefault(ALLOWLIST_KEY, ""))
        source = list.source
        fileAllowed = list.entries.filter { path.endsWith("/" + it.path) || path == it.path }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val call = blockingName(expression) ?: return
        val function = enclosingComposable(expression) ?: return
        if (leavesEventThread(expression, function)) return
        val name = function.nameAsSafeName.asString()
        val entry = fileAllowed.firstOrNull { it.function == name && it.call == call }
        if (entry != null) {
            matched += entry
            return
        }
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "`$call` in the composable `$name` runs on the event thread, which every output window " +
                    "composes on. Load it in a Dispatchers.IO coroutine that writes state.",
            )
        )
    }

    override fun postVisit(root: KtFile) {
        (fileAllowed - matched).forEach { stale ->
            report(
                CodeSmell(
                    issue,
                    Entity.from(root),
                    "The live-safe allowlist names `${stale.call}` in `${stale.function}`, which this file no " +
                        "longer calls there: delete the line (${source.ifEmpty { "the allowlist" }}).",
                )
            )
        }
    }

    private companion object {
        const val ALLOWLIST_KEY = "allowlist"
    }
}

/** Calls that block whatever they are called on. */
private val BLOCKING_METHODS = setOf(
    "exists", "isFile", "isDirectory", "listFiles", "lastModified",
    "readBytes", "readText", "readLines", "writeBytes", "writeText", "appendText",
    "inputStream", "outputStream", "bufferedReader", "mkdirs", "createNewFile",
    "openStream", "openConnection",
)

/** Classes every one of whose calls blocks. */
private val BLOCKING_TYPES = setOf("Files", "ImageIO", "DriverManager")

/** The name this rule reports [call] under, or null when it does not block. */
internal fun blockingName(call: KtCallExpression): String? {
    val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
    val qualified = (call.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == call }
    val receiver = qualified?.let { it.receiverExpression.text }
    return when {
        receiver == null -> callee.takeIf { it == "runBlocking" }
        receiver in BLOCKING_TYPES -> "$receiver.$callee"
        receiver == "Thread" && callee == "sleep" -> "Thread.sleep"
        callee in BLOCKING_METHODS -> callee
        else -> null
    }
}

/** The `@Composable` function [call] is written in, if the function closest around it is one. */
internal fun enclosingComposable(call: KtCallExpression): KtNamedFunction? =
    call.getStrictParentOfType<KtNamedFunction>()?.takeIf { function ->
        function.annotationEntries.any { it.shortName?.asString() == "Composable" }
    }

/** Calls whose lambda runs off the event thread whatever it is given. */
private val OFF_THREAD_ALWAYS = setOf("thread", "Thread", "execute", "submit")

/** Calls whose lambda runs off the event thread when they are given a dispatcher that is not Main. */
private val OFF_THREAD_WITH_DISPATCHER = setOf("withContext", "launch", "async", "runInterruptible")

/**
 * Whether [call] sits in a lambda handed to something that runs it on another thread, between it
 * and [function]: `withContext(Dispatchers.IO) { }`, a `launch` given a dispatcher, `thread { }`.
 */
internal fun leavesEventThread(call: KtCallExpression, function: KtNamedFunction): Boolean =
    generateSequence<PsiElement>(call.parent) { it.parent }
        .takeWhile { it != function }
        .filterIsInstance<KtLambdaExpression>()
        .any { lambda -> (lambda.parent as? KtValueArgument)?.let(::runsElsewhere) == true }

/** Whether the call [argument] is handed to runs it off the event thread. */
private fun runsElsewhere(argument: KtValueArgument): Boolean {
    val owner = argument.getStrictParentOfType<KtCallExpression>()
    val name = owner?.calleeExpression?.text
    if (name in OFF_THREAD_ALWAYS) return true
    if (name !in OFF_THREAD_WITH_DISPATCHER) return false
    return owner!!.valueArguments.filterNot { it is KtLambdaArgument }.any { other ->
        "Dispatcher" in other.text && "Main" !in other.text
    }
}
