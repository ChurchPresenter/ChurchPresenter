@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.stt.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.showsExactly

class BibleTabSttStatusTest {

    private val managers = mutableListOf<STTManager>()

    @AfterTest
    fun cleanUp() {
        managers.forEach { runCatching { it.dispose() } }
        managers.clear()
    }

    private fun connectedStt() = STTManager().also {
        managers.add(it)
        it.applyConnected()
    }

    @Test
    fun `the status row is hidden while STT is not connected`() = bibleTab { _, _ ->
        assertFalse(showsExactly("Starting engine…"))
        assertFalse(showsExactly("No Bible configured"))
    }

    @Test
    fun `with no bible configured and STT connected the row reports No Bible`() = bibleTab(
        settings = { it.copy(bibleSettings = it.bibleSettings.copy(primaryBible = "", secondaryBible = "")) },
        stt = connectedStt(),
    ) { _, _ ->
        assertTrue(showsExactly("No Bible configured"))
    }

    @Test
    fun `with STT connected and no engine link yet the row reports starting the engine`() = bibleTab(
        stt = connectedStt(),
    ) { _, _ ->
        assertTrue(showsExactly("Starting engine…"))
    }
}
