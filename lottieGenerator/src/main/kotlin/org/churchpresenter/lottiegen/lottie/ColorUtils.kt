package org.churchpresenter.lottiegen.lottie

/** Where each channel starts in a `RRGGBB` string, and how wide it is. */
private const val HEX_RADIX = 16
private const val CHANNEL_WIDTH = 2
private const val RED_AT = 0
private const val GREEN_AT = 2
private const val BLUE_AT = 4

/** The largest value one channel can hold, as a divisor onto Lottie's 0..1 range. */
private const val CHANNEL_MAX = 255.0

/**
 * Lottie colours are written with four decimal places; anything finer only bloats the JSON, and
 * rounding here keeps a re-export byte-identical to the file it came from.
 */
private const val COLOR_PRECISION = 10_000

private fun String.channelAt(offset: Int): Int =
    substring(offset, offset + CHANNEL_WIDTH).toInt(HEX_RADIX)

private fun Int.toLottieChannel(): Double =
    (this / CHANNEL_MAX * COLOR_PRECISION).toLong() / COLOR_PRECISION.toDouble()

/**
 * Convert hex color string (#RRGGBB) to Lottie RGB (0-1 range).
 */
fun hexToLottie(hex: String): List<Double> {
    val clean = hex.removePrefix("#")
    return listOf(
        clean.channelAt(RED_AT).toLottieChannel(),
        clean.channelAt(GREEN_AT).toLottieChannel(),
        clean.channelAt(BLUE_AT).toLottieChannel(),
    )
}

/**
 * Convert hex color string (#RRGGBB) to RGB (0-255).
 */
fun hexToRgb(hex: String): Triple<Int, Int, Int> {
    val clean = hex.removePrefix("#")
    return Triple(
        clean.channelAt(RED_AT),
        clean.channelAt(GREEN_AT),
        clean.channelAt(BLUE_AT),
    )
}

/** Convert em to px */
fun emToPx(em: Double, baseSizePx: Double): Double = em * baseSizePx

/** Convert rem to px (same as em for our purposes) */
fun remToPx(rem: Double, baseSizePx: Double): Double = rem * baseSizePx
