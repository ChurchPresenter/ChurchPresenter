package org.churchpresenter.sharedui.utils

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

@OptIn(ExperimentalTestApi::class)
class SystemFontsRememberTest {

    @Test
    fun `a composable gets the same families the blocking call does`() = runComposeUiTest {
        val expected = SystemFonts.families()
        var fonts: List<String> = emptyList()
        setContent { fonts = rememberSystemFonts() }
        waitUntil(timeoutMillis = 10_000) { fonts.isNotEmpty() || expected.isEmpty() }
        assertEquals(expected, fonts)
    }

    @Test
    fun `a composable that arrives before the scan waits for it`() = runComposeUiTest {
        SystemFonts.reset()
        var fonts: List<String> = emptyList()
        setContent { fonts = rememberSystemFonts() }
        val expected = SystemFonts.families()
        waitUntil(timeoutMillis = 10_000) { fonts.isNotEmpty() || expected.isEmpty() }
        assertEquals(expected, fonts)
    }

    @Test
    fun `resetting drops the snapshot so the next call enumerates again`() {
        val first = SystemFonts.families()
        SystemFonts.reset()
        val second = SystemFonts.families()
        assertEquals(first, second)
        assertNotSame(first, second)
    }
}
