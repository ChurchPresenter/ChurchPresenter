package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SONG_LOWER_THIRD_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.songBackgroundFrom
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.operatorSongSettings
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.sharedui.utils.isChorusHeader
import org.churchpresenter.sharedui.utils.isHeaderLine
import org.churchpresenter.sharedui.utils.isSlideBreak
import org.churchpresenter.sharedui.utils.songBackgroundDirectiveOf
import org.churchpresenter.sharedui.utils.isVerseHeader

fun SongsViewModel.getLyricSections(): List<LyricSection> {
    val items = filteredSongItemsState.value
    val idx = selectedSongIndexState.value
    if (items.isEmpty() || idx < 0 || idx >= items.size) return emptyList()
    return getLyricSections(items[idx])
}

/** Pure variant of [getLyricSections] for an arbitrary [song] — doesn't read any selection
 *  state, so it can be used with freshly-edited content that hasn't round-tripped through
 *  the (async) catalog reload yet, e.g. right after [updateSong] returns. */
fun SongsViewModel.getLyricSections(song: SongItem): List<LyricSection> {
    // Split primary lyrics into sections
    val primarySections = splitLyricsIntoSections(song.lyrics, song.title, song.number)
    // Split each further language into sections (matched by order), keeping a language that has
    // no lyrics as an empty group rather than dropping it — position is what identifies a
    // language to an output, so a gap has to stay a gap.
    val extras = song.extraTranslations()
    val extraGroups = extras.map { translation ->
        if (translation.lyrics.isEmpty()) emptyList()
        else slideGroupsOf(splitLyricsIntoSections(translation.lyrics, translation.title, song.number))
    }

    // Merge the languages by section first and by slide within it, rather than by a single
    // running index. They are the same thing until one language uses a manual slide break
    // another does not — and then a flat index would slide every later section under the wrong
    // translation. Pairing per section keeps the damage inside the section that disagrees: its
    // extra slides come out untranslated, and verse 3 still meets verse 3.
    val sections = slideGroupsOf(primarySections).flatMapIndexed { group, slides ->
        slides.mapIndexed { slide, section ->
            section.copy(
                translations = extras.mapIndexed { language, translation ->
                    SectionTranslation(
                        title = translation.title,
                        lines = extraGroups[language].getOrNull(group)?.getOrNull(slide)?.lines
                            ?: emptyList(),
                    )
                },
            )
        }
    }

    // Mark the very last section so the presenter can show end-of-song indicator, and stamp the
    // song's own background onto every section — the presenter only ever sees a section.
    return sections.mapIndexed { index, section ->
        (if (index == sections.lastIndex) section.copy(isLastSection = true) else section)
            .withBackgroundsOf(song)
    }
}

internal fun SongsViewModel.splitLyricsIntoSections(
    lyrics: List<String>,
    title: String,
    number: String,
): List<LyricSection> {
    // First pass: parse raw sections
    val rawSections = mutableListOf<LyricSection>()
    val currentLines = mutableListOf<String>()
    val currentChordLines = mutableListOf<String>()
    var currentHeader: String? = null
    var sectionType = Constants.SECTION_TYPE_VERSE

    // Which slide of the current section is being filled. A manual break ends a slide without
    // ending the section, so this counts up while the header and type stay put.
    var slideOfSection = 0

    // The background this section writes for itself, gathered from its `[background: …]`
    // directives. Cleared at each header, so a section that writes none inherits the song's;
    // applied from where it is written onward, so a directive after a slide break can even give
    // one slide of a section a background of its own.
    val backgroundFields = mutableMapOf<String, String>()

    // A header with no body under it is still a section — navigation steps over it and the
    // editor shows it — but only once. Past the first slide the header has already been
    // presented, so a trailing or doubled break must not add an empty slide behind it.
    fun flushSection() {
        if (currentLines.isNotEmpty() || (currentHeader != null && slideOfSection == 0)) {
            rawSections.add(
                LyricSection(
                    header = currentHeader,
                    title = title,
                    songNumber = number.toIntOrNull() ?: 0,
                    lines = currentLines.toList(),
                    type = sectionType,
                    slideIndex = slideOfSection++,
                    background = songBackgroundFrom(backgroundFields, SONG_BACKGROUND_PREFIX),
                    lowerThirdBackground =
                        songBackgroundFrom(backgroundFields, SONG_LOWER_THIRD_BACKGROUND_PREFIX),
                    // Only when the section actually carries chords — otherwise the stage
                    // monitor's chord zone would just repeat the lyrics zone.
                    chordLines = if (currentChordLines.any { ChordTransposer.hasChords(it) }) {
                        currentChordLines.toList()
                    } else {
                        emptyList()
                    },
                )
            )
            currentLines.clear()
            currentChordLines.clear()
        }
    }

    lyrics.forEach { line ->
        val directive = songBackgroundDirectiveOf(line)
        if (isHeaderLine(line)) {
            flushSection()
            slideOfSection = 0
            backgroundFields.clear()
            currentHeader = line
            sectionType = if (isChorusHeader(line)) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
        } else if (directive != null) {
            backgroundFields[directive.first] = directive.second
        } else if (isSlideBreak(line)) {
            // Ends the slide, not the section: the header and type carry on, so both halves of
            // a chorus still read "Chorus". Nothing is emitted for a break with no words behind
            // it, so a doubled or leading marker costs a blank slide rather than producing one.
            if (currentLines.isNotEmpty() || currentChordLines.isNotEmpty()) flushSection()
        } else if (line.isNotBlank()) {
            // Kept as written, for the band's chart only.
            currentChordLines.add(line)
            // The one place lyrics become presentable text, and so the one place chords have to
            // come off: everything downstream — presenter, stage monitor, companion server —
            // reads LyricSection.lines, and none of them should ever see a [G].
            val stripped = ChordTransposer.stripChords(line)
            // A line that was nothing but chords — an intro, a turnaround — has no words to
            // show, so it contributes no slide. A section left with none at all is folded into
            // the one it leads into; see [foldChordOnlySections].
            if (stripped.isNotBlank()) currentLines.add(stripped)
        }
    }

    flushSection()

    return repeatChorusAfterVerses(numberSlides(foldChordOnlySections(rawSections)))
}

/**
 * Groups consecutive sections that are slides of one authored section — see
 * [LyricSection.slideIndex]. An unsplit section is a group of one, which is what every song
 * without a manual break is made of.
 *
 * A group runs while the header and type hold and the slide index keeps climbing. Any of the
 * three breaking means a new section started: the index restarting is the ordinary case, and the
 * header changing catches the one where [foldChordOnlySections] has already eaten a section's
 * first slide, so its surviving slides no longer start at zero.
 */
internal fun SongsViewModel.slideGroupsOf(sections: List<LyricSection>): List<List<LyricSection>> {
    val groups = mutableListOf<MutableList<LyricSection>>()
    sections.forEach { section ->
        val open = groups.lastOrNull()?.last()
        val continues = open != null &&
            open.header == section.header &&
            open.type == section.type &&
            open.slideIndex < section.slideIndex
        if (continues) groups.last().add(section) else groups.add(mutableListOf(section))
    }
    return groups
}

/**
 * Stamps each section with its position among the slides of its own section.
 *
 * Numbered after [foldChordOnlySections] rather than during the parse, because folding can
 * remove a slide — a break with nothing but chords behind it — and the operator should be told
 * "2 of 2", counting what will actually go on screen, not what was typed.
 */
internal fun SongsViewModel.numberSlides(sections: List<LyricSection>): List<LyricSection> =
    slideGroupsOf(sections).flatMap { group ->
        group.mapIndexed { index, section -> section.copy(slideIndex = index, slideCount = group.size) }
    }

/**
 * Repeats the chorus after each verse that is not already followed by one, leaving every
 * section that was written exactly where it was written.
 *
 * A hymnal writes the chorus once and expects it sung after every verse, which is what
 * `SongSettings.autoRepeatChorus` turns on and why it defaults on. With it off the sections are
 * presented as authored -- the only way to express a chorus placed before verse 1, or after
 * verse 2 only, or written out in full at each repeat.
 *
 * What this must never do, in either mode, is lose words. The pass this replaced dropped every
 * authored chorus and re-inserted `firstOrNull { chorus }` behind each verse, so a song with a
 * second, different chorus presented the first one twice and the second one never (#403). Here
 * the repeat after a verse is the nearest chorus written at or before it -- the one that verse
 * is sung with -- falling back to the first chorus that follows, for a song whose chorus is
 * written after all its verses.
 *
 * Two guards survive from that pass, and one is added:
 *  - a song with **no chorus** is returned untouched;
 *  - a song with **no verse** is returned untouched, so a chorus-only song -- a short refrain,
 *    a choruses-only songbook -- still puts something on screen rather than coming out empty;
 *  - a `[Bridge]`, `[Intro]` or `[Tag]` no longer collects a chorus of its own. Only `{}` marks
 *    a chorus, so those parse as verses; [isVerseHeader] is what separates them.
 *
 * [foldChordOnlySections] runs first, so a chord-only intro has already been folded into the
 * section it leads into and cannot trigger a repeat of its own.
 */
internal fun SongsViewModel.repeatChorusAfterVerses(sections: List<LyricSection>): List<LyricSection> {
    if (!appSettings.operatorSongSettings().autoRepeatChorus) return sections
    // Whole sections repeat, not slides: a chorus broken across two slides is sung as two
    // slides each time round, so the unit here is the group and never the section list.
    val groups = slideGroupsOf(sections)
    if (groups.none { isChorusGroup(it) }) return sections
    if (groups.none { singsTheChorusAfterwards(it) }) return sections

    val result = mutableListOf<LyricSection>()
    var precedingChorus: List<LyricSection>? = null
    groups.forEachIndexed { index, group ->
        if (isChorusGroup(group)) precedingChorus = group
        result.addAll(group)
        if (!singsTheChorusAfterwards(group)) return@forEachIndexed
        // Already followed by a chorus as written — repeating it here would show it twice.
        if (groups.getOrNull(index + 1)?.let { isChorusGroup(it) } == true) return@forEachIndexed
        val chorus = precedingChorus ?: groups.drop(index + 1).firstOrNull { isChorusGroup(it) }
        if (chorus != null) result.addAll(chorus)
    }

    return result
}

internal fun SongsViewModel.isChorusGroup(group: List<LyricSection>): Boolean =
    group.first().type == Constants.SECTION_TYPE_CHORUS

/** A verse is sung with the chorus after it; a bridge, an intro or a tag is not. */
internal fun SongsViewModel.singsTheChorusAfterwards(group: List<LyricSection>): Boolean =
    group.first().let { it.type == Constants.SECTION_TYPE_VERSE && isVerseHeader(it.header) }

/**
 * Folds a section that is nothing but chords into the one it leads into.
 *
 * An intro, or a turnaround between verses, is written as a header and a row of chords with no
 * words under it. There is nothing to put on screen for it, so it should not be a section of its
 * own: it would sit in the list as a blank slide, and — because a bracketed header is typed as a
 * verse — the chorus auto-repeat below would insert a chorus straight after it, putting the
 * chorus ahead of verse 1.
 *
 * So its chart is carried onto the next section instead, and the band reads the intro above the
 * words it runs into. A chord-only section with nothing after it — an outro — goes onto the
 * previous one. When the section it lands on has no chords of its own, that section's words
 * become the rest of the chart, so the chart is never just the intro with the verse missing.
 */
internal fun SongsViewModel.foldChordOnlySections(sections: List<LyricSection>): List<LyricSection> {
    val carried = mutableListOf<String>()
    val out = mutableListOf<LyricSection>()

    sections.forEach { section ->
        if (section.lines.isEmpty() && section.chordLines.isNotEmpty()) {
            // The header rides along so the chart can say whose chords these are — otherwise an
            // intro folded onto verse 1 reads as a bar of the verse.
            section.header?.takeIf { it.isNotBlank() }?.let { carried.add(it) }
            carried.addAll(section.chordLines)
            return@forEach
        }
        val own = section.chordLines.ifEmpty { if (carried.isEmpty()) emptyList() else section.lines }
        out.add(section.copy(chordLines = if (carried.isEmpty()) section.chordLines else carried + own))
        carried.clear()
    }

    if (carried.isNotEmpty()) {
        val last = out.removeLastOrNull()
        if (last == null) return sections // nothing but chords — leave the song as it was written
        val own = last.chordLines.ifEmpty { last.lines }
        out.add(last.copy(chordLines = own + carried))
    }
    return out
}
