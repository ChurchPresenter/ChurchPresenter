package org.churchpresenter.app.churchpresenter.utils

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListenerProxy
import java.awt.event.WindowEvent
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppWindowIconsInstallTest {

    private val toolkit = Toolkit.getDefaultToolkit()

    private fun event(source: Any, id: Int) = object : AWTEvent(source, id) {}

    @Test
    fun `with no frames to give nothing is installed`() {
        assertNull(AppWindowIcons.install(toolkit, emptyList()))
    }

    @Test
    fun `installing listens for every window event the toolkit dispatches`() {
        val listener = assertNotNull(AppWindowIcons.install())
        try {
            assertTrue(
                toolkit.getAWTEventListeners(AWTEvent.WINDOW_EVENT_MASK)
                    .any { it === listener || (it as? AWTEventListenerProxy)?.listener === listener },
            )
            listener.eventDispatched(event(Any(), WindowEvent.WINDOW_OPENED))
        } finally {
            toolkit.removeAWTEventListener(listener)
        }
    }

    @Test
    fun `only an opening window is given the frames`() {
        assertNull(AppWindowIcons.openedWindow(event(Any(), WindowEvent.WINDOW_OPENED)), "the source is not a window")
        assertNull(AppWindowIcons.openedWindow(event(Any(), WindowEvent.WINDOW_CLOSED)), "nor is this an opening")
    }
}
