package org.churchpresenter.settings

/**
 * The slots of a song's [available] languages the Songs tab's lyrics panel shows, in slot order:
 * every one [AppSettings.songPanelHiddenLanguages] does not hide. Never empty -- with all of them
 * hidden, the first shows -- so a song's words cannot vanish from the panel.
 */
fun AppSettings.panelLanguageSlots(available: Int): List<Int> {
    val slots = (0 until available.coerceAtLeast(1)).toList()
    return slots.filterNot { it in songPanelHiddenLanguages }.ifEmpty { slots.take(1) }
}

/** These settings with language [slot] -- `0` the primary -- shown in the lyrics panel, or hidden from it. */
fun AppSettings.withPanelLanguageShown(slot: Int, shown: Boolean): AppSettings {
    val hidden = if (shown) songPanelHiddenLanguages - slot else songPanelHiddenLanguages + slot
    return copy(songPanelHiddenLanguages = hidden.distinct().sorted())
}
