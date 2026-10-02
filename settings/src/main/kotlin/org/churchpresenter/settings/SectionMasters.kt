package org.churchpresenter.settings

/*
 * A linked profile following other masters than its main one, section by section -- see
 * [ProfileSection] and [OutputProfile.sectionMasters].
 */

/** Whether [this] follows [masterId] in some section, without it being [this]'s main master. */
fun OutputProfile.followsInSection(masterId: String): Boolean =
    parentId != null && parentId != masterId && masterId in sectionMasters.values

/** The sections in which [this] follows [masterId], which is not its main master, in section order. */
fun OutputProfile.sectionsFollowing(masterId: String): List<ProfileSection> =
    ProfileSection.entries.filter { sectionMasters[it.id] == masterId }

/**
 * The master [profile]'s [section] follows: the one [OutputProfile.sectionMasters] names, else its
 * main master. Null when the section is its own, or the master is gone or itself linked.
 */
fun ProjectionSettings.masterFor(profile: OutputProfile, section: ProfileSection): OutputProfile? {
    val main = profile.parentId ?: return null
    val id = profile.sectionMasters[section.id] ?: main
    if (id == OWN_SECTION || id == profile.id) return null
    return outputProfiles.find { it.id == id && it.parentId == null }
}

/**
 * [this]'s section masters as they can be followed: an unknown section is dropped, a section naming
 * its main master is no exception and is dropped too, and one naming a master that is gone, itself
 * linked or this profile follows nothing any more -- its values kept, as a whole profile's are.
 */
internal fun OutputProfile.checkedSectionMasters(byId: Map<String, OutputProfile>): Map<String, String> =
    sectionMasters
        .filter { (section, id) -> ProfileSection.byId(section) != null && id != parentId }
        .mapValues { (_, id) -> if (canMaster(byId[id])) id else OWN_SECTION }

/** Whether [master] can be followed by [this]: it exists, follows nothing, and is not [this]. */
private fun OutputProfile.canMaster(master: OutputProfile?): Boolean =
    master != null && master.parentId == null && master.id != id

/**
 * How many of [this]'s settings differ from [masterId]'s: all its changes when [masterId] is its main
 * master, and only those in the sections following [masterId] otherwise.
 */
fun OutputProfile.changesFrom(masterId: String): Int =
    if (!followsInSection(masterId)) overrides.size
    else overrideCount(this, sectionsFollowing(masterId).flatMap { it.prefixes })

/**
 * [this] with the linked profile [id]'s [section] following [masterId]: null for its main master,
 * [OWN_SECTION] for none, or another profile that follows nothing.
 *
 * A section given a master takes that master's values entirely -- its own values there are dropped,
 * as linking with "match" does -- while a section made its own keeps the values it has. Refused --
 * `this` unchanged -- for a profile that follows nothing, and for a master that is itself linked or
 * is [id]: one level only.
 */
fun ProjectionSettings.setSectionMaster(
    id: String,
    section: ProfileSection,
    masterId: String?,
): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    val main = profile.parentId ?: return this
    val target = masterId?.takeIf { it != main }
    if (target != null && target != OWN_SECTION) {
        val master = outputProfiles.find { it.id == target } ?: return this
        if (master.id == id || master.parentId != null) return this
    }
    val masters = if (target == null) {
        profile.sectionMasters - section.id
    } else {
        profile.sectionMasters + (section.id to target)
    }
    val overrides = if (target == OWN_SECTION) {
        profile.overrides
    } else {
        profile.overrides.filterNot(section::holds).toSet()
    }
    val updated = profile.copy(sectionMasters = masters, overrides = overrides)
    return copy(outputProfiles = outputProfiles.map { if (it.id == id) updated else it }).withLinksResolved()
}
