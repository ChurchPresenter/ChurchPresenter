package org.churchpresenter.sharedui.models

import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.announcements
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.crossword_tab
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.media
import org.churchpresenter.strings.generated.resources.pictures
import org.churchpresenter.strings.generated.resources.presentation
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.tab_canvas
import org.churchpresenter.strings.generated.resources.tab_companion_surface
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_qa
import org.churchpresenter.strings.generated.resources.tab_stt
import org.churchpresenter.strings.generated.resources.tab_web
import org.jetbrains.compose.resources.StringResource

/** The name this tab is shown under in the tab row. */
val Tabs.labelRes: StringResource
    get() = when (this) {
        Tabs.BIBLE -> Res.string.bible
        Tabs.SONGS -> Res.string.songs
        Tabs.PICTURES -> Res.string.pictures
        Tabs.PRESENTATION -> Res.string.presentation
        Tabs.MEDIA -> Res.string.media
        Tabs.LOWER_THIRD -> Res.string.display_lower_third
        Tabs.ANNOUNCEMENTS -> Res.string.announcements
        Tabs.WEB -> Res.string.tab_web
        Tabs.CANVAS -> Res.string.tab_canvas
        Tabs.QA -> Res.string.tab_qa
        Tabs.STT -> Res.string.tab_stt
        Tabs.CROSSWORD -> Res.string.crossword_tab
        Tabs.DICTIONARY -> Res.string.tab_dictionary
        Tabs.COMPANION_SURFACE -> Res.string.tab_companion_surface
    }
