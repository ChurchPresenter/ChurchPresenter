package org.jetbrains.skiko

/**
 * A stand-in for skiko's own `RenderException`, declared in skiko's package because that package is
 * precisely what `CrashReporter.classifyCrash` keys on.
 *
 * `:diagnostics` does not depend on skiko and must not start — it depends on Sentry and nothing
 * else — so the real class is not on this classpath. A one-line throwable with the right fully
 * qualified name exercises the real classifier over a real throwable, which a string-matching test
 * of the predicate alone would not.
 */
class RenderException(message: String) : RuntimeException(message)
