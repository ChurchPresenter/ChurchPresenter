package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.linkedTo
import org.churchpresenter.settings.masterOf
import org.churchpresenter.settings.pathWithin
import org.churchpresenter.settings.plainText
import org.churchpresenter.settings.valueAt

/**
 * How the profile being edited is linked, for the rows of its pages to mark where each value comes
 * from without every row being handed the profile list.
 *
 * A row names the stored settings it edits as paths ([SettingsRow]'s `paths`); on a linked profile
 * one of them being among [OutputProfile.overrides] makes the row the profile's own, and any other
 * row with paths shows its master's value, dashed.
 */
internal class ProfileLink(
    val profile: OutputProfile,
    /** The master [profile] follows, or null when it follows nothing. */
    val master: OutputProfile?,
    /** The profiles following [profile], when it is a master. */
    val followers: List<OutputProfile>,
    /** Rows that take their master's value are hidden, leaving only the profile's own. */
    val onlyChanges: Boolean,
    /** Gives the values under these paths back to the master. */
    val onRevert: (Collection<String>) -> Unit,
) {
    val isLinked: Boolean get() = master != null

    /** The profile's own values at or under [paths] -- or above them, a whole object it has taken over. */
    fun ownPaths(paths: Collection<String>): List<String> =
        profile.overrides.filter { own -> paths.any { pathWithin(own, it) || pathWithin(it, own) } }

    /** Whether any value under [paths] is the profile's own. */
    fun owns(paths: Collection<String>): Boolean = paths.isNotEmpty() && ownPaths(paths).isNotEmpty()

    /** The master's value for a row editing [paths], in words, or null when it has none to show. */
    fun masterValue(paths: Collection<String>, on: String, off: String): String? {
        val m = master ?: return null
        val path = ownPaths(paths).firstOrNull()?.takeIf { own -> paths.any { pathWithin(own, it) } }
            ?: paths.firstOrNull() ?: return null
        return m.valueAt(path)?.plainText(on, off)?.takeIf { it.isNotBlank() }
    }
}

/** [profile]'s link in [this]: the master it follows and the profiles following it. */
internal fun ProjectionSettings.linkOf(
    profile: OutputProfile,
    onlyChanges: Boolean,
    onRevert: (Collection<String>) -> Unit,
): ProfileLink = ProfileLink(profile, masterOf(profile), linkedTo(profile.id), onlyChanges, onRevert)

/** The link of the profile whose page is being drawn; null outside the Profiles tab. */
internal val LocalProfileLink = staticCompositionLocalOf<ProfileLink?> { null }

/**
 * The settings a page edits, as path prefixes: what its change count counts and what "Revert" on a
 * whole page would give back. General and Outputs carry the profile's identity, which is never
 * inherited, except for the display mode, which always is.
 */
internal fun ProfilePage.pathPrefixes(): List<String> = when (this) {
    ProfilePage.General, ProfilePage.Outputs -> emptyList()
    ProfilePage.Content -> CONTENT_PATHS
    is ProfilePage.Appearance -> when (pane) {
        CustomizePane.BIBLE -> listOf("bibleSettings")
        CustomizePane.SONGS -> listOf("songSettings")
        CustomizePane.BACKGROUND -> listOf("backgroundSettings", "backgroundOverrides")
        CustomizePane.CAPTIONS -> listOf("sttSettings")
        CustomizePane.SUBTITLES -> listOf("mediaSettings")
        CustomizePane.QA -> listOf("qaSettings")
        CustomizePane.DICTIONARY -> listOf("dictionarySettings")
        CustomizePane.STAGE_MONITOR -> listOf("stageMonitorSettings")
    }
}

/** Everything the Content & sources page decides: what is shown, from where, and how it fits. */
private val CONTENT_PATHS = listOf(
    "bibleMode", "bibleTranslations", "songMode", "songTranslations", "songLookAhead", "showChords",
    "showPictures", "showMedia", "showSubtitles", "showStreaming", "showAnnouncements", "showWebsite",
    "showQA", "showSTT", "showDictionary", "showCanvas", "showFullscreenBackground",
    "showLowerThirdBackground", "showBibleBackground", "showSongsBackground", "pictureScaleMode",
    "mediaScaleMode", "lowerThirdPlacements",
)
