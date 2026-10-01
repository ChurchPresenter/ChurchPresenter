package org.churchpresenter.lottiegen.render

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.SystemFont
import io.github.alexzhirkevich.compottie.assets.LottieFontManager
import io.github.alexzhirkevich.compottie.assets.LottieFontSpec
import org.churchpresenter.lottiegen.lottie.FontRegistry

/**
 * The fonts a preview draws whole lines of text with (see `TextShaping`).
 *
 * Letter by letter, a preview draws the glyph outlines the file embeds and needs no fonts at all;
 * a whole line is drawn from a font, and without one Compottie falls back to its own default face.
 * So this is passed only while text is drawn as whole lines: the bundled file for a family the
 * generator ships, the installed family by name for anything else.
 */
object GeneratorLottieFonts : LottieFontManager {

    private val cache = mutableMapOf<String, Font>()

    @OptIn(ExperimentalTextApi::class)
    override suspend fun font(font: LottieFontSpec): Font {
        val bold = font.weight >= FontWeight.SemiBold || font.name.endsWith("-Bold")
        val key = "${font.family}|$bold|${font.style}"
        return synchronized(cache) {
            cache.getOrPut(key) {
                val weight = if (bold) FontWeight.Bold else FontWeight.Normal
                val bytes = FontRegistry.bundledFile(font.family, bold)
                    ?.let { path -> GeneratorLottieFonts::class.java.getResourceAsStream(path)?.use { it.readBytes() } }
                if (bytes != null) {
                    Font(
                        identity = "${font.family}-$weight-${font.style}",
                        data = bytes,
                        weight = weight,
                        style = font.style,
                    )
                } else {
                    SystemFont(font.family, weight, font.style)
                }
            }
        }
    }
}
