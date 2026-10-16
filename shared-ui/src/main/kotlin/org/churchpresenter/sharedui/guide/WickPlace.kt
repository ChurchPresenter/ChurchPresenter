package org.churchpresenter.sharedui.guide

import androidx.compose.runtime.compositionLocalOf
import org.churchpresenter.sharedui.models.Tabs

/** Where Wick's lamp is shown, which decides the chips and tips it offers there. */
sealed interface WickPlace {
    /** The main window, on [tab]; null before the tab is known. */
    data class Main(val tab: Tabs?) : WickPlace

    /** The Settings dialog, on [page]; null for a page that is not a [SettingsPage] (Companion Satellite). */
    data class Settings(val page: SettingsPage?) : WickPlace

    /** Any other window: the song editor, Planning Center's import, the tool windows. */
    data object Other : WickPlace
}

/** The place of the window a lamp is drawn in: a window that knows its own provides it. */
val LocalWickPlace = compositionLocalOf<WickPlace> { WickPlace.Other }
