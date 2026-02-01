package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment

object Utils {

    fun getAvailableSystemFonts(): List<String> {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    @OptIn(ExperimentalTextApi::class)
    fun systemFontFamilyOrDefault(fontName: String): FontFamily {
        return try {
            val name = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.first { it.name == fontName }.name
            FontFamily(name)
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    fun parseHexColor(hexColor: String): Color {
        return try {
            val cleanHex = hexColor.removePrefix("#")
            val red = cleanHex.substring(0, 2).toInt(16)
            val green = cleanHex.substring(2, 4).toInt(16)
            val blue = cleanHex.substring(4, 6).toInt(16)
            Color(red, green, blue)
        } catch (e: Exception) {
            Color.White
        }
    }
}