package org.churchpresenter.lottiegen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.editor.ui.EditorPreview
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class PreviewPainterTest {

    private val json = Json.encodeToString(
        JsonObject.serializer(),
        LottieGenerator.generate(
            LottieGenConfig(bgColor = "#FF0000", accentColor = "#FF0000", borderColor = "#FF0000"),
        ),
    )

    @Test
    fun `the generator preview draws the playing lower third`() =
        LottiePreviewProbe.run({ PreviewPanel(jsonString = json, aspectRatio = 16f / 9f, statusText = "") }) {
            assertTrue(redPixels() < LottiePreviewProbe.DRAWN, "red before the composition loaded")
            pumpUntil("the lower third drawn") { it >= LottiePreviewProbe.DRAWN }
        }

    @Test
    fun `the editor preview follows the seek once paused`() {
        var playing by mutableStateOf(true)
        var seek by mutableStateOf(0f)
        LottiePreviewProbe.run({ EditorPreview(json, 16f / 9f, playing, seek, {}, {}) }) {
            pumpUntil("the lower third drawn") { it >= LottiePreviewProbe.DRAWN }
            playing = false
            seek = 0f
            pumpUntil("the paused preview back at its first frame") { it == 0 }
        }
    }
}
