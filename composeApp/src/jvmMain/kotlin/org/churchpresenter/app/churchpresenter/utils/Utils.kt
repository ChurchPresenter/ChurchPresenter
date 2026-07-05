package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

object Utils {

    fun getAvailableSystemFonts(): List<String> {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.sortedBy { it.lowercase() }
    }

    /** True if the system's default locale displays time in 24-hour format (no AM/PM). */
    fun isSystemUsing24HourFormat(): Boolean {
        val locale = Locale.getDefault()
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            null, FormatStyle.SHORT, Chronology.ofLocale(locale), locale
        )
        return !pattern.contains('h')
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
        if (hexColor.equals("transparent", ignoreCase = true)) return Color.Transparent
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