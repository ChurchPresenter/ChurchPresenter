package org.churchpresenter.helper.action

import org.churchpresenter.settings.AppSettings

/** The parts of the settings document a helper change can touch, and so the parts its undo restores. */
enum class SettingsSection {
    BACKGROUND,
    PROJECTION,
    HIDDEN_TABS;

    internal fun read(settings: AppSettings): Any = when (this) {
        BACKGROUND -> settings.backgroundSettings
        PROJECTION -> settings.projectionSettings
        HIDDEN_TABS -> settings.hiddenTabs
    }

    internal fun copyInto(target: AppSettings, from: AppSettings): AppSettings = when (this) {
        BACKGROUND -> target.copy(backgroundSettings = from.backgroundSettings)
        PROJECTION -> target.copy(projectionSettings = from.projectionSettings)
        HIDDEN_TABS -> target.copy(hiddenTabs = from.hiddenTabs)
    }
}

/** The sections [before] and [after] differ in. */
fun changedSections(before: AppSettings, after: AppSettings): Set<SettingsSection> =
    SettingsSection.entries.filter { it.read(before) != it.read(after) }.toSet()

/**
 * [current] with the [sections] a change touched put back the way they were [before] it — or null
 * when any of them has moved on from what the change left ([after]), since restoring then would
 * throw away the operator's own later edit.
 */
fun revertSections(
    current: AppSettings,
    before: AppSettings,
    after: AppSettings,
    sections: Set<SettingsSection>,
): AppSettings? {
    if (sections.any { it.read(current) != it.read(after) }) return null
    return sections.fold(current) { acc, section -> section.copyInto(acc, before) }
}
