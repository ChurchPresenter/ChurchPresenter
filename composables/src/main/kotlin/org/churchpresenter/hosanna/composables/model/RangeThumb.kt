package org.churchpresenter.hosanna.composables.model

internal enum class RangeThumb { Start, End }

internal fun nearestRangeThumb(value: Double, start: Double, end: Double): RangeThumb = when {
    value >= end -> RangeThumb.End
    value <= start -> RangeThumb.Start
    value - start < end - value -> RangeThumb.Start
    else -> RangeThumb.End
}

internal fun moveRangeThumb(thumb: RangeThumb, value: Double, start: Double, end: Double): Pair<Double, Double> =
    when (thumb) {
        RangeThumb.Start -> minOf(value, end) to end
        RangeThumb.End -> start to maxOf(value, start)
    }
