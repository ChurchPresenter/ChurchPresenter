package org.churchpresenter.settings

import kotlinx.serialization.Serializable

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

/** The calendar day [millis] falls in, counted from the epoch — what "one tip a day" is measured in. */
fun helperDayOf(millis: Long): Long = millis / MILLIS_PER_DAY

/**
 * The helper lamp's persisted state: whether it is shown, which suggestions the operator has put away,
 * and where the tip rotation is.
 *
 * @property enabled whether the lamp sits in the main window at all
 * @property tipsEnabled whether it offers a tip of the day
 * @property dismissedSuggestions suggestion ids the operator said not to show again
 * @property snoozedUntil suggestion id → the epoch millis before which it stays quiet
 * @property nextTipIndex the tip the next rotation shows
 * @property lastTipDay the [helperDayOf] the last tip was offered on, so there is at most one a day
 * @property introSeen whether the first-run "Meet Wick" dialog has been finished or skipped
 * @property startedByUser whether the operator has started Wick from Help → Show Helper; outside dev mode
 *   nothing of Wick appears until they have
 */
@Serializable
data class HelperSettings(
    val enabled: Boolean = true,
    val tipsEnabled: Boolean = true,
    val dismissedSuggestions: Set<String> = emptySet(),
    val snoozedUntil: Map<String, Long> = emptyMap(),
    val nextTipIndex: Int = 0,
    val lastTipDay: Long = 0L,
    val introSeen: Boolean = false,
    val startedByUser: Boolean = false,
)

/** Whether Wick is here, in [devMode] or with [helper] as saved: dev mode, or started from Help → Show Helper. */
fun isWickAvailable(devMode: Boolean, helper: HelperSettings): Boolean = devMode || helper.startedByUser

/** Whether suggestion [id] may be shown at [nowMillis]: not dismissed, and not inside a snooze. */
fun HelperSettings.allows(id: String, nowMillis: Long): Boolean =
    id !in dismissedSuggestions && (snoozedUntil[id] ?: 0L) <= nowMillis

/** These settings with suggestion [id] put away for good. */
fun HelperSettings.dismissing(id: String): HelperSettings =
    copy(dismissedSuggestions = dismissedSuggestions + id, snoozedUntil = snoozedUntil - id)

/** These settings with suggestion [id] quiet until [untilMillis]. */
fun HelperSettings.snoozing(id: String, untilMillis: Long): HelperSettings =
    copy(snoozedUntil = snoozedUntil + (id to untilMillis))

/** Whether a tip may be offered at [nowMillis] — tips are on and none has been offered today. */
fun HelperSettings.tipDue(nowMillis: Long): Boolean = tipsEnabled && helperDayOf(nowMillis) > lastTipDay

/** These settings after a tip was offered at [nowMillis], with the rotation moved on by one. */
fun HelperSettings.tipShown(nowMillis: Long): HelperSettings =
    copy(lastTipDay = helperDayOf(nowMillis), nextTipIndex = nextTipIndex + 1)

/** These settings with every dismissal and snooze forgotten. */
fun HelperSettings.resettingSuggestions(): HelperSettings =
    copy(dismissedSuggestions = emptySet(), snoozedUntil = emptyMap())
