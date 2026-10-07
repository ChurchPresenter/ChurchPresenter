package org.churchpresenter.sharedui.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemFilesTest {

    @Test
    fun `a macOS AppleDouble companion is not the operator's file`() {
        assertTrue(isSystemArtifact("._Kazakhstan 2026.003.jpeg"))
    }

    @Test
    fun `an Office lock file is not the operator's file`() {
        assertTrue(isSystemArtifact("~\$N_TRADITIONAL.spb"))
    }

    @Test
    fun `an ordinary name, a dotfile or a tilde in the middle is left alone`() {
        assertFalse(isSystemArtifact("Kazakhstan 2026.003.jpeg"))
        assertFalse(isSystemArtifact(".hidden.jpg"))
        assertFalse(isSystemArtifact("before~\$after.spb"))
        assertFalse(isSystemArtifact("~backup.spb"))
    }
}
