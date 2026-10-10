package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTarget
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.add_browser_source_output
import org.churchpresenter.strings.generated.resources.add_ndi_output
import org.churchpresenter.strings.generated.resources.atem_test_connection
import org.churchpresenter.strings.generated.resources.helper_hint_atem_dsk
import org.churchpresenter.strings.generated.resources.helper_hint_atem_host
import org.churchpresenter.strings.generated.resources.helper_hint_atem_test
import org.churchpresenter.strings.generated.resources.helper_hint_browser_source_add
import org.churchpresenter.strings.generated.resources.helper_hint_browser_source_name
import org.churchpresenter.strings.generated.resources.helper_hint_edit_song_field
import org.churchpresenter.strings.generated.resources.helper_hint_ndi_add
import org.churchpresenter.strings.generated.resources.helper_hint_ndi_name
import org.churchpresenter.strings.generated.resources.helper_hint_obs_connect
import org.churchpresenter.strings.generated.resources.helper_hint_obs_enable
import org.churchpresenter.strings.generated.resources.helper_hint_obs_host
import org.churchpresenter.strings.generated.resources.helper_hint_obs_password
import org.churchpresenter.strings.generated.resources.helper_hint_obs_scene
import org.churchpresenter.strings.generated.resources.helper_hint_output_picker_new
import org.churchpresenter.strings.generated.resources.helper_hint_pco_connect
import org.churchpresenter.strings.generated.resources.helper_hint_pco_import
import org.churchpresenter.strings.generated.resources.helper_hint_pco_plan
import org.churchpresenter.strings.generated.resources.helper_hint_pco_service_type
import org.churchpresenter.strings.generated.resources.helper_hint_shortcut_row
import org.churchpresenter.strings.generated.resources.helper_hint_song_field
import org.churchpresenter.strings.generated.resources.helper_hint_title_slide
import org.churchpresenter.strings.generated.resources.helper_hint_planning_center
import org.churchpresenter.strings.generated.resources.obs_connect
import org.churchpresenter.strings.generated.resources.planning_center_connect
import org.churchpresenter.strings.generated.resources.planning_center_import_button
import org.churchpresenter.strings.generated.resources.planning_center_import_title
import org.churchpresenter.strings.generated.resources.author
import org.churchpresenter.strings.generated.resources.song_capo
import org.churchpresenter.strings.generated.resources.ccli_number
import org.churchpresenter.strings.generated.resources.composer
import org.churchpresenter.strings.generated.resources.song_number
import org.churchpresenter.strings.generated.resources.song_book
import org.churchpresenter.strings.generated.resources.tune
import org.jetbrains.compose.resources.StringResource

/**
 * Walkthroughs of setting something up — an NDI or Browser Source output for streaming, OBS, an ATEM,
 * Planning Center, a field of the song editor, a keyboard shortcut — one ringed control at a time.
 * They only point: every value is the operator's to type.
 */
internal object SetupTopics {
    /** The tour for [normalized], or null when it is about none of these. */
    fun find(normalized: String): GuideTour? {
        val words = normalized.split(' ')
        // "Open ATEM settings" opens the page; a walkthrough is for setting one up.
        val settingUp = words.any { it in SETUP } && words.none { it in SETTINGS_WORDS }
        return when {
            normalized.containsPhrase("title slide") -> titleSlide()
            !settingUp -> null
            normalized.containsPhrase("browser source") -> browserSource()
            "ndi" in words -> ndi()
            "obs" in words -> obs()
            "atem" in words || "switcher" in words -> atem()
            else -> songField(normalized, words)
        }
    }

    /** NDI: add an output on the Projection page, name it, and pick what it shows. */
    fun ndi() = GuideTour(
        listOf(
            settingsStep(
                SettingsPage.PROJECTION,
                GuideTargets.NDI_ADD,
                helperText(Res.string.helper_hint_ndi_add, helperText(Res.string.add_ndi_output)),
            ),
            GuideStep(GuideTargets.NDI_FIRST_NAME, helperText(Res.string.helper_hint_ndi_name)),
            GuideStep(GuideTargets.outputProfilePicker("ndi", 0), helperText(Res.string.helper_hint_output_picker_new)),
        ),
    )

    /** A Browser Source, for OBS's own Browser source: add it, name it, pick what it shows. */
    fun browserSource() = GuideTour(
        listOf(
            settingsStep(
                SettingsPage.PROJECTION,
                GuideTargets.BROWSER_SOURCE_ADD,
                helperText(Res.string.helper_hint_browser_source_add, helperText(Res.string.add_browser_source_output)),
            ),
            GuideStep(GuideTargets.BROWSER_SOURCE_FIRST_NAME, helperText(Res.string.helper_hint_browser_source_name)),
            GuideStep(
                GuideTargets.outputProfilePicker("browser", 0),
                helperText(Res.string.helper_hint_output_picker_new),
            ),
        ),
    )

    /** OBS: switch it on, where it runs, its password, connect, then which scene each kind of content uses. */
    fun obs() = GuideTour(
        listOf(
            settingsStep(
                SettingsPage.INTEGRATIONS,
                GuideTargets.settingsRow("obs_enable"),
                helperText(Res.string.helper_hint_obs_enable),
            ),
            GuideStep(GuideTargets.settingsRow("obs_host"), helperText(Res.string.helper_hint_obs_host)),
            GuideStep(GuideTargets.settingsRow("obs_password"), helperText(Res.string.helper_hint_obs_password)),
            GuideStep(
                GuideTargets.OBS_CONNECT,
                helperText(Res.string.helper_hint_obs_connect, helperText(Res.string.obs_connect)),
            ),
            GuideStep(GuideTargets.OBS_DEFAULT_SCENE, helperText(Res.string.helper_hint_obs_scene)),
        ),
    )

    /** ATEM: its address, a test, then whether the lower third goes on a downstream key. */
    fun atem() = GuideTour(
        listOf(
            settingsStep(
                SettingsPage.ATEM,
                GuideTargets.settingsRow("atem_host"),
                helperText(Res.string.helper_hint_atem_host),
            ),
            GuideStep(
                GuideTargets.ATEM_TEST_CONNECTION,
                helperText(Res.string.helper_hint_atem_test, helperText(Res.string.atem_test_connection)),
            ),
            GuideStep(GuideTargets.ATEM_DSK_SWITCH, helperText(Res.string.helper_hint_atem_dsk)),
        ),
    )

    /** Planning Center: the import button on the schedule, then its window from connecting to importing. */
    fun planningCenter() = GuideTour(
        listOf(
            GuideStep(
                GuideTargets.PLANNING_CENTER_IMPORT,
                helperText(Res.string.helper_hint_planning_center, helperText(Res.string.planning_center_import_title)),
            ),
            GuideStep(
                GuideTargets.PCO_CONNECT,
                helperText(Res.string.helper_hint_pco_connect, helperText(Res.string.planning_center_connect)),
            ),
            GuideStep(GuideTargets.PCO_SERVICE_TYPE, helperText(Res.string.helper_hint_pco_service_type)),
            GuideStep(GuideTargets.PCO_PLAN, helperText(Res.string.helper_hint_pco_plan)),
            GuideStep(
                GuideTargets.PCO_IMPORT,
                helperText(Res.string.helper_hint_pco_import, helperText(Res.string.planning_center_import_button)),
            ),
        ),
    )

    /** A song's title slide is how a screen shows songs: its profile's Songs page. */
    fun titleSlide() = GuideTour(
        listOf(
            GuideStep(
                GuideTargets.settingsRow("profile_title_slide"),
                helperText(Res.string.helper_hint_title_slide),
                HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(page = "SONGS")),
            ),
        ),
    )

    /** [action]'s row in the Keyboard Shortcuts window, opened on it. */
    fun shortcutRow(action: ShortcutAction) = GuideTour(
        listOf(
            GuideStep(
                GuideTargets.shortcutRow(action.name),
                helperText(Res.string.helper_hint_shortcut_row, helperText(action.descriptionRes)),
                HelperAction.OpenShortcutRow(action),
            ),
        ),
    )

    /** "Where do I put the CCLI number": the Songs tab, Edit Song, then that field. */
    private fun songField(normalized: String, words: List<String>): GuideTour? {
        val (target, label) = SONG_FIELDS.firstOrNull { (keys, _) -> keys.any { normalized.containsPhrase(it) } }
            ?.second ?: return null
        if (words.none { it in SONG_WORDS } || words.any { it in NOT_A_FIELD || it.first().isDigit() }) return null
        return GuideTour(
            listOf(
                tabStep(Tabs.SONGS),
                GuideStep(
                    GuideTargets.EDIT_SONG,
                    helperText(Res.string.helper_hint_edit_song_field),
                    HelperAction.SelectTab(Tabs.SONGS),
                ),
                GuideStep(target, helperText(Res.string.helper_hint_song_field, helperText(label))),
            ),
        )
    }

    private fun settingsStep(page: SettingsPage, target: GuideTarget, hint: HelperText) =
        GuideStep(target, hint, HelperAction.OpenSettings(page))

    private val SETUP = setOf(
        "connect", "setup", "set", "configure", "link", "hook", "how", "where", "add", "put", "enter", "type", "fill",
        "stream", "streaming", "send",
    )
    private val SETTINGS_WORDS = setOf("settings", "options", "preferences")

    /** A song field's name in a request about something else: the CCLI report, the number's font, song 12. */
    private val NOT_A_FIELD =
        setOf("report", "reports", "font", "bigger", "smaller", "size", "color", "colour", "style")
    private val SONG_WORDS = setOf("song", "songs", "hymn", "lyrics", "author", "ccli", "composer", "songbook", "capo")

    private val SONG_FIELDS: List<Pair<List<String>, Pair<GuideTarget, StringResource>>> = listOf(
        listOf("ccli") to (GuideTargets.SONG_CCLI to Res.string.ccli_number),
        listOf("author", "writer", "wrote") to (GuideTargets.SONG_AUTHOR to Res.string.author),
        listOf("composer", "composed") to (GuideTargets.SONG_COMPOSER to Res.string.composer),
        listOf("songbook", "hymnal", "book") to (GuideTargets.SONG_SONGBOOK to Res.string.song_book),
        listOf("song number", "hymn number") to (GuideTargets.SONG_NUMBER to Res.string.song_number),
        listOf("tune") to (GuideTargets.SONG_TUNE to Res.string.tune),
        listOf("capo") to (GuideTargets.SONG_CAPO to Res.string.song_capo),
    )
}

/** A setup walkthrough, asked or told: "set up ndi", "connect obs", "where do I put the ccli number". */
internal fun setupTopicsRule(r: Request): Resolution? =
    SetupTopics.find(r.text)?.let { act(HelperAction.Highlight(it)) }
