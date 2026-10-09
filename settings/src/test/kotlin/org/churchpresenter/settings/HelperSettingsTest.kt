package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelperSettingsTest {

    @Test
    fun `outside dev mode Wick is here only once started from the Help menu`() {
        assertFalse(isWickAvailable(devMode = false, HelperSettings()))
        assertTrue(isWickAvailable(devMode = false, HelperSettings(startedByUser = true)))
        assertTrue(isWickAvailable(devMode = true, HelperSettings()))
    }
}
