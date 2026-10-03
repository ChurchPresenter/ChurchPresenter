@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.BibleEngineSettings
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsExactly
import org.churchpresenter.stt.STTManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class BibleTabEngineStatusTest {

    private class FakeEngineStatus(
        connected: Boolean = false,
        startFailed: Boolean = false,
        engineStt: Boolean? = null,
    ) : BibleEngineStatus {
        override val connected: State<Boolean> = mutableStateOf(connected)
        override val startFailed: State<Boolean> = mutableStateOf(startFailed)
        override val engineSttConnected: State<Boolean?> = mutableStateOf(engineStt)
    }

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

    private fun engineTab(status: BibleEngineStatus, expected: String) = bibleTab(
        settings = { it.copy(bibleEngineSettings = BibleEngineSettings(enabled = true)) },
        stt = connectedStt(),
        engineStatus = status,
    ) { _, _ ->
        assertTrue(showsExactly(expected), renderedText().toString())
    }

    @Test
    fun `an engine that failed to start is reported unavailable`() =
        engineTab(FakeEngineStatus(startFailed = true), "Engine unavailable")

    @Test
    fun `an engine that lost the speech feed says so`() =
        engineTab(FakeEngineStatus(connected = true, engineStt = false), "Engine lost its connection to the STT server")

    @Test
    fun `a running engine with nothing heard yet is waiting for speech`() =
        engineTab(FakeEngineStatus(connected = true, engineStt = true), "Running — waiting for STT")
}
