package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

/**
 * The time field's screen-reader names and period words.
 *
 * @property hour the hour part's name.
 * @property minute the minute part's name.
 * @property period the AM/PM part's name.
 * @property am the word shown for morning.
 * @property pm the word shown for afternoon.
 */
@Immutable
class TimeFieldLabels(
    val hour: String,
    val minute: String,
    val period: String,
    val am: String,
    val pm: String,
)

/**
 * The time spinner's button texts.
 *
 * @property now the button that sets the current time.
 * @property done the button that closes the spinner.
 * @property increase the screen-reader name of the up arrows.
 * @property decrease the screen-reader name of the down arrows.
 */
@Immutable
class TimeFieldActions(
    val now: String,
    val done: String,
    val increase: String,
    val decrease: String,
)

/**
 * The timer spinner's column names and arrow names.
 *
 * @property hours the hours column's name.
 * @property minutes the minutes column's name.
 * @property seconds the seconds column's name.
 * @property increase the screen-reader name of the up arrows.
 * @property decrease the screen-reader name of the down arrows.
 */
@Immutable
class TimerSpinnerLabels(
    val hours: String,
    val minutes: String,
    val seconds: String,
    val increase: String,
    val decrease: String,
)

/**
 * The color field's and picker's texts.
 *
 * @property pick the screen-reader name of the swatch that opens the picker.
 * @property none the word shown when no color is set.
 * @property opacity the opacity slider's name.
 * @property theme the title over the theme colors.
 * @property recent the title over recent colors.
 * @property invalid the error shown for text that is not a color.
 */
@Immutable
class ColorFieldLabels(
    val pick: String,
    val none: String,
    val opacity: String,
    val theme: String,
    val recent: String,
    val invalid: String,
)

/**
 * The screen-reader names of a number field's arrows.
 *
 * @property increase the up arrow's name.
 * @property decrease the down arrow's name.
 */
@Immutable
class NumberFieldActions(val increase: String, val decrease: String)

/**
 * The volume control's screen-reader names.
 *
 * @property volume the slider's name.
 * @property mute the button's name while sound is on.
 * @property unmute the button's name while muted.
 */
@Immutable
class VolumeLabels(val volume: String, val mute: String, val unmute: String)

/**
 * The "Tab style" section of the tab visibility menu.
 *
 * @property title the section title.
 * @property iconAndText the icon-and-text option.
 * @property text the text-only option.
 * @property icon the icon-only option.
 */
@Immutable
class TabStyleLabels(val title: String, val iconAndText: String, val text: String, val icon: String)

/**
 * The screen-reader actions that move a row.
 *
 * @property up the move-up action.
 * @property down the move-down action.
 */
@Immutable
class MoveLabels(val up: String, val down: String)

/**
 * The texts of [org.churchpresenter.hosanna.composables.navigation.TabVisibilityButton].
 *
 * @property button the eye button's name and tooltip.
 * @property style the "Tab style" section.
 * @property showTabs the title over the tab checkboxes.
 * @property reset the reset button.
 * @property lastTab why the last visible tab cannot be hidden, shown in its tooltip.
 * @property move the screen-reader actions that reorder a tab.
 */
@Immutable
class TabMenuLabels(
    val button: String,
    val style: TabStyleLabels,
    val showTabs: String,
    val reset: String,
    val lastTab: String,
    val move: MoveLabels,
)
