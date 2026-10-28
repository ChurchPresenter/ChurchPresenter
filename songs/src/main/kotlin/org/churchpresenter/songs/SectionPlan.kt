package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.ChorusRepeat
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.sectionKeysOf
import org.churchpresenter.settings.operatorSongSettings

/**
 * What a song decides about its written sections: which it leaves out, by their index among the
 * written groups, and whether its chorus repeats after each verse.
 */
internal data class SectionPlan(
    val keys: List<String> = emptyList(),
    val skippedGroups: Set<Int> = emptySet(),
    val repeatChorus: Boolean = false,
)

/** [song]'s [SectionPlan], its skip keys matched against the groups of its own [primarySlides]. */
internal fun SongsViewModel.sectionPlanOf(song: SongItem, primarySlides: List<LyricSection>): SectionPlan {
    val keys = sectionKeysOf(slideGroupsOf(primarySlides).map { it.first().header })
    return SectionPlan(
        keys = keys,
        skippedGroups = keys.indices.filter { keys[it] in song.skippedSections }.toSet(),
        repeatChorus = when (song.chorusRepeat) {
            ChorusRepeat.ON -> true
            ChorusRepeat.OFF -> false
            ChorusRepeat.SETTINGS -> appSettings.operatorSongSettings().autoRepeatChorus
        },
    )
}

/**
 * [slides] with [plan] applied: each group keyed and the skipped ones marked, then the chorus
 * repeated if it says so.
 */
internal fun arrangeSections(slides: List<LyricSection>, plan: SectionPlan): List<LyricSection> {
    val groups = slideGroupsOf(slides).mapIndexed { index, group ->
        val key = plan.keys.getOrNull(index).orEmpty()
        group.map { it.copy(sectionKey = key, skipped = index in plan.skippedGroups) }
    }
    return if (plan.repeatChorus) repeatChorusAfterVerses(groups) else groups.flatten()
}

/**
 * The [skipped] keys that still name a section of [lyrics]: the editor does not show the skip
 * list, so a verse renamed or deleted there should not leave its key behind in the file.
 */
internal fun skipKeysStillWritten(
    skipped: List<String>,
    lyrics: List<String>,
    title: String,
    number: String,
): List<String> {
    if (skipped.isEmpty()) return skipped
    val keys = sectionKeysOf(slideGroupsOf(lyricSlidesOf(lyrics, title, number)).map { it.first().header })
    return skipped.filter { it in keys }
}
