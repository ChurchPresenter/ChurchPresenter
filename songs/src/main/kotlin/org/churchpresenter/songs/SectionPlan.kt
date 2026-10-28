package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.sectionKeysOf
import org.churchpresenter.settings.operatorSongSettings

/**
 * What a song decides about its written sections: the order they are sung in, as indexes among the
 * written groups (empty for as written), and whether its chorus repeats after each verse when it
 * has no order of its own.
 */
internal data class SectionPlan(
    val keys: List<String> = emptyList(),
    val order: List<Int> = emptyList(),
    val repeatChorus: Boolean = false,
)

/** [song]'s [SectionPlan], its order matched against the groups of its own [primarySlides]. */
internal fun SongsViewModel.sectionPlanOf(song: SongItem, primarySlides: List<LyricSection>): SectionPlan {
    val keys = sectionKeysOf(slideGroupsOf(primarySlides).map { it.first().header })
    return SectionPlan(
        keys = keys,
        order = song.sectionOrder.map { keys.indexOf(it) }.filter { it >= 0 },
        repeatChorus = appSettings.operatorSongSettings().autoRepeatChorus,
    )
}

/**
 * [slides] with [plan] applied. With an order, its sections in that order, then every written
 * section it leaves out, marked skipped so it can still be clicked but is never stepped onto.
 * Without one, as written, with the chorus repeated if Settings say so.
 */
internal fun arrangeSections(slides: List<LyricSection>, plan: SectionPlan): List<LyricSection> {
    val groups = slideGroupsOf(slides).mapIndexed { index, group ->
        val key = plan.keys.getOrNull(index).orEmpty()
        group.map { it.copy(sectionKey = key) }
    }
    if (plan.order.isEmpty()) return if (plan.repeatChorus) repeatChorusAfterVerses(groups) else groups.flatten()
    val sung = plan.order.mapNotNull { groups.getOrNull(it) }
    if (sung.isEmpty()) return groups.flatten()
    val left = groups.indices.filter { it !in plan.order }
        .map { index -> groups[index].map { it.copy(skipped = true) } }
    return (sung + left).flatten()
}

/**
 * The [order] keys that still name a section of [lyrics]: a verse renamed or deleted in the
 * editor should not leave its key behind in the file.
 */
internal fun orderKeysStillWritten(
    order: List<String>,
    lyrics: List<String>,
    title: String,
    number: String,
): List<String> {
    if (order.isEmpty()) return order
    val keys = writtenSectionKeys(lyrics, title, number)
    return order.filter { it in keys }
}

/** The keys of [lyrics]' written sections, in the order they are written. */
internal fun writtenSectionKeys(lyrics: List<String>, title: String, number: String): List<String> =
    sectionKeysOf(slideGroupsOf(lyricSlidesOf(lyrics, title, number)).map { it.first().header })
