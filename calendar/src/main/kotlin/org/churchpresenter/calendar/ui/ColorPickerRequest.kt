package org.churchpresenter.calendar.ui

/**
 * A color asked to be chosen, and what to do with the answer.
 *
 * The same shape as `:songlibrary`'s `SongEditorRequest`, and for the same reason: the app already
 * has a color picker — the one the Announcements tab, the Canvas and the text backdrop all use —
 * and a second one living here would be a second thing to keep in step. The host supplies its own,
 * so a color is chosen the same way wherever it is chosen.
 *
 * [initialHex] is `#RRGGBB`. [onPicked] receives the same form.
 */
data class ColorPickerRequest(
    val initialHex: String,
    val onPicked: (String) -> Unit,
    val onDismiss: () -> Unit,
)
