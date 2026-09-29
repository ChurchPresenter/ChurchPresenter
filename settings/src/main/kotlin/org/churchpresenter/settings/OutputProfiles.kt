package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants

/**
 * A profile with a fresh [id] that no profile in [existing] already uses.
 *
 * Its Bible and song backgrounds are its own and say `Default`, so they follow the profile's own
 * default background: a new screen is set up from its Background page, rather than quietly taking
 * whatever the app's Bible and song surfaces happen to be.
 */
fun newOutputProfile(existing: List<OutputProfile>, name: String = ""): OutputProfile {
    val taken = existing.map { it.id }.toSet()
    var n = existing.size + 1
    while ("profile$n" in taken) n++
    val followsDefault = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT)
    return OutputProfile(
        id = "profile$n",
        name = name,
        backgroundSettings = BackgroundSettings(
            bibleBackground = followsDefault,
            bibleLowerThirdBackground = followsDefault,
            songBackground = followsDefault,
            songLowerThirdBackground = followsDefault,
        ),
        backgroundOverrides = CONTENT_SURFACES.map { it.name }.toSet(),
    )
}

/** The surfaces a new profile carries its own of: the content bands, each following the profile's default. */
private val CONTENT_SURFACES = listOf(
    BackgroundSurface.BIBLE,
    BackgroundSurface.BIBLE_LOWER_THIRD,
    BackgroundSurface.SONG,
    BackgroundSurface.SONG_LOWER_THIRD,
)

/** [profile] added at the end. */
fun ProjectionSettings.addOutputProfile(profile: OutputProfile): ProjectionSettings =
    copy(outputProfiles = outputProfiles + profile)

/** The profile with [id] replaced by [transform] of it. No-op if [id] names no profile. */
fun ProjectionSettings.updateOutputProfile(
    id: String,
    transform: (OutputProfile) -> OutputProfile,
): ProjectionSettings = copy(outputProfiles = outputProfiles.map { if (it.id == id) transform(it) else it })

/** The profile with [id] renamed to [name]. */
fun ProjectionSettings.renameOutputProfile(id: String, name: String): ProjectionSettings =
    updateOutputProfile(id) { it.copy(name = name) }

/**
 * The profile with [id] removed, or `this` unchanged while any output still follows it.
 *
 * An output always follows exactly one profile now -- there is no per-output fallback state left
 * to resolve to -- so deleting one that is in use would leave that output pointed at nothing. The
 * caller is expected to check [outputProfileUsageCount] first and tell the operator which outputs
 * are using it; this refusal is the defensive backstop, not the primary UI.
 */
fun ProjectionSettings.deleteOutputProfile(id: String): ProjectionSettings =
    // A master with profiles still following it is refused too: they would be left pointing at
    // nothing. They are unlinked or deleted first.
    if (outputProfileUsageCount(id) > 0 || linkedTo(id).isNotEmpty()) this
    else copy(outputProfiles = outputProfiles.filterNot { it.id == id })

/** A copy of the profile at [id] under [newName] and a fresh id, or `this` unchanged if [id] names none. */
fun ProjectionSettings.duplicateOutputProfile(id: String, newName: String): ProjectionSettings {
    val source = outputProfiles.find { it.id == id } ?: return this
    val fresh = newOutputProfile(outputProfiles, newName)
    // A standalone copy with the values the source draws with: a duplicate of a linked profile does
    // not follow its master too.
    return addOutputProfile(source.copy(id = fresh.id, name = newName, parentId = null, overrides = emptySet()))
}

/** How many outputs, across all four output lists, currently follow the profile at [id]. */
fun ProjectionSettings.outputProfileUsageCount(id: String): Int =
    (screenAssignments + browserSourceOutputs + ndiOutputs + omtOutputs).count { it.activeProfileId == id }

/** The outputs, across all four lists, currently following the profile at [id], labeled for display. */
fun ProjectionSettings.outputProfileUsers(id: String, labelOf: (ScreenAssignment) -> String): List<String> =
    (screenAssignments + browserSourceOutputs + ndiOutputs + omtOutputs)
        .filter { it.activeProfileId == id }
        .map(labelOf)
