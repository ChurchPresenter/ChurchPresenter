package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSection
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.linkedTo
import org.churchpresenter.settings.masterFor
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
 * row with paths shows its master's value, dashed. Each row is marked against the master of the
 * section its paths are in ([masterFor]), which need not be [master]; a section that follows no
 * master is not marked at all.
 */
internal class ProfileLink(
    val profile: OutputProfile,
    /** The master [profile] follows -- its main master -- or null when it follows nothing. */
    val master: OutputProfile?,
    /** The master each section follows; a section left out follows none, its values its own. */
    val sectionMasters: Map<ProfileSection, OutputProfile>,
    /** The profiles following [profile], when it is a master. */
    val followers: List<OutputProfile>,
    /** Rows that take their master's value are hidden, leaving only the profile's own. */
    val onlyChanges: Boolean,
    /** Gives the values under these paths back to the master. */
    val onRevert: (Collection<String>) -> Unit,
) {
    val isLinked: Boolean get() = master != null

    /**
     * The master the values at [paths] follow: their section's, or the main master for values no
     * section holds. Null when they are the profile's own -- it follows nothing, or not there.
     */
    fun masterFor(paths: Collection<String>): OutputProfile? {
        if (master == null) return null
        val section = paths.firstNotNullOfOrNull { ProfileSection.of(it) } ?: return master
        return sectionMasters[section]
    }

    /** Whether the values at [paths] follow a master, and so are marked as followed or own. */
    fun follows(paths: Collection<String>): Boolean = paths.isNotEmpty() && masterFor(paths) != null

    /** The profile's own values at or under [paths] -- or above them, a whole object it has taken over. */
    fun ownPaths(paths: Collection<String>): List<String> =
        profile.overrides.filter { own -> paths.any { pathWithin(own, it) || pathWithin(it, own) } }

    /** Whether any value under [paths] is the profile's own. */
    fun owns(paths: Collection<String>): Boolean = paths.isNotEmpty() && ownPaths(paths).isNotEmpty()

    /** The master's value for a row editing [paths], in words, or null when it has none to show. */
    fun masterValue(paths: Collection<String>, on: String, off: String): String? {
        val m = masterFor(paths) ?: return null
        val path = ownPaths(paths).firstOrNull()?.takeIf { own -> paths.any { pathWithin(own, it) } }
            ?: paths.firstOrNull() ?: return null
        return m.valueAt(path)?.plainText(on, off)?.takeIf { it.isNotBlank() }
    }
}

/** [profile]'s link in [this]: the masters it follows and the profiles following it. */
internal fun ProjectionSettings.linkOf(
    profile: OutputProfile,
    onlyChanges: Boolean,
    onRevert: (Collection<String>) -> Unit,
): ProfileLink = ProfileLink(
    profile = profile,
    master = masterOf(profile),
    sectionMasters = ProfileSection.entries.mapNotNull { s -> masterFor(profile, s)?.let { s to it } }.toMap(),
    followers = linkedTo(profile.id),
    onlyChanges = onlyChanges,
    onRevert = onRevert,
)

/**
 * Whether [page] of [profile] follows a master at all -- General and Outputs never do. Only changes
 * hides what a page takes from its master, so it means nothing on a page that follows none.
 */
internal fun ProjectionSettings.followsOn(profile: OutputProfile, page: ProfilePage): Boolean =
    page.section()?.let { masterFor(profile, it) } != null

/** The link of the profile whose page is being drawn; null outside the Profiles tab. */
internal val LocalProfileLink = staticCompositionLocalOf<ProfileLink?> { null }

/**
 * The settings a page edits, as path prefixes: what its change count counts and what "Revert" on a
 * whole page would give back. General and Outputs carry the profile's identity, which is never
 * inherited, except for the display mode, which always is.
 */
internal fun ProfilePage.pathPrefixes(): List<String> = section()?.prefixes.orEmpty()

/** The section of a profile [this] page edits, or null for General and Outputs, which edit none. */
internal fun ProfilePage.section(): ProfileSection? = when (this) {
    ProfilePage.General, ProfilePage.Outputs -> null
    ProfilePage.Content -> ProfileSection.CONTENT
    is ProfilePage.Appearance -> pane.section()
}

/** The section of a profile [this] pane's page edits. */
internal fun CustomizePane.section(): ProfileSection = when (this) {
    CustomizePane.BIBLE -> ProfileSection.BIBLE
    CustomizePane.SONGS -> ProfileSection.SONGS
    CustomizePane.BACKGROUND -> ProfileSection.BACKGROUND
    CustomizePane.CAPTIONS -> ProfileSection.CAPTIONS
    CustomizePane.SUBTITLES -> ProfileSection.SUBTITLES
    CustomizePane.QA -> ProfileSection.QA
    CustomizePane.DICTIONARY -> ProfileSection.DICTIONARY
    CustomizePane.STAGE_MONITOR -> ProfileSection.STAGE
}

/** The page that edits [this] section. */
internal fun ProfileSection.page(): ProfilePage =
    if (this == ProfileSection.CONTENT) ProfilePage.Content
    else ProfilePage.Appearance(CustomizePane.entries.first { it.section() == this })
