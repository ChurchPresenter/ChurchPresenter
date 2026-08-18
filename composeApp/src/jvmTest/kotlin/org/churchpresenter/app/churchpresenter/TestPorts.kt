package org.churchpresenter.app.churchpresenter

/**
 * The port this fork should bind, given the [base] port a suite was written against.
 *
 * Server suites bind a fixed port rather than port 0 so a failure names a stable address in the log.
 * Sequentially that is fine — the server is stopped between classes. Under `maxParallelForks` it is
 * a cross-process collision instead, and two suites already shared 39_840 before this existed
 * (`InstanceLinkClientFetchGuardTest` and `CompanionServerMediaCommandTest`), which only ever worked
 * because nothing ran them at the same time.
 *
 * So each fork shifts the whole range by its own band, assigned in [PerForkTestHome] from Gradle's
 * worker id. The offset is absent outside Gradle, so a single IDE run still binds [base] itself and
 * the number in the source is the number in the log.
 */
internal fun testPort(base: Int): Int =
    base + (System.getProperty(PerForkTestHome.PORT_OFFSET_PROPERTY)?.toIntOrNull() ?: 0)
