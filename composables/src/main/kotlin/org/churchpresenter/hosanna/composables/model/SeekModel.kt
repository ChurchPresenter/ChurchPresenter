package org.churchpresenter.hosanna.composables.model

import org.churchpresenter.hosanna.composables.defaults.SeekBarDefaults

private const val MINUTE = 60L
private const val HOUR = 3600L

internal fun clockText(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toLong()
    val h = total / HOUR
    val m = total % HOUR / MINUTE
    val s = total % MINUTE
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun seekFraction(position: Double, duration: Double): Float =
    if (duration <= 0) 0f else (position / duration).coerceIn(0.0, 1.0).toFloat()

internal fun seekTimeAt(fraction: Float, duration: Double): Double =
    fraction.coerceIn(0f, 1f) * duration.coerceAtLeast(0.0)

internal fun applySeekKey(key: SliderKey, big: Boolean, position: Double, duration: Double): Double {
    val step = if (big) SeekBarDefaults.BIG_STEP_SECONDS else SeekBarDefaults.STEP_SECONDS
    return when (key) {
        SliderKey.Down -> position - step
        SliderKey.Up -> position + step
        SliderKey.Home, SliderKey.Reset -> 0.0
        SliderKey.End -> duration
    }.coerceIn(0.0, duration.coerceAtLeast(0.0))
}
