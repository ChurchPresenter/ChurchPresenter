package org.churchpresenter.sharedui.utils

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * [DevFlags.forceDevWindow] gates the extra windowed presenter output. It is a `by lazy` read of an
 * env var and a system property, so only the default branch is observable once initialised — the
 * override paths are documented rather than asserted.
 */
class DevFlagsTest {

    @Test
    fun `the dev window is off unless explicitly forced`() {
        // Neither CHURCHPRESENTER_FORCE_DEV_WINDOW nor -Dchurchpresenter.forceDevWindow is set in
        // the test JVM, so this must be false. If it ever reads true here, the flag is leaking from
        // the environment into builds that should not have it.
        assertFalse(DevFlags.forceDevWindow)
    }
}
