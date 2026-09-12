package org.churchpresenter.lottiegen.band

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Remembers the band generator's last configuration between openings, in the generator's own
 * folder under the app data directory. Everything carries over except the canvas size, which
 * has to match the output the host is generating for, and the sample text when the host opens
 * it for the other kind of content.
 */
internal object BandConfigStorage {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(): File {
        val dir = File(System.getProperty("user.home"), ".churchpresenter/churchpresenter-lottiegen")
        dir.mkdirs()
        return File(dir, "bible-band-last.json")
    }

    fun load(): BibleLottieGenConfig? {
        val f = file()
        if (!f.isFile) return null
        return try {
            json.decodeFromString(BibleLottieGenConfig.serializer(), f.readText())
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun save(config: BibleLottieGenConfig) {
        try {
            file().writeText(json.encodeToString(BibleLottieGenConfig.serializer(), config))
        } catch (_: IOException) {
            // Not remembering is the worst case; the generator still works.
        }
    }

    /** The remembered design, on the canvas the host sized; [seed] alone when nothing is remembered. */
    fun restore(seed: BibleLottieGenConfig): BibleLottieGenConfig {
        val last = load() ?: return seed
        // Opened for the other content, the sample text and layout come from the host again: a
        // song's lyric is not a Bible verse, and its title is not a reference.
        val sameKind = last.kind == seed.kind
        return last.copy(
            canvasW = seed.canvasW,
            canvasH = seed.canvasH,
            kind = seed.kind,
            layout = if (sameKind) last.layout else seed.layout,
            referencePlacement = if (sameKind) last.referencePlacement else seed.referencePlacement,
            previewText1 = if (sameKind) last.previewText1 else seed.previewText1,
            previewReference1 = if (sameKind) last.previewReference1 else seed.previewReference1,
            previewText2 = if (sameKind) last.previewText2 else seed.previewText2,
            previewReference2 = if (sameKind) last.previewReference2 else seed.previewReference2,
        )
    }
}
