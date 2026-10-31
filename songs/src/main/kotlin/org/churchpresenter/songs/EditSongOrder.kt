package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.sectionKeysOf
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf
import org.churchpresenter.sharedui.utils.sectionLabelOf
import org.churchpresenter.songchords.SecondVoice

/** A section as it is written in the editor's lyrics box: its order key, its name and its words. */
internal data class WrittenSection(
    val key: String,
    val label: String,
    val kind: SongSectionKind,
    val isVerse: Boolean,
    val lines: List<String>,
) {
    /** Short enough for a chip: the name's first letter and its number — `V1`, `C`, `B`. */
    val abbreviation: String
        get() = label.firstOrNull()?.uppercase().orEmpty() + label.filter { it.isDigit() }
}

/** The sections of [text], with what the editor needs to order them, worked out as the outputs do. */
internal data class EditSongSections(
    val written: List<WrittenSection>,
    /** The keys a song with no order of its own plays, chorus repeats included. */
    val defaultOrder: List<String>,
) {
    private val byKey = written.associateBy { it.key }
    fun of(key: String): WrittenSection? = byKey[key]
}

/** [text]'s sections, and the order it plays in by default when Settings [repeatChorus]. */
internal fun editSongSectionsOf(text: String, repeatChorus: Boolean): EditSongSections {
    val slides = lyricSlidesOf(text.split("\n"), title = "", number = "")
    val groups = slideGroupsOf(slides)
    val keys = sectionKeysOf(groups.map { it.first().header })
    val written = groups.mapIndexed { index, group ->
        val header = group.first().header
        val label = header?.let(::sectionLabelOf).orEmpty()
        WrittenSection(
            key = keys[index],
            label = label,
            kind = sectionKindOf(label),
            isVerse = singsTheChorusAfterwards(group),
            lines = group.flatMap { slide -> slide.lines.map(SecondVoice::plain) },
        )
    }
    val played = arrangeSections(slides, SectionPlan(keys = keys, repeatChorus = repeatChorus))
    return EditSongSections(written, slideGroupsOf(played).map { it.first().sectionKey })
}

/** A ready-made order; an empty [keys] is the song's default. */
internal data class OrderPreset(val kind: OrderPresetKind, val keys: List<String>)

internal enum class OrderPresetKind {
    DEFAULT,
    CHORUS_AFTER_EACH_VERSE,
    START_WITH_CHORUS,
    END_WITH_DOUBLE_CHORUS,
    BRIDGE_BEFORE_LAST_CHORUS,
}

/** The presets [sections] can be put in: those that need a chorus or a bridge only when it has one. */
internal fun orderPresetsOf(sections: EditSongSections): List<OrderPreset> = buildList {
    add(OrderPreset(OrderPresetKind.DEFAULT, emptyList()))
    val verses = sections.written.filter { it.isVerse }.map { it.key }
    val chorus = sections.written.firstOrNull { it.kind == SongSectionKind.CHORUS }?.key ?: return@buildList
    if (verses.isEmpty()) return@buildList
    val eachVerse = verses.flatMap { listOf(it, chorus) }
    add(OrderPreset(OrderPresetKind.CHORUS_AFTER_EACH_VERSE, eachVerse))
    add(OrderPreset(OrderPresetKind.START_WITH_CHORUS, listOf(chorus) + eachVerse))
    add(OrderPreset(OrderPresetKind.END_WITH_DOUBLE_CHORUS, eachVerse + chorus))
    val bridge = sections.written.firstOrNull { it.kind == SongSectionKind.BRIDGE }?.key ?: return@buildList
    add(OrderPreset(OrderPresetKind.BRIDGE_BEFORE_LAST_CHORUS, eachVerse + bridge + chorus))
}
