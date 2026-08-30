package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The switch that keeps a test run out of the production Sentry project.
 *
 * `sentry.properties` carries a real DSN and lives in `jvmMain/resources`, which is on the *test*
 * runtime classpath — so a suite that initialises the reporter published as though it were a real
 * install, and did: "kaboom", "boom" and "nowhere to write" are all in Sentry, filed against a test
 * class. The `sentry.dsn=""` the test tasks already set does not stop it, because that only
 * disables the SDK's own external configuration while `initSentry` hands `Sentry.init` the DSN it
 * read off the classpath itself. This property is the one `initSentry` checks.
 */
class TelemetryDisableSwitchTest {

    @Test
    fun `the property name is the one the build sets`() {
        // Both build files set this string literally; a rename that missed one would silently put
        // the suite back into the production project, which is exactly the failure being fixed.
        assertTrue(CrashReporter.DISABLE_PROPERTY == "churchpresenter.telemetry.disabled")
    }

    @Test
    fun `the affirmative spellings all disable it`() {
        assertTrue(CrashReporter.telemetryDisabled("true"))
        assertTrue(CrashReporter.telemetryDisabled("TRUE"))
        assertTrue(CrashReporter.telemetryDisabled(" yes "))
        assertTrue(CrashReporter.telemetryDisabled("1"))
    }

    @Test
    fun `anything else leaves telemetry alone`() {
        // Absent is the shipping case: an operator who opted in must still be reported for.
        assertFalse(CrashReporter.telemetryDisabled(null))
        assertFalse(CrashReporter.telemetryDisabled(""))
        assertFalse(CrashReporter.telemetryDisabled("false"))
        assertFalse(CrashReporter.telemetryDisabled("0"))
    }

    @Test
    fun `it is actually set while this suite runs`() {
        // The point of the whole change: if this fails, the build stopped setting it and the next
        // test that reports an exception sends it to the production project.
        assertTrue(CrashReporter.telemetryDisabled(), "the test task must set ${CrashReporter.DISABLE_PROPERTY}")
    }
}
