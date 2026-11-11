package org.churchpresenter.diagnostics

/**
 * What a stall groups on for this frame: its class and function, never its line, and never the
 * names the compiler makes up -- a lambda's `$lambda$0$6`, an anonymous `$1`, Compose's `-533V2PY`
 * mangle, a `ComposableSingletons` holder -- which change whenever the code around them does and
 * would open a new issue for the same function. A lambda or coroutine class is named for the
 * function it was written in: `FooKt$Bar$1.invoke` is `FooKt.Bar`.
 */
internal val StackTraceElement.groupName: String
    get() {
        // A class whose own name starts with `$` (a JDK proxy, `$Proxy6`) has nothing nested in it.
        val split = className.indexOf('$').takeIf { it > 0 && className[it - 1] != '.' } ?: className.length
        val outer = className.take(split)
        val nested = className.drop(split + 1).split('$').filter { it.isNotEmpty() }
        val holder = nested.firstOrNull()?.takeIf { outer.endsWith(SINGLETONS) }
        val owner = if (holder != null) outer.dropLast(SINGLETONS.length) + holder else outer
        val enclosing = nested.drop(if (holder != null) 1 else 0).firstOrNull { it.isSourceName }
        val method = unmangled(methodName.substringBefore('$').substringBefore('-'))
        val function = when {
            enclosing != null && method in SYNTHETIC_METHODS -> unmangled(enclosing)
            method.isSingletonLambda -> LAMBDA
            else -> method
        }
        return "$owner.$function"
    }

/** Whether a nested class name is a function's own name rather than a number or a lambda's. */
private val String.isSourceName: Boolean
    get() = first().isLetter() && !isSingletonLambda && this != LAMBDA

/** A `ComposableSingletons` lambda, numbered `lambda-1` or hashed `lambda_1494010327`. */
private val String.isSingletonLambda: Boolean
    get() = startsWith("$LAMBDA-") || startsWith("${LAMBDA}_")

/** [name] without Compose's inline-class mangle, which a lambda class spells `_WH_ejsw`. */
private fun unmangled(name: String): String {
    val mangle = MANGLE.find(name) ?: return name
    return if (mangle.value.any { it.isUpperCase() || it.isDigit() }) name.removeRange(mangle.range) else name
}

private val MANGLE = Regex("_[A-Za-z0-9_]{7}$")
private val SYNTHETIC_METHODS = setOf("invoke", "invokeSuspend", "run", "call", "accept", "apply", "get")
private const val SINGLETONS = "ComposableSingletons"
private const val LAMBDA = "lambda"

/**
 * Whether the thread was in Chromium, above the app's own code ([appFrame], or the whole stack when
 * there is none): JCEF starting it, which it does on the event thread, or the Win32 pump that hands
 * Chromium's windows the messages sent to them (`SentMessagePump`). Its work is not the app's to
 * shorten, so it groups as one issue per OS.
 */
internal fun isInChromium(frames: List<StackTraceElement>, appFrame: Int): Boolean {
    val end = if (appFrame < 0) frames.size else appFrame
    return (0 until end).any { frames[it].className.startsWith(CEF_PACKAGE) || frames[it].methodName == PEEK_MESSAGE }
}

private const val CEF_PACKAGE = "org.cef."
private const val PEEK_MESSAGE = "PeekMessage"
