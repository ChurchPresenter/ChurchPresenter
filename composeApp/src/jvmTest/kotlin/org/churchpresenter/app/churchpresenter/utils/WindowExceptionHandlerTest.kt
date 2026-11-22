package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import io.mockk.mockk
import java.awt.Window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalComposeUiApi::class)
class WindowExceptionHandlerTest {

    @Test
    fun `every window's handler drops the race and hands the rest to that window's usual handler`() {
        // A real Window cannot be built headless; the factory only passes it on.
        val window = mockk<Window>(relaxed = true)
        val asked = mutableListOf<Window>()
        val usual = mutableListOf<Throwable>()
        val delegate = object : WindowExceptionHandlerFactory {
            override fun exceptionHandler(window: Window): WindowExceptionHandler {
                asked += window
                return object : WindowExceptionHandler {
                    override fun onException(throwable: Throwable) {
                        usual += throwable
                    }
                }
            }
        }

        val handler = toleratingDisposedOwnerRace(delegate).exceptionHandler(window)
        val other = IllegalStateException("boom")
        handler.onException(IllegalArgumentException("RootNodeOwner is already disposed"))
        handler.onException(other)

        assertSame(window, asked.single())
        assertEquals(listOf<Throwable>(other), usual)
    }
}
