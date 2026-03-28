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
            FontFamily(fontName)
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    fun parseHexColor(hexColor: String): Color {
        return try {
            val cleanHex = hexColor.removePrefix("#")
            when (cleanHex.length) {
                8 -> {
                    val alpha = cleanHex.substring(0, 2).toInt(16)
                    val red = cleanHex.substring(2, 4).toInt(16)
                    val green = cleanHex.substring(4, 6).toInt(16)
                    val blue = cleanHex.substring(6, 8).toInt(16)
                    Color(red, green, blue, alpha)
                }
                6 -> {
                    val red = cleanHex.substring(0, 2).toInt(16)
                    val green = cleanHex.substring(2, 4).toInt(16)
                    val blue = cleanHex.substring(4, 6).toInt(16)
                    Color(red, green, blue)
                }
                else -> Color.White
            }
        } catch (e: Exception) {
            Color.White
        }
    }
}