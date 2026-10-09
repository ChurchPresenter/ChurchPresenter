package org.churchpresenter.sharedui.guide

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuideSessionTest {

    private val a = GuideTarget("a")
    private val b = GuideTarget("b")

    @Test
    fun `only a press on the target being pointed at counts`() {
        val session = GuideSession()
        session.pressed(a)
        assertEquals(0, session.activePresses)
        session.activeTarget = a
        session.pressed(b)
        assertEquals(0, session.activePresses)
        session.pressed(a)
        session.pressed(a)
        assertEquals(2, session.activePresses)
    }

    @Test
    fun `the registry keeps where each target is until it is forgotten`() {
        val registry = GuideTargetRegistry()
        assertNull(registry.boundsOf(a))
        registry.report(a, Rect(0f, 0f, 10f, 10f))
        registry.report(a, Rect(0f, 0f, 10f, 10f))
        registry.report(a, Rect(5f, 5f, 15f, 15f))
        assertEquals(Rect(5f, 5f, 15f, 15f), registry.boundsOf(a))
        registry.remove(a)
        assertNull(registry.boundsOf(a))
    }

    @Test
    fun `a new session points at nothing and holds no focus for a window to take`() {
        val session = GuideSession()
        assertNull(session.activeTarget)
        assertNull(session.activeHint)
        assertNull(session.profileFocus)
        assertNull(session.shortcutFocus)
        assertEquals(false, session.wickTyping)
        assertEquals(0, session.otherLamps)
    }

    @Test
    fun `what a tour leaves for a window is kept until the window takes it`() {
        val session = GuideSession()
        val focus = ProfileFocus(profileId = "p", page = "SONGS", rowKey = "font")
        session.profileFocus = focus
        session.shortcutFocus = "TAKE"
        session.wickTyping = true
        session.otherLamps = 1
        session.activeHint = { "here" }
        assertEquals("p", session.profileFocus?.profileId)
        assertEquals("SONGS", session.profileFocus?.page)
        assertEquals("font", session.profileFocus?.rowKey)
        assertEquals("TAKE", session.shortcutFocus)
        assertEquals(true, session.wickTyping)
        assertEquals(1, session.otherLamps)
        assertEquals(ProfileFocus(), ProfileFocus(null, null, null))
    }
}
