package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stayed in `:composeApp` when the rest of `CrashReporterTest` moved to `:diagnostics`. The
 * appender it guards against comes from `sentry-logback`, which is a `:composeApp` dependency, and
 * `logback-test.xml` is `:composeApp`'s test resource — in `:diagnostics` this assertion would be
 * vacuously true and would guard nothing.
 */
class LogbackSentryAppenderTest {

    /**
     * The real leak guard. `CrashReporter.isEnabled()` only samples one instant, and the production
     * logback SentryAppender initialises Sentry *lazily* on the first ERROR/WARN — so this whole
     * suite once shipped its throwaway exceptions to production Sentry while `isEnabled` still
     * read false at the top. The structural fix is jvmTest's own logback-test.xml with no Sentry
     * appender; assert that no SentryAppender is attached anywhere so re-adding one fails loudly.
     */
    @Test
    fun `no SentryAppender is attached to logback during tests`() {
        val context = org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
        val offenders = context.loggerList
            .flatMap { logger -> logger.iteratorForAppenders().asSequence().toList() }
            .filter { it.javaClass.name.contains("Sentry", ignoreCase = true) }
        assertTrue(
            offenders.isEmpty(),
            "logback-test.xml must not wire a Sentry appender — found: ${offenders.map { it.javaClass.name }}"
        )
    }
}
