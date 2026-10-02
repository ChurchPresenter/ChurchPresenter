package org.churchpresenter.sharedui.composables

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The script that hides the pointer over a live web page on an output (found on Windows: the arrow
 * showed over a Web tab page with "hide the mouse pointer" on, because Chromium's own window sets its
 * cursor). It runs inside Chromium, which a unit test has none of, so what is pinned here is what the
 * script says; whether Chromium obeys it is the Windows retest's to confirm.
 */
class OutputCursorScriptTest {

    private val hide = outputCursorScript(hide = true)
    private val show = outputCursorScript(hide = false)

    @Test
    fun `hiding adds one style rule that hides the pointer everywhere on the page`() {
        assertContains(hide, "cursor:none!important")
        assertContains(hide, "*,*::before,*::after")
        assertContains(hide, "createElement('style')")
    }

    @Test
    fun `hiding twice reuses the same element rather than adding another`() {
        assertContains(hide, "getElementById('churchpresenter-hide-cursor')")
        assertContains(hide, "if(!s)")
    }

    @Test
    fun `showing removes that element and adds nothing`() {
        assertContains(show, "getElementById('churchpresenter-hide-cursor')")
        assertContains(show, ".remove()")
        assertFalse(show.contains("cursor:none"))
        assertFalse(show.contains("createElement"))
    }

    @Test
    fun `both are self-contained so they can run on any page`() {
        listOf(hide, show).forEach {
            assertTrue(it.startsWith("(function(){") && it.endsWith("})();"), it)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `nothing outside an output hides the pointer`() = runComposeUiTest {
        var hidden: Boolean? = null
        setContent { hidden = LocalOutputCursorHidden.current }
        waitForIdle()
        assertEquals(false, hidden, "the Web tab's own browser keeps its pointer")
    }
}
