package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.time.chrono.Chronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

object Utils {

    /** The installed font families — see [SystemFonts], which enumerates them once per process. */
    fun getAvailableSystemFonts(): List<String> = SystemFonts.families()

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

    /**
     * WCAG 2 relative luminance of [color], in the 0..1 range contrast-ratio math uses.
     * https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html
     */
    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val cs = c.toDouble()
            return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    /** WCAG 2 contrast ratio between two colors — 1:1 (identical) up to 21:1 (black on white). */
    fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a) + 0.05
        val l2 = relativeLuminance(b) + 0.05
        return if (l1 > l2) l1 / l2 else l2 / l1
    }

    /**
     * [foreground] as-is if it already meets [minRatio] against [background] — WCAG AA for
     * normal text is 4.5:1, the default — otherwise whichever of pure white or pure black
     * contrasts better with [background]. For user-chosen color pairs (e.g. a schedule label's
     * saved text/background) that can land close enough to unreadable; this only changes what's
     * rendered, never what's stored, so the original picked color is still shown if the user
     * reopens the editor.
     */
    fun ensureContrast(foreground: Color, background: Color, minRatio: Double = 4.5): Color {
        if (contrastRatio(foreground, background) >= minRatio) return foreground
        val whiteContrast = contrastRatio(Color.White, background)
        val blackContrast = contrastRatio(Color.Black, background)
        return if (whiteContrast >= blackContrast) Color.White else Color.Black
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
