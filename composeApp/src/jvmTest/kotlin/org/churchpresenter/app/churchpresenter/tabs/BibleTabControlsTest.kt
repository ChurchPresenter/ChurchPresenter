@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.data.settings.BibleEngineSettings
import org.churchpresenter.app.churchpresenter.viewmodel.ContinuationSpeed
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleTabControlsTest {

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
    fun `changing the scope selector updates the scope index`() = bibleTab { vm, _ ->
        assertEquals(0, vm.selectedScopeIndex.value)

        onNodeWithText(BibleLabel.ENTIRE_BIBLE).performClick()
        waitForIdle()
        onNodeWithText(BibleLabel.CURRENT_BOOK).performClick()
        waitForIdle()

        assertEquals(1, vm.selectedScopeIndex.value)
    }

    @Test
    fun `changing the mode selector updates the mode index`() = bibleTab { vm, _ ->
        assertEquals(0, vm.selectedModeIndex.value)

        onNodeWithText(BibleLabel.CONTAINS_PHRASE).performClick()
        waitForIdle()
        onNodeWithText(BibleLabel.EXACT_MATCH).performClick()
        waitForIdle()

        assertEquals(1, vm.selectedModeIndex.value)
    }

    @Test
    fun `the continuation-speed chip cycles from Balanced to Fast and back`() {
        bibleTab(
            settings = { it.copy(bibleEngineSettings = BibleEngineSettings(enabled = true)) },
            stt = connectedStt()
        ) { vm, reports ->
            assertEquals(ContinuationSpeed.BALANCED, vm.continuationSpeed.value)

            onNodeWithText("Next verse speed: Balanced").performClick()
            waitForIdle()

            assertEquals(ContinuationSpeed.FAST, vm.continuationSpeed.value)
            assertEquals("fast", reports.settingsAfterChange?.bibleEngineSettings?.continuationSpeed)

            onNodeWithText("Next verse speed: Fast").performClick()
            waitForIdle()

            assertEquals(ContinuationSpeed.BALANCED, vm.continuationSpeed.value)
        }
    }
}
