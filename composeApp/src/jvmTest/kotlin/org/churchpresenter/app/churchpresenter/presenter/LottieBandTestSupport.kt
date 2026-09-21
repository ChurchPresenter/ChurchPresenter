package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenerator
import java.io.File

/** A generated band template written where a test can point the settings at it. */
internal object LottieBandTestSupport {
    private val json = Json { prettyPrint = false }

    fun templateJson(cfg: BibleLottieGenConfig = BibleLottieGenConfig(canvasW = 960, canvasH = 180)): String =
        json.encodeToString(JsonObject.serializer(), BibleLottieGenerator.generate(cfg))

    fun writeTemplate(
        dir: File,
        name: String = "band.json",
        cfg: BibleLottieGenConfig = BibleLottieGenConfig(canvasW = 960, canvasH = 180),
    ): File = File(dir, name).apply { writeText(templateJson(cfg)) }
}
