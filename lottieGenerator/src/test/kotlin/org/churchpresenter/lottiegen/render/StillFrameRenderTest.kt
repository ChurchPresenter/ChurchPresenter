package org.churchpresenter.lottiegen.render

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.lottie.LottieGenerator
import org.churchpresenter.lottiegen.model.LottieGenConfig
import org.churchpresenter.lottiegen.tools.DumpStyleReview
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StillFrameRenderTest {

    private val temp: File = Files.createTempDirectory("still-frame-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    @Test
    fun `a generated lower third renders to a still with content in it`() {
        val json = Json.encodeToString(JsonObject.serializer(), LottieGenerator.generate(LottieGenConfig(canvasW = 320, canvasH = 180)))
        val pixels = runBlocking { StillFrame.render(json, 320, 180, 0.6f) }
        assertEquals(320 * 180, pixels.size)
        assertNotNull(StillFrame.frameBounds(pixels, 320, 180))
    }

    @Test
    fun `the review tool stops on no styles or unknown ones and writes nothing`() {
        DumpStyleReview.main(emptyArray())
        val out = File(temp, "review")
        DumpStyleReview.main(arrayOf("nope, ,also-nope", out.absolutePath))
        assertTrue(out.isDirectory)
        assertTrue(out.listFiles().orEmpty().isEmpty())
    }
}
