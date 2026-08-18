package lottiegen.lottie

/**
 * Convert hex color string (#RRGGBB) to Lottie RGB (0-1 range).
 */
fun hexToLottie(hex: String): List<Double> {
    val clean = hex.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16)
    val g = clean.substring(2, 4).toInt(16)
    val b = clean.substring(4, 6).toInt(16)
    return listOf(
        (r / 255.0 * 10000).toLong() / 10000.0,
        (g / 255.0 * 10000).toLong() / 10000.0,
        (b / 255.0 * 10000).toLong() / 10000.0
    )
}

/**
 * Convert hex color string (#RRGGBB) to RGB (0-255).
 */
fun hexToRgb(hex: String): Triple<Int, Int, Int> {
    val clean = hex.removePrefix("#")
    return Triple(
        clean.substring(0, 2).toInt(16),
        clean.substring(2, 4).toInt(16),
        clean.substring(4, 6).toInt(16)
    )
}

/** Convert em to px */
fun emToPx(em: Double, baseSizePx: Double): Double = em * baseSizePx

/** Convert rem to px (same as em for our purposes) */
fun remToPx(rem: Double, baseSizePx: Double): Double = rem * baseSizePx
