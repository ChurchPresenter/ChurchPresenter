package org.churchpresenter.lottiegen.lottie

import java.awt.FontFormatException
import java.io.IOException
import java.awt.Font
import java.awt.GraphicsEnvironment

object FontRegistry {

    private val loadedFonts = mutableMapOf<String, Font>()
    private var initialized = false

    /** Font file mappings: familyName -> list of (resourcePath, awtStyle) */
    private val fontFiles = mapOf(
        "Open Sans" to listOf("OpenSans-Regular.ttf" to Font.PLAIN, "OpenSans-Bold.ttf" to Font.BOLD),
        "Poppins" to listOf("Poppins-Regular.ttf" to Font.PLAIN, "Poppins-Bold.ttf" to Font.BOLD),
        "Raleway" to listOf("Raleway-Regular.ttf" to Font.PLAIN, "Raleway-Bold.ttf" to Font.BOLD),
        "Anonymous Pro" to listOf("AnonymousPro-Regular.ttf" to Font.PLAIN, "AnonymousPro-Bold.ttf" to Font.BOLD),
        "Patua One" to listOf("PatuaOne-Regular.ttf" to Font.PLAIN),
        "Lora" to listOf("Lora-Regular.ttf" to Font.PLAIN, "Lora-Bold.ttf" to Font.BOLD),
        "Abril Fatface" to listOf("AbrilFatface-Regular.ttf" to Font.PLAIN),
        "Cookie" to listOf("Cookie-Regular.ttf" to Font.PLAIN),
        "Oleo Script" to listOf("OleoScript-Regular.ttf" to Font.PLAIN, "OleoScript-Bold.ttf" to Font.BOLD),
        "Kalam" to listOf("Kalam-Regular.ttf" to Font.PLAIN, "Kalam-Bold.ttf" to Font.BOLD),
        "Fredoka One" to listOf("FredokaOne-Regular.ttf" to Font.PLAIN)
    )

    fun initialize() {
        if (initialized) return
        initialized = true

        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()

        for ((family, files) in fontFiles) {
            for ((fileName, style) in files) {
                register(ge, family, fileName, style)
            }
        }
    }

    /** Loads one bundled font file and registers it; a font that will not load is skipped. */
    private fun register(ge: GraphicsEnvironment, family: String, fileName: String, style: Int) {
        val stream = FontRegistry::class.java.getResourceAsStream("/fonts/$fileName") ?: return
        try {
            val font = Font.createFont(Font.TRUETYPE_FONT, stream)
            ge.registerFont(font)
            loadedFonts["$family-$style"] = font
            stream.close()
        } catch (e: FontFormatException) {
            System.err.println("Failed to load font $fileName: ${e.message}")
        } catch (e: IOException) {
            System.err.println("Failed to load font $fileName: ${e.message}")
        }
    }

    /**
     * Get a Font instance for the given family, style, and size.
     * Falls back to the system font if the bundled font is not available.
     */
    fun getFont(family: String, awtStyle: Int, size: Float): Font {
        initialize()

        val key = "$family-$awtStyle"
        val baseFont = loadedFonts[key]
        if (baseFont != null) {
            return baseFont.deriveFont(size)
        }

        // Try plain style as fallback for families that don't have bold
        if (awtStyle == Font.BOLD) {
            val plainKey = "$family-${Font.PLAIN}"
            val plainFont = loadedFonts[plainKey]
            if (plainFont != null) {
                return plainFont.deriveFont(Font.BOLD, size)
            }
        }

        // Fallback to system font
        return Font(family, awtStyle, size.toInt()).deriveFont(size)
    }
}
