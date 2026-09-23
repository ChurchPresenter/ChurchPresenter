package org.churchpresenter.settings

/** A profile with a fresh [id] that no profile in [existing] already uses. */
fun newOutputProfile(existing: List<OutputProfile>, name: String = ""): OutputProfile {
    val taken = existing.map { it.id }.toSet()
    var n = existing.size + 1
    while ("profile$n" in taken) n++
    return OutputProfile(id = "profile$n", name = name)
}

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
    if (outputProfileUsageCount(id) > 0) this else copy(outputProfiles = outputProfiles.filterNot { it.id == id })

/** A copy of the profile at [id] under [newName] and a fresh id, or `this` unchanged if [id] names none. */
fun ProjectionSettings.duplicateOutputProfile(id: String, newName: String): ProjectionSettings {
    val source = outputProfiles.find { it.id == id } ?: return this
    val fresh = newOutputProfile(outputProfiles, newName)
    return addOutputProfile(source.copy(id = fresh.id, name = newName))
}

/** How many outputs, across all three output lists, currently follow the profile at [id]. */
fun ProjectionSettings.outputProfileUsageCount(id: String): Int =
    (screenAssignments + browserSourceOutputs + ndiOutputs).count { it.activeProfileId == id }

/** The outputs, across all three lists, currently following the profile at [id], labeled for display. */
fun ProjectionSettings.outputProfileUsers(id: String, labelOf: (ScreenAssignment) -> String): List<String> =
    (screenAssignments + browserSourceOutputs + ndiOutputs).filter { it.activeProfileId == id }.map(labelOf)
