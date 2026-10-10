package org.churchpresenter.lottiegen

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchModeTest {

    private val none: (String) -> String? = { null }

    @Test
    fun `the arguments pick the window, the band before the editor`() {
        assertEquals(LaunchMode.GENERATOR, launchMode(emptyArray(), none))
        assertEquals(LaunchMode.EDITOR, launchMode(arrayOf("--editor"), none))
        assertEquals(LaunchMode.BAND, launchMode(arrayOf("--editor", "--band"), none))
    }

    @Test
    fun `the system properties pick it too, and only when true`() {
        assertEquals(LaunchMode.EDITOR, launchMode(emptyArray()) { if (it == "lottiegen.editor") "true" else null })
        assertEquals(LaunchMode.BAND, launchMode(emptyArray()) { if (it == "lottiegen.band") "true" else "false" })
        assertEquals(LaunchMode.GENERATOR, launchMode(emptyArray()) { "false" })
    }
}
