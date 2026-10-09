package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Wick's "show John 3:16", read by the Bible tab as its search box would read it. */
class BibleViewModelReferenceLiveTest {

    private fun model() =
        BibleViewModel(AppSettings(), dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined)

    @Test
    fun `text that is no reference, or a book with no chapter, is not put live`() {
        val vm = model()
        assertFalse(vm.goLiveWithReference("   "))
        assertFalse(vm.goLiveWithReference("hello there"))
    }

    @Test
    fun `with no Bible loaded even a real reference names no book, so nothing goes live`() {
        assertFalse(model().goLiveWithReference(" John 3:16 "))
    }

    @Test
    fun `the standard English names run from Genesis to Revelation, and no further`() {
        assertEquals("genesis", standardEnglishBookName(1)?.lowercase())
        assertEquals("revelation", standardEnglishBookName(66)?.lowercase())
        assertNull(standardEnglishBookName(0))
        assertNull(standardEnglishBookName(67))
    }
}
