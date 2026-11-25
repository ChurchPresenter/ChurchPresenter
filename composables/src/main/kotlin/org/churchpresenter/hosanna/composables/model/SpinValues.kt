package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Immutable
internal class SpinValues(val previous: String, val current: String, val next: String)

internal fun spinValues(
    minutes: Int,
    part: TimePart,
    use24Hour: Boolean,
    labels: TimeFieldLabels,
): SpinValues = when (part) {
    TimePart.Hour -> SpinValues(
        hourText(stepTime(minutes, TimePart.Hour, up = false), use24Hour),
        hourText(minutes, use24Hour),
        hourText(stepTime(minutes, TimePart.Hour, up = true), use24Hour),
    )
    TimePart.Minute -> SpinValues(
        minuteText(stepTime(minutes, TimePart.Minute, up = false)),
        minuteText(minutes),
        minuteText(stepTime(minutes, TimePart.Minute, up = true)),
    )
    TimePart.Period -> if (isPm(minutes)) {
        SpinValues(labels.am, labels.pm, "")
    } else {
        SpinValues("", labels.am, labels.pm)
    }
}
