package org.churchpresenter.sharedui.composables

import androidx.compose.ui.Modifier
import java.awt.Cursor
import java.awt.HeadlessException
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class OutputCursorTest {

    @Test
    fun `a cursor that can be built is returned`() {
        val cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

        assertSame(cursor, cursorOrNull { cursor })
    }

    @Test
    fun `no display gives no cursor instead of a crash`() {
        assertNull(cursorOrNull { throw HeadlessException() })
    }

    @Test
    fun `a native AWT library that cannot load gives no cursor instead of a crash`() {
        assertNull(cursorOrNull { throw UnsatisfiedLinkError("no awt in system library path") })
    }

    @Test
    fun `a pointer that is not hidden leaves the modifier alone`() {
        assertSame(Modifier, Modifier.hiddenOutputCursor(hide = false))
    }
}
