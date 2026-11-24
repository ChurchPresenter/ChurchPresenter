package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

@Immutable
class TimeFieldLabels(
    val hour: String,
    val minute: String,
    val period: String,
    val am: String,
    val pm: String,
)

@Immutable
class TimeFieldActions(
    val now: String,
    val done: String,
    val increase: String,
    val decrease: String,
)

@Immutable
class TimerSpinnerLabels(
    val hours: String,
    val minutes: String,
    val seconds: String,
    val increase: String,
    val decrease: String,
)

@Immutable
class ColorFieldLabels(
    val pick: String,
    val none: String,
    val opacity: String,
    val theme: String,
    val recent: String,
    val invalid: String,
)

@Immutable
class NumberFieldActions(val increase: String, val decrease: String)

@Immutable
class VolumeLabels(val volume: String, val mute: String, val unmute: String)
