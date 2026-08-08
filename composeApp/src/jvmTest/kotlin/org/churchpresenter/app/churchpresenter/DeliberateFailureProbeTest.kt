package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.fail

/**
 * TEMPORARY. Fails on purpose so CI exercises the red path of the Coverage floor step.
 * This branch is a throwaway probe for PR #241 and must never be merged.
 */
class DeliberateFailureProbeTest {
    @Test
    fun `fails on purpose so the coverage floor step meets a failed suite`() {
        fail("deliberate probe failure for PR #241 — this branch is not for merging")
    }
}
