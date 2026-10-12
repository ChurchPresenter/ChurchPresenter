@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.happypath

import androidx.compose.ui.test.hasText
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.stt.STTManager
import org.churchpresenter.stt.transcribe
import kotlin.test.Test

private const val CAPTION = "grace and peace to you from God our Father"

class SttHappyPathTest : HappyPathHarness() {

    // The words arrive whole rather than typed out, so the output shows them at once.
    private fun library() = settings(Tabs.STT).copy(sttSettings = STTSettings(dripFeedEnabled = false))

    @Test
    fun `captions from the server show in the tab`() {
        val stt = STTManager()
        happyPath(library(), sttManager = stt) { _ ->
            // What the socket's connect callback runs, and what `transcription_update` delivers.
            stt.applyConnected()
            stt.transcribe(CAPTION)
            waitForIdle()

            await("the caption is in the tab") {
                onAllNodes(hasText(CAPTION, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun `live captions reach the output`() {
        val stt = STTManager()
        happyPath(library(), sttManager = stt) { app ->
            stt.applyConnected()
            stt.transcribe(CAPTION)
            waitForIdle()
            goLive()

            await("captions are on air") { app.presenter.isLive(Presenting.STT) }
            awaitOnOutput(app, CAPTION)
        }
    }
}
