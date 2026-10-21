package org.churchpresenter.profiles

import kotlinx.serialization.json.JsonElement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.pathWithin
import org.churchpresenter.settings.valueAt

/** The pages a setting can be edited on, in the order they are searched. */
private val EDITING_PAGES: List<ProfilePage> =
    listOf(ProfilePage.Content) + CustomizePane.entries.map { ProfilePage.Appearance(it) }

/** The page a setting is edited on, or null for one no page shows. */
internal fun pageForPath(path: String): ProfilePage? =
    EDITING_PAGES.firstOrNull { page -> page.pathPrefixes().any { pathWithin(path, it) } }

/** One line of the changes card: a changed setting, its page, and what it is changed from. */
internal class ChangeLine(
    val path: String,
    val page: ProfilePage?,
    /** The master's name -- the section's own master, or [master] for a value no section holds. */
    val sourceName: String,
    val sourceValue: JsonElement?,
)

/** The profile's changes in path order, each against the master it is changed from. */
internal fun ProfileLink.changeLines(master: OutputProfile): List<ChangeLine> =
    profile.overrides.sorted().map { path ->
        val from = masterFor(listOf(path)) ?: master
        ChangeLine(path, pageForPath(path), from.displayName(), from.valueAt(path))
    }
