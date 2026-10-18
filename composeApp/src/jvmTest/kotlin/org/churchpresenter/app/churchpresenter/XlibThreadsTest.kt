package org.churchpresenter.app.churchpresenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XlibThreadsTest {

    @Test
    fun `Linux turns on Xlib's locking`() {
        var calls = 0
        assertTrue(initXlibThreads("Linux") { calls++; 1 })
        assertEquals(1, calls)
    }

    @Test
    fun `macOS and Windows never load libX11`() {
        for (os in listOf("Mac OS X", "Windows 11")) {
            assertFalse(initXlibThreads(os) { error("$os must not call XInitThreads") })
        }
    }

    @Test
    fun `a machine without libX11 starts anyway`() {
        assertFalse(initXlibThreads("Linux") { throw UnsatisfiedLinkError("libX11.so: not found") })
    }

    @Test
    fun `Xlib refusing to set up its locking is reported, not thrown`() {
        assertFalse(initXlibThreads("Linux") { 0 })
    }
}
