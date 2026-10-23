package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * Which profile each output uses, saved under a name so the operator can put every output back the
 * way it was in one click -- a Sunday service, a youth night. [profiles] maps an output's key
 * (`Constants.previewOutputKey`, `screen:1`, `ndi:0`) to the [OutputProfile.id] it uses.
 *
 * One per install, on [ProjectionSettings], like the preview layouts: it arranges the operator's
 * outputs, not how any of them draws.
 */
@Serializable
data class ProfileSet(
    val id: String = "",
    val name: String = "",
    val profiles: Map<String, String> = emptyMap(),
)

/** Every output by its key, each kind in the order its list keeps: screens, Browser Source, NDI, OMT. */
private fun ProjectionSettings.outputsByKey(): Map<String, ScreenAssignment> =
    OUTPUT_KINDS.flatMap { kind ->
        kind.outputs(this).mapIndexed { i, output -> Constants.previewOutputKey(kind.key, i) to output }
    }.toMap()

/**
 * A new set holding the profile every output uses now, named [name], added at the end. Its id is one
 * no other set has.
 */
fun ProjectionSettings.withProfileSetSaved(name: String): ProjectionSettings {
    val taken = profileSets.map { it.id }.toSet()
    var n = profileSets.size + 1
    while ("set$n" in taken) n++
    val profiles = outputsByKey().mapNotNull { (key, output) -> output.activeProfileId?.let { key to it } }.toMap()
    val saved = ProfileSet(id = "set$n", name = name, profiles = profiles)
    return copy(profileSets = profileSets + saved)
}

/**
 * [this] with each output the set [id] names using the profile it names. An output that is gone, or
 * a profile that is, is passed over, and an output the set does not name keeps its own.
 */
fun ProjectionSettings.withProfileSetApplied(id: String): ProjectionSettings {
    val set = profileSets.firstOrNull { it.id == id } ?: return this
    val known = outputProfiles.map { it.id }.toSet()
    return OUTPUT_KINDS.fold(this) { proj, kind ->
        val updated = kind.outputs(proj).mapIndexed { i, output ->
            val picked = set.profiles[Constants.previewOutputKey(kind.key, i)]
            if (picked != null && picked in known) output.copy(activeProfileId = picked) else output
        }
        kind.withOutputs(proj, updated)
    }
}

/** Whether every output [set] names that is still here uses the profile it names now. */
fun ProjectionSettings.isProfileSetApplied(set: ProfileSet): Boolean {
    val outputs = outputsByKey()
    val known = outputProfiles.map { it.id }.toSet()
    val checked = set.profiles.filter { (key, profile) -> key in outputs && profile in known }
    return checked.isNotEmpty() && checked.all { (key, profile) -> outputs.getValue(key).activeProfileId == profile }
}

/**
 * Whether sets are worth offering -- the layout menu's switch for their button, and the button: two
 * outputs or more, of any kind, and two profiles or more to switch them between. Sets already saved
 * are kept while they are not offered.
 */
fun ProjectionSettings.offersProfileSets(): Boolean = outputsByKey().size >= 2 && outputProfiles.size >= 2

/** Whether the preview header shows the profile sets button: switched on, and sets worth offering. */
fun ProjectionSettings.showsProfileSetsButton(): Boolean = showProfileSetsButton && offersProfileSets()

/** [this] with the set [id] renamed [name]. */
fun ProjectionSettings.withProfileSetRenamed(id: String, name: String): ProjectionSettings =
    copy(profileSets = profileSets.map { if (it.id == id) it.copy(name = name) else it })

/** [this] without the set [id]. */
fun ProjectionSettings.withoutProfileSet(id: String): ProjectionSettings =
    copy(profileSets = profileSets.filterNot { it.id == id })

/** One kind of output a set covers: its key, and how its list is read and written. */
private class OutputKindList(
    val key: String,
    val outputs: (ProjectionSettings) -> List<ScreenAssignment>,
    val withOutputs: (ProjectionSettings, List<ScreenAssignment>) -> ProjectionSettings,
)

private val OUTPUT_KINDS = listOf(
    OutputKindList(Constants.PREVIEW_OUTPUT_SCREEN, { it.screenAssignments }) { p, o -> p.copy(screenAssignments = o) },
    OutputKindList(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, { it.browserSourceOutputs }) { p, o ->
        p.copy(browserSourceOutputs = o)
    },
    OutputKindList(Constants.PREVIEW_OUTPUT_NDI, { it.ndiOutputs }) { p, o -> p.copy(ndiOutputs = o) },
    OutputKindList(Constants.PREVIEW_OUTPUT_OMT, { it.omtOutputs }) { p, o -> p.copy(omtOutputs = o) },
)
