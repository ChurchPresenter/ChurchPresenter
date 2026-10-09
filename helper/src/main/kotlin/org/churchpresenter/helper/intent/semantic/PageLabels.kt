package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.appearance
import org.churchpresenter.strings.generated.resources.atem_settings
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.strings.generated.resources.bible
import org.churchpresenter.strings.generated.resources.media_subtitles
import org.churchpresenter.strings.generated.resources.obs_settings
import org.churchpresenter.strings.generated.resources.output_profiles_tab
import org.churchpresenter.strings.generated.resources.profile_nav_content
import org.churchpresenter.strings.generated.resources.profile_nav_general
import org.churchpresenter.strings.generated.resources.profile_nav_live_captions
import org.churchpresenter.strings.generated.resources.profile_nav_stage_layout
import org.churchpresenter.strings.generated.resources.profile_outputs_group
import org.churchpresenter.strings.generated.resources.projection
import org.churchpresenter.strings.generated.resources.server_settings
import org.churchpresenter.strings.generated.resources.songs
import org.churchpresenter.strings.generated.resources.tab_dictionary
import org.churchpresenter.strings.generated.resources.tab_qa
import org.jetbrains.compose.resources.StringResource

/** What the Settings dialog's tab for [page] is called. */
internal fun settingsPageLabel(page: SettingsPage): StringResource = when (page) {
    SettingsPage.SYSTEM -> Res.string.appearance
    SettingsPage.BIBLE -> Res.string.bible
    SettingsPage.BACKGROUND -> Res.string.background
    SettingsPage.PROFILES -> Res.string.output_profiles_tab
    SettingsPage.PROJECTION -> Res.string.projection
    SettingsPage.SERVER -> Res.string.server_settings
    SettingsPage.ATEM -> Res.string.atem_settings
    SettingsPage.INTEGRATIONS -> Res.string.obs_settings
}

/** What the Profiles page's section list calls the page named [name] (`SONGS`, `GENERAL`, …), or null. */
internal fun profilePageLabel(name: String): StringResource? = when (name) {
    "GENERAL" -> Res.string.profile_nav_general
    "OUTPUTS" -> Res.string.profile_outputs_group
    "CONTENT" -> Res.string.profile_nav_content
    "SONGS" -> Res.string.songs
    "BIBLE" -> Res.string.bible
    "BACKGROUND" -> Res.string.background
    "CAPTIONS" -> Res.string.profile_nav_live_captions
    "SUBTITLES" -> Res.string.media_subtitles
    "QA" -> Res.string.tab_qa
    "DICTIONARY" -> Res.string.tab_dictionary
    "STAGE_MONITOR" -> Res.string.profile_nav_stage_layout
    else -> null
}
