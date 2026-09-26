package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The schedule toolbar's buttons, in the order they are drawn — each one the operator can turn off
 * from the panel's options menu. Persisted by `name` in `AppSettings.hiddenScheduleButtons`, so a
 * name this build does not know (an older or newer entry) simply hides nothing.
 */
enum class ScheduleToolbarButton {
    /** Title-row readouts rather than toolbar buttons, but hidden from the same menu. */
    ITEM_COUNT, ZOOM,
    NEW, OPEN, SAVE, CLEAR, UNDO, REDO, ADD_LABEL, PLANNING_CENTER, CALENDAR;

    /** The menu's own test tag for this entry. */
    val menuTag: String get() = "schedule_options_button_${name.lowercase()}"
}

/**
 * How large the schedule toolbar's icons are drawn, chosen from the panel's options menu. Persisted by
 * `name` in `AppSettings.scheduleToolbarIconSize`; [SMALL] is the size they had before the choice.
 */
enum class ScheduleToolbarIconSize(val buttonSize: Dp, val iconSize: Dp) {
    SMALL(26.dp, 14.dp),
    MEDIUM(32.dp, 18.dp),
    LARGE(40.dp, 22.dp);

    /** The menu's own test tag for this entry. */
    val menuTag: String get() = "schedule_options_icon_size_${name.lowercase()}"

    companion object {
        /** The size named [name], or [SMALL] for a name this build does not know. */
        fun fromName(name: String): ScheduleToolbarIconSize = entries.firstOrNull { it.name == name } ?: SMALL
    }
}

/** The file group, the history group, the extras group — what the two pill dividers separate. */
private val TOOLBAR_GROUPS = listOf(
    listOf(
        ScheduleToolbarButton.NEW, ScheduleToolbarButton.OPEN,
        ScheduleToolbarButton.SAVE, ScheduleToolbarButton.CLEAR,
    ),
    listOf(ScheduleToolbarButton.UNDO, ScheduleToolbarButton.REDO),
    listOf(
        ScheduleToolbarButton.ADD_LABEL, ScheduleToolbarButton.PLANNING_CENTER,
        ScheduleToolbarButton.CALENDAR,
    ),
)

/** Whether a divider still separates anything once [hidden] is taken out. */
internal fun scheduleToolbarDividerVisible(groupIndex: Int, hidden: Set<String>): Boolean {
    fun groupShown(index: Int) = TOOLBAR_GROUPS[index].any { it.name !in hidden }
    return groupShown(groupIndex) && (groupIndex + 1..TOOLBAR_GROUPS.lastIndex).any { groupShown(it) }
}

/** Whether the toolbar row has anything left to draw — the title row's own readouts don't count. */
internal fun scheduleToolbarVisible(hidden: Set<String>): Boolean =
    TOOLBAR_GROUPS.flatten().any { it.name !in hidden }
