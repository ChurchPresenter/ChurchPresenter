// The file is named for the class under test, UpdateChecker; UpdateCheckIntervalTest moved to
// :settings with the enum, leaving one declaration whose name no longer matches the file.
@file:Suppress("MatchingDeclarationName")

package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [UpdateChecker.isNewerVersion] decides whether the app nags the operator to update. A false
 * positive re-prompts forever; a false negative silently strands users on an old build.
 *
 * Versions here follow this project's own scheme (see `gitCommitCount()` in build.gradle.kts):
 * `MAJOR.MINOR.PATCH` where MAJOR is the 2-digit year and MINOR/PATCH derive from the commit
 * count, so the numbers roll over frequently and comparisons are exercised hard in practice.
 */
class IsNewerVersionTest {

    @Test
    fun `a higher component at any position means newer`() {
        assertTrue(UpdateChecker.isNewerVersion("26.1.0", "25.9.9"), "major wins over everything")
        assertTrue(UpdateChecker.isNewerVersion("26.2.0", "26.1.99"), "minor wins over patch")
        assertTrue(UpdateChecker.isNewerVersion("26.1.2", "26.1.1"))
    }

    @Test
    fun `an identical version is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion("26.1.2", "26.1.2"), "an equal version must never prompt")
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion("25.9.9", "26.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("26.1.1", "26.1.2"))
    }

    @Test
    fun `numeric comparison is used, not lexicographic`() {
        // "10" < "9" as strings; a string compare here would hide every release past x.9.
        assertTrue(UpdateChecker.isNewerVersion("26.10.0", "26.9.0"))
        assertTrue(UpdateChecker.isNewerVersion("26.1.100", "26.1.99"))
        assertFalse(UpdateChecker.isNewerVersion("26.9.0", "26.10.0"))
    }

    @Test
    fun `missing trailing components count as zero`() {
        assertFalse(UpdateChecker.isNewerVersion("26.1", "26.1.0"), "26.1 == 26.1.0")
        assertTrue(UpdateChecker.isNewerVersion("26.2", "26.1.5"))
        assertTrue(UpdateChecker.isNewerVersion("26.1.1", "26.1"))
        assertFalse(UpdateChecker.isNewerVersion("26", "26.0.1"))
    }

    @Test
    fun `a leading v prefix is tolerated on both sides`() {
        // Releases are tagged "v26.1.2"; non-numeric parts are dropped rather than crashing.
        assertTrue(UpdateChecker.isNewerVersion("v26.1.2", "v26.1.1"))
        assertFalse(UpdateChecker.isNewerVersion("v26.1.1", "v26.1.2"))
    }

    @Test
    fun `unparseable input degrades to not-newer instead of throwing`() {
        // The remote version string comes off the network, so it must never crash the check.
        assertFalse(UpdateChecker.isNewerVersion("", "26.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("not-a-version", "26.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("26.1.0", "26.1.0-beta"))
    }
}
