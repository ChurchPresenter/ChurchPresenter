package org.churchpresenter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily

private const val SRGB_LINEAR_THRESHOLD = 0.03928
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_GAMMA_OFFSET = 0.055
private const val SRGB_GAMMA_DIVISOR = 1.055
private const val SRGB_GAMMA_EXPONENT = 2.4
private const val LUMINANCE_RED = 0.2126
private const val LUMINANCE_GREEN = 0.7152
private const val LUMINANCE_BLUE = 0.0722
private const val HEX_ARGB_LENGTH = 8
private const val HEX_RGB_LENGTH = 6

object Utils {

    /** The installed font families — see [SystemFonts], which enumerates them once per process. */
    fun getAvailableSystemFonts(): List<String> = SystemFonts.families()

    @OptIn(ExperimentalTextApi::class)
    fun systemFontFamilyOrDefault(fontName: String): FontFamily {
        return try {
            FontFamily(fontName)
        } catch (_: Exception) {
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
            return if (cs <= SRGB_LINEAR_THRESHOLD) cs / SRGB_LINEAR_DIVISOR
            else Math.pow((cs + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_DIVISOR, SRGB_GAMMA_EXPONENT)
        }
        return LUMINANCE_RED * channel(color.red) +
            LUMINANCE_GREEN * channel(color.green) +
            LUMINANCE_BLUE * channel(color.blue)
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
                HEX_ARGB_LENGTH -> {
                    val alpha = cleanHex.substring(0, 2).toInt(16)
                    val red = cleanHex.substring(2, 4).toInt(16)
                    val green = cleanHex.substring(4, 6).toInt(16)
                    val blue = cleanHex.substring(6, 8).toInt(16)
                    Color(red, green, blue, alpha)
                }
                HEX_RGB_LENGTH -> {
                    val red = cleanHex.substring(0, 2).toInt(16)
                    val green = cleanHex.substring(2, 4).toInt(16)
                    val blue = cleanHex.substring(4, 6).toInt(16)
                    Color(red, green, blue)
                }
                else -> Color.White
            }
        } catch (_: Exception) {
            Color.White
        }
    }
}
