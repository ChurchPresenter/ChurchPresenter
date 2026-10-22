package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupPromptPacingTest {

    @Test
    fun `a start is counted`() {
        assertEquals(2, StartupPromptPacing().launched().launched().launches)
    }

    @Test
    fun `the first start that wants a prompt may have it`() {
        assertTrue(StartupPromptPacing().launched().promptAllowed())
    }

    @Test
    fun `a start that showed a prompt has no second one`() {
        assertFalse(StartupPromptPacing().launched().prompted().promptAllowed())
    }

    @Test
    fun `the start after a prompt is quiet`() {
        val second = StartupPromptPacing().launched().prompted().launched()

        assertFalse(second.promptAllowed())
    }

    @Test
    fun `prompts land every other start`() {
        val third = StartupPromptPacing().launched().prompted().launched().launched()

        assertTrue(third.promptAllowed())
        assertFalse(third.prompted().launched().promptAllowed())
        assertTrue(third.prompted().launched().launched().promptAllowed())
    }

    @Test
    fun `a pacing saved before survives a settings round trip`() {
        val pacing = StartupPromptPacing(launches = 5, lastPromptLaunch = 3)
        val json = settingsJson.encodeToString(AppSettings.serializer(), AppSettings(startupPrompts = pacing))

        assertEquals(pacing, settingsJson.decodeFromString(AppSettings.serializer(), json).startupPrompts)
    }
}
