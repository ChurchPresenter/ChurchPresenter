package org.churchpresenter.sharedui.utils

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DevFlagsTest {

    private fun unset(env: String, property: String) =
        System.getenv(env) == null && System.getProperty(property) == null

    @Test
    fun `the dev window is not forced unless asked for`() {
        assumeTrue(unset("CHURCHPRESENTER_FORCE_DEV_WINDOW", "churchpresenter.forceDevWindow"))
        assertFalse(DevFlags.forceDevWindow)
    }

    @Test
    fun `the platform's own render API is kept unless one is named`() {
        assumeTrue(unset("CHURCHPRESENTER_RENDER_API", "churchpresenter.renderApi"))
        assertNull(DevFlags.renderApiOverride)
    }

    @Test
    fun `software decoding is forced unless switched off`() {
        assumeTrue(unset("CHURCHPRESENTER_FORCE_AVCODEC", "churchpresenter.forceAvcodec"))
        assertEquals(true, DevFlags.forceAvcodec)
    }
}
