package org.churchpresenter.settings

/*
 * Linked profiles: a master, and profiles that follow it except where they say otherwise.
 *
 * **Every profile stores every value in full**, linked or not. A linked profile's [OutputProfile.overrides]
 * names which of its values are its own; all the rest are its master's, and are written into it again
 * ([materialize]) every time the master changes and every time the document is loaded. That is what
 * keeps the rest of the app out of it: an output draws with its profile's values exactly as before,
 * and resolution never walks a chain of masters.
 *
 * A value is addressed by its path into the profile's serialized settings -- `bibleSettings.marginTop`,
 * `songSettings.lyricsFontSize`. A list of Bible translations is addressed entry by entry, by file
 * name, `bibleSettings.translations[kjv.spb].textFontSize`, since one translation can be given a
 * look of its own; any other list is one value.
 */

/**
 * The profiles following [id], in list order: those it is the main master of, and those following
 * it in one section or more ([OutputProfile.sectionMasters]).
 */
fun ProjectionSettings.linkedTo(id: String): List<OutputProfile> =
    outputProfiles.filter { it.parentId == id || it.followsInSection(id) }

/** The master [profile] follows -- its main master -- if it follows one that exists. */
fun ProjectionSettings.masterOf(profile: OutputProfile): OutputProfile? =
    profile.parentId?.let { id -> outputProfiles.find { it.id == id && it.parentId == null } }

/**
 * Every linked profile brought back in line with its master, and the list put in blocks -- each
 * master followed by the profiles linked to it.
 *
 * A profile whose master is gone, or is itself linked, follows nothing any more: one level only,
 * and nothing is left pointing at a profile that cannot be drawn from. Its values are kept.
 */
fun ProjectionSettings.withLinksResolved(): ProjectionSettings {
    val byId = outputProfiles.associateBy { it.id }
    val fixed = outputProfiles.map { profile ->
        val master = profile.parentId?.let(byId::get)
        when {
            profile.parentId == null ->
                if (profile.sectionMasters.isEmpty()) profile else profile.copy(sectionMasters = emptyMap())
            master == null || master.parentId != null || master.id == profile.id ->
                profile.copy(parentId = null, overrides = emptySet(), sectionMasters = emptyMap())
            else -> {
                val linked = profile.copy(sectionMasters = profile.checkedSectionMasters(byId))
                materialize(master, linked) { section ->
                    when (val id = linked.sectionMasters[section.id]) {
                        null -> master
                        OWN_SECTION -> null
                        else -> byId.getValue(id)
                    }
                }
            }
        }
    }
    return copy(outputProfiles = fixed.inBlocks())
}

/** [this] in blocks: each profile that follows nothing, then the profiles linked to it, in order. */
fun List<OutputProfile>.inBlocks(): List<OutputProfile> {
    val tops = filter { it.parentId == null }
    val children = filter { it.parentId != null }.groupBy { it.parentId }
    val placed = tops.flatMap { top -> listOf(top) + children[top.id].orEmpty() }
    // A child whose master is not a top-level profile has already had its link cleared by
    // [withLinksResolved]; anything left over keeps its place at the end rather than vanishing.
    return placed + (this - placed.toSet())
}

/**
 * [this] with the profile [id] edited by [transform], the way an edit on the Profiles tab applies.
 *
 * On a linked profile every value the edit changed becomes its own (joins [OutputProfile.overrides]),
 * and its display mode stays its master's. A change in a section that follows no master is only a
 * value -- resolution drops it from the overrides again (see [materialize]). On a master, every
 * profile linked to it is brought back in line, so the change reaches each of them except where one
 * has a value of its own.
 */
fun ProjectionSettings.editProfile(id: String, transform: (OutputProfile) -> OutputProfile): ProjectionSettings {
    val old = outputProfiles.find { it.id == id } ?: return this
    val master = masterOf(old)
    val edited = transform(old).let { next ->
        if (master == null) {
            next
        } else {
            val locked = next.copy(displayMode = master.displayMode)
            locked.copy(overrides = locked.overrides + changedPaths(old, locked) - DISPLAY_MODE_PATH)
        }
    }
    return copy(outputProfiles = outputProfiles.map { if (it.id == id) edited else it }).withLinksResolved()
}

/**
 * [this] with the linked profile [id] taking its master's value again at every path under [prefixes]
 * -- each from the master of the section it is in.
 */
fun ProjectionSettings.revertToMaster(id: String, prefixes: Collection<String>): ProjectionSettings =
    copy(
        outputProfiles = outputProfiles.map { p ->
            if (p.id != id) {
                p
            } else {
                p.copy(overrides = p.overrides.filterNot { own -> prefixes.any { pathWithin(own, it) } }.toSet())
            }
        },
    ).withLinksResolved()

/**
 * [this] with [id] following nothing, in any section, its values exactly as they were -- so what its
 * outputs draw does not change -- placed directly after its old master's block.
 */
fun ProjectionSettings.unlinkProfile(id: String): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    val masterId = profile.parentId ?: return this
    val freed = profile.copy(parentId = null, overrides = emptySet(), sectionMasters = emptyMap())
    val rest = outputProfiles.filterNot { it.id == id }
    val lastOfBlock = rest.indexOfLast { it.id == masterId || it.parentId == masterId }
    val list = rest.toMutableList().apply { add(lastOfBlock + 1, freed) }
    return copy(outputProfiles = list).withLinksResolved()
}

/**
 * [this] with [id] following [masterId]: keeping its own value wherever it differs from the master
 * when [keepOwnValues], or taking every one of the master's when not. The display mode is always
 * the master's. Refused -- `this` unchanged -- for a profile that has profiles linked to it, and
 * for a master that is itself linked: one level only.
 */
fun ProjectionSettings.linkProfile(id: String, masterId: String, keepOwnValues: Boolean): ProjectionSettings {
    val profile = outputProfiles.find { it.id == id } ?: return this
    val master = outputProfiles.find { it.id == masterId } ?: return this
    if (id == masterId || master.parentId != null || linkedTo(id).isNotEmpty()) return this
    val own = if (keepOwnValues) changedPaths(master, profile) - DISPLAY_MODE_PATH else emptySet()
    val linked = profile.copy(parentId = masterId, overrides = own)
    return copy(outputProfiles = outputProfiles.map { if (it.id == id) linked else it }).withLinksResolved()
}

/**
 * [this] with [fresh] added at the end of [masterId]'s block, following it: identical to it until
 * changed, previewed at its shape. Unchanged when [masterId] is not a profile that follows nothing.
 */
fun ProjectionSettings.createLinkedProfile(masterId: String, fresh: OutputProfile): ProjectionSettings {
    val master = outputProfiles.find { it.id == masterId } ?: return this
    if (master.parentId != null) return this
    val linked = fresh.copy(
        parentId = masterId,
        previewWidth = master.previewWidth,
        previewHeight = master.previewHeight,
    )
    return copy(outputProfiles = outputProfiles + linked).withLinksResolved()
}
